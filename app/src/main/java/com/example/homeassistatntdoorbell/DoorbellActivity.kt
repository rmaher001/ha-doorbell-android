package com.example.homeassistatntdoorbell

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen activity that appears when doorbell is triggered
 */
class DoorbellActivity : AppCompatActivity() {

    private val TAG = "DoorbellActivity"
    private lateinit var prefsManager: PreferencesManager
    private lateinit var haClient: HomeAssistantClient

    // UI Elements
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtTitle: TextView
    private lateinit var txtDescription: TextView
    private lateinit var btnTalk: Button
    private lateinit var btnUnlock: Button
    private lateinit var btnDismiss: Button
    private lateinit var imgCamera: ImageView
    private lateinit var cardAIInfo: MaterialCardView

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var autoDismissSeconds = 0
    private var launchedExternalApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_doorbell)

        prefsManager = PreferencesManager(this)
        autoDismissSeconds = prefsManager.autoDismissSeconds

        initViews()
        setupListeners()
        loadCameraStream()
        loadAIAnalysis()
        startAutoDismissTimer()
    }

    private fun initViews() {
        webView = findViewById(R.id.webViewCamera)
        progressBar = findViewById(R.id.progressBar)
        txtTitle = findViewById(R.id.txtTitle)
        txtDescription = findViewById(R.id.txtDescription)
        btnTalk = findViewById(R.id.btnTalk)
        btnUnlock = findViewById(R.id.btnUnlock)
        btnDismiss = findViewById(R.id.btnDismiss)
        imgCamera = findViewById(R.id.imgCamera)
        cardAIInfo = findViewById(R.id.cardAIInfo)
    }

    private fun setupListeners() {
        btnTalk.setOnClickListener {
            launchReolinkApp()
        }

        btnUnlock.setOnClickListener {
            unlockDoor()
        }

        btnDismiss.setOnClickListener {
            finish()
        }
    }

    /**
     * Launch Reolink app directly to doorbell camera for playback/timeline
     */
    private fun launchReolinkApp() {
        // Release WebView microphone before launching Reolink
        webView.loadUrl("about:blank")
        launchedExternalApp = true

        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.mcu.reolink",
                    "com.android.bc.login.WelcomeActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("UID", "952700096JAYXREP")
                putExtra("ALMTIME", System.currentTimeMillis().toString())
                putExtra("ALMNAME", "Detection")
                putExtra("DEVNAME", "Doorbell")
                putExtra("ALMTYPE", "PEOPLE")
                putExtra("ALMCHN", "1")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Reolink: ${e.message}")
            val launchIntent = packageManager.getLaunchIntentForPackage("com.mcu.reolink")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Reolink app not installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Load Scrypted WebRTC stream for live video with two-way audio
     */
    private fun loadCameraStream() {
        progressBar.visibility = View.VISIBLE
        imgCamera.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // Configure WebView for WebRTC with two-way audio
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                Log.d(TAG, "WebView permission request: ${request.resources.joinToString()}")
                // Grant all permissions (microphone for two-way audio)
                runOnUiThread {
                    request.grant(request.resources)
                }
            }

            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                Log.d(TAG, "WebView Console: ${message?.message()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "WebView page loaded: $url")
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                Log.e(TAG, "WebView error: $errorCode - $description")
            }
        }

        val scryptedUrl = prefsManager.getFullScryptedDashboardUrl()
        Log.d(TAG, "Loading Scrypted URL: $scryptedUrl")
        webView.loadUrl(scryptedUrl)
    }

    /**
     * Load AI analysis from Home Assistant automation
     */
    private fun loadAIAnalysis() {
        haClient = HomeAssistantClient(
            prefsManager.haUrl,
            prefsManager.accessToken,
            onDoorbellTrigger = {},
            onConnectionStateChange = {}
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val result = haClient.getAIAnalysis()

            withContext(Dispatchers.Main) {
                result.onSuccess { aiResponse ->
                    txtTitle.text = aiResponse.title
                    txtDescription.text = aiResponse.description
                    cardAIInfo.visibility = View.VISIBLE
                }.onFailure { error ->
                    Log.e(TAG, "Failed to get AI analysis: ${error.message}")
                    // Keep card hidden if AI analysis fails
                }
            }
        }
    }

    /**
     * Unlock the front door
     */
    private fun unlockDoor() {
        btnUnlock.isEnabled = false
        btnUnlock.text = "Unlocking..."

        lifecycleScope.launch(Dispatchers.IO) {
            val result = haClient.unlockDoor(prefsManager.lockEntity)

            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Toast.makeText(this@DoorbellActivity, "Door unlocked", Toast.LENGTH_SHORT).show()
                    btnUnlock.text = "Unlocked ✓"

                    // Auto-dismiss after unlock
                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 2000)

                }.onFailure { error ->
                    Toast.makeText(
                        this@DoorbellActivity,
                        "Failed to unlock: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    btnUnlock.isEnabled = true
                    btnUnlock.text = "Unlock Door"
                }
            }
        }
    }

    /**
     * Start auto-dismiss timer
     */
    private fun startAutoDismissTimer() {
        if (autoDismissSeconds > 0) {
            autoDismissHandler.postDelayed({
                Log.d(TAG, "Auto-dismissing after $autoDismissSeconds seconds")
                finish()
            }, autoDismissSeconds * 1000L)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload Scrypted stream when returning from Reolink app
        if (launchedExternalApp) {
            launchedExternalApp = false
            // Clear WebView state to force fresh WebRTC connection
            webView.clearCache(true)
            webView.clearHistory()
            loadCameraStream()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismissHandler.removeCallbacksAndMessages(null)
        webView.destroy()
    }
}
