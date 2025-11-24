package com.example.homeassistatntdoorbell

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Home Assistant WebSocket and REST API client
 */
class HomeAssistantClient(
    private val haUrl: String,
    private val accessToken: String,
    private val onDoorbellTrigger: () -> Unit,
    private val onConnectionStateChange: (Boolean) -> Unit
) {
    private val TAG = "HomeAssistantClient"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var messageId = 1
    private var isAuthenticated = false
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES) // No read timeout for WebSocket
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * Connect to Home Assistant WebSocket
     */
    fun connect() {
        val wsUrl = haUrl.replace("https://", "wss://").replace("http://", "ws://") + "/api/websocket"

        Log.d(TAG, "Connecting to: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                onConnectionStateChange(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                onConnectionStateChange(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                onConnectionStateChange(false)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                onConnectionStateChange(false)
                scheduleReconnect()
            }
        })
    }

    /**
     * Handle incoming WebSocket messages
     */
    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString

            Log.d(TAG, "Received message type: $type")

            when (type) {
                "auth_required" -> {
                    // Send authentication
                    val authMessage = mapOf(
                        "type" to "auth",
                        "access_token" to accessToken
                    )
                    send(authMessage)
                }

                "auth_ok" -> {
                    Log.d(TAG, "Authentication successful")
                    isAuthenticated = true
                    subscribeToEvents()
                }

                "auth_invalid" -> {
                    Log.e(TAG, "Authentication failed")
                    disconnect()
                }

                "result" -> {
                    val success = json.get("success")?.asBoolean ?: false
                    if (success) {
                        Log.d(TAG, "Subscription successful")
                    } else {
                        Log.e(TAG, "Subscription failed")
                    }
                }

                "event" -> {
                    handleEvent(json)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }

    /**
     * Handle state change events
     */
    private fun handleEvent(json: JsonObject) {
        try {
            val event = json.getAsJsonObject("event")
            val eventType = event.get("event_type")?.asString

            if (eventType == "state_changed") {
                val data = event.getAsJsonObject("data")
                val entityId = data.get("entity_id")?.asString
                val newState = data.getAsJsonObject("new_state")
                val state = newState?.get("state")?.asString

                Log.d(TAG, "State changed: $entityId -> $state")

                // Check if it's the doorbell entity changing to "on"
                if (entityId == PreferencesManager.DEFAULT_DOORBELL_ENTITY && state == "on") {
                    Log.i(TAG, "Doorbell triggered!")
                    onDoorbellTrigger()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling event: ${e.message}", e)
        }
    }

    /**
     * Subscribe to state change events
     */
    private fun subscribeToEvents() {
        val subscribeMessage = mapOf(
            "id" to messageId++,
            "type" to "subscribe_events",
            "event_type" to "state_changed"
        )
        send(subscribeMessage)
    }

    /**
     * Send message to WebSocket
     */
    private fun send(message: Any) {
        try {
            val json = gson.toJson(message)
            webSocket?.send(json)
            Log.d(TAG, "Sent: $json")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}", e)
        }
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var delayMs = 1000L
            while (true) {
                Log.d(TAG, "Reconnecting in ${delayMs}ms...")
                delay(delayMs)
                connect()

                // Exponential backoff up to 30 seconds
                delayMs = (delayMs * 2).coerceAtMost(30000)

                // Wait to see if connection succeeds
                delay(5000)
                if (isAuthenticated) {
                    break
                }
            }
        }
    }

    /**
     * Disconnect WebSocket
     */
    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isAuthenticated = false
        onConnectionStateChange(false)
    }

    /**
     * Test connection to Home Assistant REST API
     */
    suspend fun testConnection(): Result<String> {
        return try {
            val request = Request.Builder()
                .url("$haUrl/api/")
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = gson.fromJson(body, JsonObject::class.java)
                val message = json.get("message")?.asString ?: "Unknown"
                Result.success("Connected to Home Assistant: $message")
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get AI analysis data from automation state
     */
    suspend fun getAIAnalysis(): Result<AIResponse> {
        return try {
            val request = Request.Builder()
                .url("$haUrl/api/states/automation.doorbell_notification")
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = gson.fromJson(body, JsonObject::class.java)

                // Extract visitor_data from attributes
                val attributes = json.getAsJsonObject("attributes")
                val visitorData = attributes?.getAsJsonObject("visitor_data")
                val structuredResponse = visitorData?.getAsJsonObject("structured_response")

                val title = structuredResponse?.get("title")?.asString ?: "Visitor at Door"
                val description = structuredResponse?.get("description")?.asString ?: "Someone is at the front door"

                Result.success(AIResponse(title, description))
            } else {
                // Return generic message if automation state not available yet
                Result.success(AIResponse("Visitor at Door", "Someone is at the front door"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AI analysis: ${e.message}", e)
            Result.success(AIResponse("Visitor at Door", "Someone is at the front door"))
        }
    }

    /**
     * Call Home Assistant service to unlock door
     */
    suspend fun unlockDoor(lockEntity: String): Result<String> {
        return callService("lock", "unlock", mapOf("entity_id" to lockEntity))
    }

    /**
     * Call Home Assistant service
     */
    private suspend fun callService(domain: String, service: String, data: Map<String, Any>): Result<String> {
        return try {
            val jsonData = gson.toJson(data)
            val requestBody = jsonData.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$haUrl/api/services/$domain/$service")
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success("Service called successfully")
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * AI analysis response data class
 */
data class AIResponse(
    val title: String,
    val description: String
)
