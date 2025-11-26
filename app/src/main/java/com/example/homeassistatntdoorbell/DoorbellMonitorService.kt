package com.example.homeassistatntdoorbell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that monitors Home Assistant for doorbell events
 */
class DoorbellMonitorService : Service() {

    private val TAG = "DoorbellMonitorService"
    private lateinit var prefsManager: PreferencesManager
    private var haClient: HomeAssistantClient? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "doorbell_monitor_service"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        prefsManager = PreferencesManager(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        if (!prefsManager.isConfigured()) {
            Log.e(TAG, "Service not configured, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        connectToHomeAssistant()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        haClient?.disconnect()
        haClient = null
    }

    /**
     * Connect to Home Assistant WebSocket
     */
    private fun connectToHomeAssistant() {
        haClient?.disconnect()

        haClient = HomeAssistantClient(
            haUrl = prefsManager.haUrl,
            accessToken = prefsManager.accessToken,
            onDoorbellTrigger = {
                Log.i(TAG, "Doorbell triggered! Showing notification...")
                handleDoorbellTrigger()
            },
            onConnectionStateChange = { connected ->
                Log.d(TAG, "Connection state: $connected")
                updateNotification(if (connected) "Connected" else "Disconnected")
            }
        )

        haClient?.connect()
    }

    /**
     * Handle doorbell trigger event
     */
    private fun handleDoorbellTrigger() {
        // Launch full-screen activity directly
        val intent = Intent(this, DoorbellActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /**
     * Create notification channel for service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Doorbell Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that doorbell monitoring is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground service notification
     */
    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doorbell Monitor")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update foreground service notification
     */
    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }
}
