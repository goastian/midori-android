package org.midorinext.android.apptracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.midorinext.android.apptracking.packet.AppTrackingRuleEngine
import org.midorinext.android.apptracking.packet.DnsPacketCodec
import org.midorinext.android.apptracking.packet.DnsPacketParser
import org.midorinext.android.apptracking.packet.TcpPacketCodec
import org.midorinext.android.apptracking.packet.TrafficHostInspector
import org.midorinext.android.apptracking.packet.TunPacketPipeline
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.R
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import javax.inject.Inject

@AndroidEntryPoint
class MidoriAppTrackingVpnService : VpnService() {

    @Inject
    lateinit var telemetry: AppTrackingTelemetry

    @Inject
    lateinit var appPreferencesRepository: AppPreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trafficJob: Job? = null
    private var packetJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null

    private val packetParser = DnsPacketParser()
    private val hostInspector = TrafficHostInspector()
    private val ruleEngine = AppTrackingRuleEngine()
    private val packetBuffer = ByteArray(MAX_PACKET_SIZE)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSystemProtection()
            ACTION_STOP -> stopSystemProtection()
        }
        return START_STICKY
    }

    private fun startSystemProtection() {
        if (AppTrackingRuntimeState.running.value) return

        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            val interfaceReady = establishInterface()
            if (!interfaceReady) {
                telemetry.logEvent("system_protection_failed_interface")
                stopSystemProtection()
                return@launch
            }

            AppTrackingRuntimeState.setRunning(true)
            AppTrackingRuntimeState.resetMetrics()
            telemetry.logEvent("system_protection_started")
            startTrafficSampling()
            startPacketPipeline()
        }
    }

    private fun stopSystemProtection() {
        packetJob?.cancel()
        packetJob = null
        trafficJob?.cancel()
        trafficJob = null
        vpnInterface?.close()
        vpnInterface = null
        AppTrackingRuntimeState.setRunning(false)
        telemetry.logEvent("system_protection_stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun establishInterface(): Boolean = withContext(Dispatchers.IO) {
        val prefs = appPreferencesRepository.flow.firstOrNull() ?: return@withContext false
        val dnsServers = resolveDnsServers()

        val builder = Builder()
            .setSession("Midori App Tracking")
            .setMtu(1500)
            .addAddress("10.22.0.2", 32)

        dnsServers.forEach { dnsAddress ->
            val host = dnsAddress.hostAddress ?: return@forEach
            builder.addDnsServer(dnsAddress)
            // DNS-focused route: intercept resolver traffic and avoid hijacking all app traffic.
            builder.addRoute(host, 32)
        }

        val excludedPackages = (prefs.appTrackingExcludedPackagesList + DEFAULT_BROWSER_EXCLUSIONS).distinct()
        excludedPackages.forEach { packageName ->
            runCatching { builder.addDisallowedApplication(packageName) }
        }

        vpnInterface?.close()
        vpnInterface = builder.establish()
        vpnInterface != null
    }

    private fun resolveDnsServers(): List<InetAddress> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork
        val activeDnsServers = connectivityManager
            .getLinkProperties(activeNetwork)
            ?.dnsServers
            .orEmpty()
            .filterIsInstance<Inet4Address>()

        return if (activeDnsServers.isNotEmpty()) {
            activeDnsServers
        } else {
            listOf(
                InetAddresses.parseNumericAddress("1.1.1.1"),
                InetAddresses.parseNumericAddress("8.8.8.8")
            ).filterIsInstance<Inet4Address>()
        }
    }

    private fun startPacketPipeline() {
        val descriptor = vpnInterface ?: return
        val pipeline = TunPacketPipeline(descriptor)

        packetJob?.cancel()
        packetJob = serviceScope.launch {
            while (isActive) {
                val size = runCatching { pipeline.readPacket(packetBuffer) }.getOrElse { break }
                if (size <= 0) continue

                val dnsQuery = packetParser.parseDnsQuery(packetBuffer, size)
                if (dnsQuery != null && dnsQuery.udpInfo.destinationPort == DNS_PORT) {
                    val appPackage = resolveOwnerPackage(
                        protocol = UDP_PROTOCOL,
                        sourceIp = dnsQuery.udpInfo.sourceIp,
                        sourcePort = dnsQuery.udpInfo.sourcePort,
                        destinationIp = dnsQuery.udpInfo.destinationIp,
                        destinationPort = dnsQuery.udpInfo.destinationPort
                    )

                    val trackerMatch = ruleEngine.classify(dnsQuery.domain)
                    val responsePayload = if (trackerMatch != null) {
                        telemetry.logBlockedRequest(
                            domain = dnsQuery.domain,
                            appPackage = appPackage,
                            company = trackerMatch.company,
                            categories = trackerMatch.categories
                        )
                        DnsPacketCodec.createNxDomainResponse(dnsQuery.dnsPayload)
                    } else {
                        forwardDns(dnsQuery.dnsPayload, dnsQuery.udpInfo.destinationIp)
                    }

                    if (responsePayload != null) {
                        val responsePacket = DnsPacketCodec.buildIpv4UdpPacket(
                            sourceIp = dnsQuery.udpInfo.destinationIp,
                            destinationIp = dnsQuery.udpInfo.sourceIp,
                            sourcePort = dnsQuery.udpInfo.destinationPort,
                            destinationPort = dnsQuery.udpInfo.sourcePort,
                            payload = responsePayload
                        )
                        runCatching { pipeline.writePacket(responsePacket) }
                    }
                    continue
                }

                val hostSignal = hostInspector.parseHostSignal(packetBuffer, size) ?: continue
                val trackerMatch = ruleEngine.classify(hostSignal.host) ?: continue

                val appPackage = resolveOwnerPackage(
                    protocol = hostSignal.protocol,
                    sourceIp = hostSignal.sourceIp,
                    sourcePort = hostSignal.sourcePort,
                    destinationIp = hostSignal.destinationIp,
                    destinationPort = hostSignal.destinationPort
                )

                telemetry.logBlockedRequest(
                    domain = hostSignal.host,
                    appPackage = appPackage,
                    company = trackerMatch.company,
                    categories = trackerMatch.categories
                )
            }
            runCatching { pipeline.close() }
        }
    }

    private fun forwardDns(queryPayload: ByteArray, destinationIp: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        return try {
            protect(socket)
            socket.soTimeout = DNS_TIMEOUT_MS
            val resolverAddress = InetAddress.getByAddress(destinationIp)

            val request = DatagramPacket(
                queryPayload,
                queryPayload.size,
                resolverAddress,
                DNS_PORT
            )
            socket.send(request)

            val responseBuffer = ByteArray(MAX_DNS_PAYLOAD)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(response)
            responseBuffer.copyOf(response.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    private fun resolveOwnerPackage(
        protocol: Int,
        sourceIp: ByteArray,
        sourcePort: Int,
        destinationIp: ByteArray,
        destinationPort: Int
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        return runCatching {
            val connectivityManager = getSystemService(ConnectivityManager::class.java)
            val localAddress = InetSocketAddress(Inet4Address.getByAddress(sourceIp), sourcePort)
            val remoteAddress = InetSocketAddress(Inet4Address.getByAddress(destinationIp), destinationPort)
            val uid = connectivityManager.getConnectionOwnerUid(
                protocol,
                localAddress,
                remoteAddress
            )
            if (uid <= 0) {
                null
            } else {
                packageManager.getPackagesForUid(uid)?.firstOrNull()
            }
        }.getOrNull()
    }

    private fun startTrafficSampling() {
        trafficJob?.cancel()
        trafficJob = serviceScope.launch {
            while (isActive) {
                telemetry.logTrafficSample(
                    rxBytes = TrafficStats.getTotalRxBytes(),
                    txBytes = TrafficStats.getTotalTxBytes()
                )
                delay(15_000)
            }
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_tracking_system_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_tracking_system_notification_title))
            .setContentText(getString(R.string.app_tracking_system_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        packetJob?.cancel()
        trafficJob?.cancel()
        vpnInterface?.close()
        serviceScope.cancel()
        AppTrackingRuntimeState.setRunning(false)
    }

    private fun isBrowserPackage(packageName: String?): Boolean {
        return packageName != null && packageName in DEFAULT_BROWSER_EXCLUSIONS
    }

    companion object {
        const val ACTION_START = "org.midorinext.android.apptracking.action.START"
        const val ACTION_STOP = "org.midorinext.android.apptracking.action.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "app_tracking_protection_system"
        private const val NOTIFICATION_ID = 2048
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 2500
        private const val MAX_DNS_PAYLOAD = 4096
        private const val MAX_PACKET_SIZE = 32767
        private const val UDP_PROTOCOL = 17

        // Keep ATP focused on app tracker SDK traffic and avoid counting normal browser navigation.
        private val DEFAULT_BROWSER_EXCLUSIONS = setOf(
            "org.midorinext.android",
            "com.android.chrome",
            "org.mozilla.firefox",
            "org.mozilla.fenix",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.opera.browser"
        )
    }
}







