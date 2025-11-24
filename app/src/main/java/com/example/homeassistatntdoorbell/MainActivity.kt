package com.example.homeassistatntdoorbell

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager

    // UI Elements
    private lateinit var editHaUrl: TextInputEditText
    private lateinit var editAccessToken: TextInputEditText
    private lateinit var editDoorbellEntity: TextInputEditText
    private lateinit var editLockEntity: TextInputEditText
    private lateinit var editCameraUrl: TextInputEditText
    private lateinit var btnTestConnection: Button
    private lateinit var txtConnectionStatus: TextView
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    private lateinit var txtServiceStatus: TextView
    private lateinit var btnRequestPermissions: Button
    private lateinit var txtPermissionStatus: TextView

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefsManager = PreferencesManager(this)

        // Create notification channels
        NotificationHelper.createNotificationChannels(this)

        initViews()
        loadSettings()
        setupListeners()
        updatePermissionStatus()
    }

    private fun initViews() {
        editHaUrl = findViewById(R.id.editHaUrl)
        editAccessToken = findViewById(R.id.editAccessToken)
        editDoorbellEntity = findViewById(R.id.editDoorbellEntity)
        editLockEntity = findViewById(R.id.editLockEntity)
        editCameraUrl = findViewById(R.id.editCameraUrl)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        txtServiceStatus = findViewById(R.id.txtServiceStatus)
        btnRequestPermissions = findViewById(R.id.btnRequestPermissions)
        txtPermissionStatus = findViewById(R.id.txtPermissionStatus)
    }

    private fun loadSettings() {
        editHaUrl.setText(prefsManager.haUrl)
        editAccessToken.setText(prefsManager.accessToken)
        editDoorbellEntity.setText(prefsManager.doorbellEntity)
        editLockEntity.setText(prefsManager.lockEntity)
        editCameraUrl.setText(prefsManager.cameraUrl)
    }

    private fun saveSettings() {
        prefsManager.haUrl = editHaUrl.text.toString().trim()
        prefsManager.accessToken = editAccessToken.text.toString().trim()
        prefsManager.doorbellEntity = editDoorbellEntity.text.toString().trim()
        prefsManager.lockEntity = editLockEntity.text.toString().trim()
        prefsManager.cameraUrl = editCameraUrl.text.toString().trim()
    }

    private fun setupListeners() {
        btnTestConnection.setOnClickListener {
            saveSettings()
            testConnection()
        }

        btnStartService.setOnClickListener {
            saveSettings()
            if (!prefsManager.isConfigured()) {
                Toast.makeText(this, "Please configure Home Assistant settings first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!hasRequiredPermissions()) {
                Toast.makeText(this, "Please grant all required permissions first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startMonitoringService()
        }

        btnStopService.setOnClickListener {
            stopMonitoringService()
        }

        btnRequestPermissions.setOnClickListener {
            requestRequiredPermissions()
        }
    }

    private fun testConnection() {
        if (!prefsManager.isConfigured()) {
            txtConnectionStatus.text = "Please fill in all required fields"
            txtConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            return
        }

        btnTestConnection.isEnabled = false
        txtConnectionStatus.text = "Testing connection..."
        txtConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark))

        lifecycleScope.launch(Dispatchers.IO) {
            val client = HomeAssistantClient(
                prefsManager.haUrl,
                prefsManager.accessToken,
                onDoorbellTrigger = {},
                onConnectionStateChange = {}
            )

            val result = client.testConnection()

            withContext(Dispatchers.Main) {
                btnTestConnection.isEnabled = true
                result.onSuccess { message ->
                    txtConnectionStatus.text = "✓ $message"
                    txtConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                }.onFailure { error ->
                    txtConnectionStatus.text = "✗ ${error.message}"
                    txtConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, DoorbellMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        prefsManager.serviceEnabled = true
        updateServiceStatus(true)
        Toast.makeText(this, "Monitoring service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, DoorbellMonitorService::class.java)
        stopService(intent)
        prefsManager.serviceEnabled = false
        updateServiceStatus(false)
        Toast.makeText(this, "Monitoring service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (DoorbellMonitorService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateServiceStatus(running: Boolean) {
        if (running) {
            txtServiceStatus.text = "Service: Running"
            txtServiceStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            txtServiceStatus.text = "Service: Stopped"
            txtServiceStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = mutableListOf<String>()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Check other permissions that require manual enabling
            checkSpecialPermissions()
        }
    }

    private fun checkSpecialPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Full-screen intent (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // This requires manual user action in Settings
            permissionsNeeded.add("Full-Screen Intent (Settings → Apps → This App → Allow)")
        }

        // Display over other apps
        if (!Settings.canDrawOverlays(this)) {
            permissionsNeeded.add("Display over other apps")
        }

        if (permissionsNeeded.isNotEmpty()) {
            val message = "Please enable these permissions manually:\n\n" +
                    permissionsNeeded.joinToString("\n") +
                    "\n\nWould you like to open Settings?"

            AlertDialog.Builder(this)
                .setTitle("Additional Permissions Required")
                .setMessage(message)
                .setPositiveButton("Open Settings") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Later", null)
                .show()
        } else {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            updatePermissionStatus()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun updatePermissionStatus() {
        val status = StringBuilder()
        var allGranted = true

        // Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            status.append(if (granted) "✓" else "✗").append(" Notifications\n")
            if (!granted) allGranted = false
        }

        // Display over apps
        val canDrawOverlays = Settings.canDrawOverlays(this)
        status.append(if (canDrawOverlays) "✓" else "✗").append(" Display over apps\n")
        if (!canDrawOverlays) allGranted = false

        // Note about full-screen intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            status.append("⚠ Full-screen intent (enable in Settings)")
        }

        txtPermissionStatus.text = status.toString().trim()
        txtPermissionStatus.setTextColor(
            getColor(if (allGranted) android.R.color.holo_green_dark else android.R.color.holo_orange_dark)
        )
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()

        // Check actual service status, not just saved preference
        val isRunning = isServiceRunning()
        updateServiceStatus(isRunning)

        // Sync preference with actual state
        if (isRunning != prefsManager.serviceEnabled) {
            prefsManager.serviceEnabled = isRunning
        }

        // Auto-start service if configured but not running
        if (!isRunning && prefsManager.serviceEnabled && prefsManager.isConfigured() && hasRequiredPermissions()) {
            startMonitoringService()
        }
    }
}
