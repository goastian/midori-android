/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import net.freehaven.tor.control.RawEventListener
import net.freehaven.tor.control.TorControlConnection
import org.torproject.jni.TorService
import java.io.File

/**
 * Manages the Tor lifecycle: start, stop, status, and new identity.
 * Launches the native TorService from tor-android and connects via control socket.
 */
class TorManager(private val context: Context) {

    companion object {
        private const val TAG = "TorManager"
    }

    private val dataDir = File(context.filesDir, "tor_data")
    private var torServiceConnection: ServiceConnection? = null
    private var controlConnection: TorControlConnection? = null
    private var torService: TorService? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var socksPort: Int = -1
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var bootstrapProgress: Int = 0
        private set

    interface Listener {
        fun onBootstrapProgress(progress: Int)
        fun onConnected(socksPort: Int)
        fun onDisconnected()
        fun onError(message: String)
    }

    var listener: Listener? = null

    /**
     * Start Tor and return the SOCKS5 proxy port.
     */
    suspend fun start(): Int = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.w(TAG, "Tor already running on port $socksPort")
            return@withContext socksPort
        }

        try {
            dataDir.mkdirs()

            // Find a free port for SOCKS
            socksPort = findAvailablePort()
            Log.d(TAG, "Allocated SOCKS port: $socksPort")

            // Write torrc
            val torrcFile = TorService.getTorrc(context)
            val torrcContent = buildTorrc(socksPort, dataDir)
            torrcFile.writeText(torrcContent)
            Log.d(TAG, "Wrote torrc:\n$torrcContent")

            // Write defaults torrc (required by tor-android)
            val defaultsTorrcFile = TorService.getDefaultsTorrc(context)
            defaultsTorrcFile.writeText("""
                DNSPort 0
                TransPort 0
                DisableNetwork 1
            """.trimIndent())

            // Start and bind to TorService
            startTorService()

            socksPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Tor", e)
            listener?.onError("Failed to start Tor: ${e.message}")
            cleanup()
            throw e
        }
    }

    /**
     * Stop Tor.
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Stopping Tor...")
        try {
            controlConnection?.shutdownTor("SHUTDOWN")
        } catch (e: Exception) {
            Log.w(TAG, "Error sending shutdown", e)
        }
        cleanup()
        isRunning = false
        bootstrapProgress = 0
        listener?.onDisconnected()
    }

    /**
     * Request a new Tor identity (new circuits).
     */
    fun requestNewIdentity() {
        try {
            controlConnection?.signal("NEWNYM")
            Log.d(TAG, "New identity requested")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request new identity", e)
        }
    }

    fun destroy() {
        scope.cancel()
        cleanup()
    }

    private fun cleanup() {
        try {
            torServiceConnection?.let { context.unbindService(it) }
        } catch (_: Exception) {}
        torServiceConnection = null
        controlConnection = null
        torService = null
    }

    private suspend fun startTorService() = suspendCancellableCoroutine<Unit> { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d(TAG, "TorService connected")
                val binder = service as? TorService.LocalBinder
                torService = binder?.service

                scope.launch {
                    // Wait for control connection
                    var conn: TorControlConnection? = null
                    var attempts = 0
                    while (conn == null && attempts < 60) {
                        delay(500)
                        conn = torService?.torControlConnection
                        attempts++
                    }

                    if (conn != null) {
                        delay(1000) // Let Tor fully initialize
                        controlConnection = conn
                        setupControlConnection(conn)
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    } else {
                        val error = Exception("Failed to get Tor control connection after 30s")
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "TorService disconnected")
                isRunning = false
                torService = null
                controlConnection = null
                listener?.onDisconnected()
            }
        }

        torServiceConnection = connection

        val intent = Intent(context, TorService::class.java)
        try {
            context.startService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun setupControlConnection(conn: TorControlConnection) {
        try {
            conn.authenticate(ByteArray(0))
            conn.setEvents(listOf("STATUS_CLIENT", "NOTICE", "WARN", "ERR"))
            conn.addRawEventListener(object : RawEventListener {
                override fun onEvent(keyword: String?, data: String?) {
                    val message = data ?: return
                    Log.d(TAG, "Tor [$keyword]: $message")
                    parseBootstrapProgress(message)?.let { progress ->
                        bootstrapProgress = progress
                        listener?.onBootstrapProgress(progress)
                        if (progress == 100 && !isRunning) {
                            isRunning = true
                            listener?.onConnected(socksPort)
                        }
                    }
                }
            })

            // Enable network (we started with DisableNetwork 1)
            conn.setConf(listOf("DisableNetwork 0"))
            Log.d(TAG, "Control connection setup complete, network enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup control connection", e)
            listener?.onError("Control setup failed: ${e.message}")
        }
    }

    private fun parseBootstrapProgress(message: String): Int? {
        // Parse: "NOTICE BOOTSTRAP PROGRESS=50 TAG=..."
        val regex = Regex("""BOOTSTRAP PROGRESS=(\d+)""")
        return regex.find(message)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun buildTorrc(socksPort: Int, dataDir: File): String = buildString {
        appendLine("SocksPort $socksPort")
        appendLine("DataDirectory ${dataDir.absolutePath}")
        appendLine("AvoidDiskWrites 1")
        appendLine("SafeSocks 1")
        // Prevent DNS leaks
        appendLine("DNSPort 0")
        appendLine("TransPort 0")
        // Disable exit relay
        appendLine("ExitRelay 0")
    }

    private fun findAvailablePort(): Int {
        val socket = java.net.ServerSocket(0)
        val port = socket.localPort
        socket.close()
        return port
    }
}
