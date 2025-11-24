package com.example.homeassistatntdoorbell

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
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
    private lateinit var btnUnlock: Button
    private lateinit var btnDismiss: Button
    private lateinit var imgCamera: ImageView
    private lateinit var cardAIInfo: MaterialCardView

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var autoDismissSeconds = 0

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
        btnUnlock = findViewById(R.id.btnUnlock)
        btnDismiss = findViewById(R.id.btnDismiss)
        imgCamera = findViewById(R.id.imgCamera)
        cardAIInfo = findViewById(R.id.cardAIInfo)
    }

    private fun setupListeners() {
        btnUnlock.setOnClickListener {
            unlockDoor()
        }

        btnDismiss.setOnClickListener {
            finish()
        }
    }

    /**
     * Load camera stream in WebView
     */
    private fun loadCameraStream() {
        progressBar.visibility = View.VISIBLE

        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Load camera stream
        val cameraUrl = prefsManager.getFullCameraUrl()
        Log.d(TAG, "Loading camera: $cameraUrl")

        // Add authentication header for Home Assistant
        val headers = mapOf("Authorization" to "Bearer ${prefsManager.accessToken}")
        webView.loadUrl(cameraUrl, headers)
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

    override fun onDestroy() {
        super.onDestroy()
        autoDismissHandler.removeCallbacksAndMessages(null)
        webView.destroy()
    }
}
