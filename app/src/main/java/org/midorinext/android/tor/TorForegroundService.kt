/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.midorinext.android.IntentReceiverActivity
import org.midorinext.android.R

/**
 * Foreground service wrapper for TorManager.
 * Keeps Tor running even when the app is backgrounded.
 */
class TorForegroundService : Service() {

    companion object {
        private const val TAG = "TorForegroundService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "midori_tor_service"

        const val ACTION_START = "org.midorinext.android.tor.START"
        const val ACTION_STOP = "org.midorinext.android.tor.STOP"

        @Volatile
        var instance: TorForegroundService? = null
            private set

        fun startTor(context: Context) {
            val intent = Intent(context, TorForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTor(context: Context) {
            val intent = Intent(context, TorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    val torManager by lazy { TorManager(applicationContext) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    inner class LocalBinder : Binder() {
        fun getService(): TorForegroundService = this@TorForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        torManager.listener = object : TorManager.Listener {
            override fun onBootstrapProgress(progress: Int) {
                updateNotification("Tor: connecting… $progress%")
            }

            override fun onConnected(socksPort: Int) {
                updateNotification("Tor connected (port $socksPort)")
                TorIntegration.onTorConnected(applicationContext, socksPort)
            }

            override fun onDisconnected() {
                TorIntegration.onTorDisconnected(applicationContext)
            }

            override fun onError(message: String) {
                updateNotification("Tor error: $message")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    torManager.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, createNotification("Tor is starting…"))
                scope.launch {
                    try {
                        torManager.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start Tor", e)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.launch { torManager.stop() }
        torManager.destroy()
        scope.cancel()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, IntentReceiverActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tor_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tor)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_tor,
                getString(R.string.tor_stop),
                PendingIntent.getService(
                    this, 0,
                    Intent(this, TorForegroundService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tor Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps Tor running for private browsing"
                setShowBadge(false)
            }
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }
}
