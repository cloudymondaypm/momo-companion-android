package com.xiaozhi.simple.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xiaozhi.simple.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

class WebSocketService {
    companion object {
        private const val TAG = "WebSocketService"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val MAX_RECONNECT_DELAY = 30000L
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var sessionId: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var deviceId: String = ""
    private var serialNumber: String = ""

    private var currentUrl: String = ""
    private var currentToken: String = ""
    private var reconnectAttempts = 0
    private var autoReconnectEnabled = true
    private var isManualDisconnect = false
    @Volatile private var generation = 0

    var onTextMessage: ((String) -> Unit)? = null
    var onEmotion: ((String) -> Unit)? = null
    var onSttMessage: ((String) -> Unit)? = null
    var onAudioData: ((ByteArray) -> Unit)? = null
    var onSessionId: ((String) -> Unit)? = null
    var onTtsStateChanged: ((String) -> Unit)? = null
    var onActivationRequired: ((challenge: String, code: String) -> Unit)? = null
    var onReconnecting: ((attempt: Int, maxAttempts: Int) -> Unit)? = null

    fun beginSetup() { _connectionState.value = ConnectionState.Connecting }
    fun reportError(message: String) {
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.Error(message)
    }

    fun connect(url: String, deviceId: String = "", serialNumber: String = "", token: String = "") {
        this.deviceId = deviceId
        this.serialNumber = serialNumber
        this.currentUrl = url
        this.currentToken = token
        this.isManualDisconnect = false

        if (_connectionState.value is ConnectionState.Connected) {
            Log.d(TAG, "Already connected, skip")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        sessionId = null
        val attemptGeneration = ++generation
        Log.d(TAG, "Connecting: $url")

        // Self-hosted Xiaozhi requires Device-Id/Client-Id on the handshake even
        // when server.auth.enabled=false. Authorization is optional.
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Protocol-Version", "1")
            .addHeader("Device-Id", deviceId)
            .addHeader("Client-Id", serialNumber)

        if (token.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (attemptGeneration != generation) { webSocket.cancel(); return }
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.Connecting
                sendHello()
                // Wait for the server hello before enabling PTT.
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (attemptGeneration != generation) return
                if (text.contains("认证失败") || text.contains("authentication failed", ignoreCase = true)) {
                    generation++
                    webSocket.cancel()
                    reportError("Authentication failed; enable automatic token setup or check server authentication")
                    return
                }
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (attemptGeneration != generation) return
                onAudioData?.invoke(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (attemptGeneration != generation) return
                webSocket.close(code, reason)
                Log.d(TAG, "Connection closing: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (attemptGeneration != generation) return
                Log.d(TAG, "Connection closed: $code - $reason")
                stopHeartbeat()
                handleConnectionLoss("Closed: $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (attemptGeneration != generation) return
                Log.e(TAG, "Connection failed: ${t.message}", t)
                stopHeartbeat()
                handleConnectionLoss("Failed: ${t.message}")
            }
        })
    }

    private fun handleConnectionLoss(reason: String) {
        Log.w(TAG, "Connection lost: $reason")

        if (isManualDisconnect) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        if (autoReconnectEnabled && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            attemptReconnect()
        } else {
            _connectionState.value = ConnectionState.Error(reason)
        }
    }

    private fun attemptReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            onReconnecting?.invoke(reconnectAttempts, MAX_RECONNECT_ATTEMPTS)
            _connectionState.value = ConnectionState.Connecting

            val delayMs = minOf(reconnectAttempts * 2000L, MAX_RECONNECT_DELAY)
            delay(delayMs)

            if (!isManualDisconnect) {
                connect(currentUrl, deviceId, serialNumber, currentToken)
            }
        }
    }

    fun disconnect() {
        generation++
        isManualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        stopHeartbeat()
        webSocket?.close(1000, "Normal close")
        webSocket = null
        sessionId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }

    private fun sendHello() {
        val hello = mapOf(
            "type" to "hello",
            "version" to 1,
            "features" to mapOf("mcp" to false),
            "transport" to "websocket",
            "audio_params" to mapOf(
                "format" to "opus",
                "sample_rate" to 16000,
                "channels" to 1,
                "frame_duration" to 60
            )
        )
        sendJson(hello)
    }

    fun startListening(mode: String = "manual") {
        val sid = sessionId ?: run {
            Log.w(TAG, "No session ID, cannot start listening")
            return
        }
        sendJson(
            mapOf(
                "type" to "listen",
                "state" to "start",
                "mode" to mode,
                "session_id" to sid
            )
        )
    }

    fun stopListening() {
        val sid = sessionId ?: return
        sendJson(
            mapOf(
                "type" to "listen",
                "state" to "stop",
                "session_id" to sid
            )
        )
    }

    fun sendText(text: String) {
        val message = mutableMapOf<String, Any>(
            "type" to "listen",
            "state" to "detect",
            "text" to text
        )
        sessionId?.let { message["session_id"] = it }
        sendJson(message)
    }

    fun sendAudio(audioData: ByteArray) {
        webSocket?.send(ByteString.of(*audioData))
    }

    fun abort() {
        val message = mutableMapOf<String, Any>("type" to "abort")
        sessionId?.let { message["session_id"] = it }
        sendJson(message)
    }

    fun sendAbort() = abort()

    private fun sendJson(data: Any) {
        webSocket?.send(gson.toJson(data))
    }

    private fun handleTextMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: ""

            json.get("session_id")?.asString?.let {
                if (sessionId != it) {
                    sessionId = it
                    onSessionId?.invoke(it)
                }
            }

            when (type) {
                "llm" -> json.get("emotion")?.takeUnless { it.isJsonNull }?.asString?.let { onEmotion?.invoke(it) }
                "hello" -> {
                    if (!sessionId.isNullOrBlank()) {
                        reconnectAttempts = 0
                        _connectionState.value = ConnectionState.Connected
                        startHeartbeat()
                    }
                }
                "activation_required" -> {
                    val challenge = json.get("challenge")?.asString ?: ""
                    val code = json.get("code")?.asString ?: ""
                    if (challenge.isNotEmpty() && code.isNotEmpty()) {
                        onActivationRequired?.invoke(challenge, code)
                    }
                }
                "tts" -> {
                    val state = json.get("state")?.asString
                    val ttsText = json.get("text")?.asString
                    when (state) {
                        "start", "sentence_start" -> {
                            if (!ttsText.isNullOrEmpty()) onTextMessage?.invoke(ttsText)
                            onTtsStateChanged?.invoke("start")
                        }
                        "stop" -> onTtsStateChanged?.invoke("end")
                    }
                }
                "stt" -> {
                    val sttText = json.get("text")?.asString
                    if (!sttText.isNullOrEmpty()) onSttMessage?.invoke(sttText)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse message failed: ${e.message}", e)
            if (_connectionState.value !is ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Error("Server rejected handshake; check token or Device ID")
            }
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (webSocket != null && _connectionState.value is ConnectionState.Connected) {
                try {
                    delay(30000)
                    sendJson(mapOf("type" to "ping"))
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
