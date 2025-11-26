package com.example.homeassistatntdoorbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Broadcast receiver that starts the doorbell monitoring service on device boot
 */
class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "${intent.action}, checking if service should start")

            val prefsManager = PreferencesManager(context)

            // Only auto-start if the service was previously enabled and app is configured
            if (prefsManager.serviceEnabled && prefsManager.isConfigured()) {
                Log.i(TAG, "Auto-starting DoorbellMonitorService after boot")

                val serviceIntent = Intent(context, DoorbellMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d(TAG, "Service not configured or was disabled, skipping auto-start")
            }
        }
    }
}
