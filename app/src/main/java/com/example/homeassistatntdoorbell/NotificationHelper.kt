package com.example.homeassistatntdoorbell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * Helper for creating and managing notification channels
 */
object NotificationHelper {

    const val CHANNEL_ID_DOORBELL = "doorbell_alerts"

    /**
     * Create all notification channels
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            // Doorbell alert channel (high priority, full-screen capable)
            val doorbellChannel = NotificationChannel(
                CHANNEL_ID_DOORBELL,
                "Doorbell Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when someone rings the doorbell"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)

                // Use default notification sound
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                )

                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(doorbellChannel)
        }
    }
}
