package com.xiaozhi.simple.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xiaozhi.simple.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.net.URI
import java.util.concurrent.TimeUnit

/** One generation per socket; late callbacks can never revive an old connection. */
class WebSocketService(private val allowLocalTestServer: Boolean = false) {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: WebSocket? = null
    private var generation = 0L
    private var enabled = false
    private var attempts = 0
    private var retry: Job? = null
    private var timeout: Job? = null
    private var sessionId = ""
    private var currentUrl = ""
    private var deviceId = ""
    private var clientId = ""
    private var token = ""
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    var onEmotion: ((String) -> Unit)? = null
    var onTextMessage: ((String) -> Unit)? = null
    var onSttMessage: ((String) -> Unit)? = null
    var onAudioData: ((ByteArray) -> Unit)? = null
    var onTtsStateChanged: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @Synchronized
    fun connect(url: String, deviceId: String, serialNumber: String, token: String) {
        if (_connectionState.value is ConnectionState.Connected || enabled) return
        val uri = runCatching { URI(url) }.getOrNull()
        val testUrl = allowLocalTestServer && uri?.scheme == "ws" && uri.host in listOf("localhost", "127.0.0.1")
        if (uri == null || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.fragment != null || (uri.scheme != "wss" && !testUrl) ||
            listOf(deviceId, serialNumber, token).any { it.any { c -> c.code < 32 || c.code > 126 } }) {
            _connectionState.value = ConnectionState.Error("Use a valid wss:// server and plain ASCII device ID/token.")
            return
        }
        this.currentUrl = url
        this.deviceId = deviceId
        this.clientId = serialNumber
        this.token = token.removePrefix("Bearer ")
        enabled = true
        attempts = 0
        openSocket()
    }

    @Synchronized
    private fun openSocket() {
        if (!enabled) return
        generation++
        val id = generation
        sessionId = ""
        _connectionState.value = ConnectionState.Connecting
        val request = Request.Builder().url(currentUrl).header("Protocol-Version", "1")
            .header("Device-Id", deviceId).header("Client-Id", clientId)
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }.build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(this@WebSocketService) {
                if (!enabled || id != generation) { webSocket.cancel(); return@synchronized }
                webSocket.send(gson.toJson(mapOf("type" to "hello", "version" to 1,
                    "transport" to "websocket", "audio_params" to mapOf("format" to "opus",
                        "sample_rate" to 16000, "channels" to 1, "frame_duration" to 60))))
                Unit
            }
            override fun onMessage(webSocket: WebSocket, text: String) = synchronized(this@WebSocketService) {
                if (enabled && id == generation && text.length <= 65536) runCatching { handleText(text) }
            }
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = synchronized(this@WebSocketService) {
                if (enabled && id == generation && _connectionState.value is ConnectionState.Connected && bytes.size <= 8192)
                    onAudioData?.invoke(bytes.toByteArray())
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = synchronized(this@WebSocketService) {
                if (id == generation) { webSocket.close(code, null); lost(id, "Server closed the connection.") }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = synchronized(this@WebSocketService) {
                lost(id, "Server closed the connection.")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = synchronized(this@WebSocketService) {
                val status = response?.code
                if (id == generation && status in listOf(401, 403, 404)) {
                    disconnect()
                    _connectionState.value = ConnectionState.Error("Server rejected connection (HTTP $status). Check server/token/device ID.")
                } else lost(id, "Connection lost. Retrying automatically.")
            }
        })
        timeout?.cancel()
        timeout = scope.launch {
            delay(10000)
            synchronized(this@WebSocketService) {
                if (id == generation && _connectionState.value is ConnectionState.Connecting)
                    lost(id, "Server hello timed out. Retrying.")
            }
        }
    }

    private fun lost(id: Long, message: String) {
        if (!enabled || id != generation) return
        generation++
        timeout?.cancel()
        socket?.cancel()
        socket = null
        sessionId = ""
        _connectionState.value = ConnectionState.Connecting
        onError?.invoke(message)
        val wait = (2000L * (1L shl attempts.coerceAtMost(4))).coerceAtMost(30000L)
        attempts++
        retry?.cancel()
        retry = scope.launch { delay(wait); synchronized(this@WebSocketService) { openSocket() } }
    }

    private fun handleText(text: String) {
        if (text.trim() == "认证失败" || text.contains("Authentication failed", ignoreCase = true)) {
            disconnect()
            _connectionState.value = ConnectionState.Error("Server authentication failed. Open Settings → Get server setup, or enter your server token.")
            return
        }
        val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return
        fun string(name: String) = runCatching { json.get(name)?.asString }.getOrNull()
        when (string("type")) {
            "hello" -> {
                if (string("transport") != "websocket") return
                val audio = runCatching { json.getAsJsonObject("audio_params") }.getOrNull()
                if (audio != null && (audio.get("format")?.asString != "opus" ||
                    (audio.get("channels")?.asInt ?: 1) != 1)) {
                    disconnect()
                    _connectionState.value = ConnectionState.Error("Server must support mono Opus audio.")
                    return
                }
                sessionId = string("session_id") ?: ""
                timeout?.cancel()
                attempts = 0
                _connectionState.value = ConnectionState.Connected
            }
            "llm" -> if (_connectionState.value is ConnectionState.Connected)
                string("emotion")?.takeIf { it.length <= 64 }?.let { onEmotion?.invoke(it) }
            "stt" -> string("text")?.takeIf { it.isNotBlank() }?.let { onSttMessage?.invoke(it) }
            "tts" -> when (string("state")) {
                "start" -> onTtsStateChanged?.invoke("start")
                "sentence_start" -> string("text")?.takeIf { it.isNotBlank() }?.let { onTextMessage?.invoke(it) }
                "stop" -> onTtsStateChanged?.invoke("stop")
            }
            "activation_required" -> {
                disconnect()
                _connectionState.value = ConnectionState.Error("This server requires device activation. Register the Device ID in your server dashboard.")
            }
        }
    }

    /** Xiaozhi's text-input path; never opens capture or sends Opus packets. */
    @Synchronized fun sendText(text: String): Boolean {
        if (!com.xiaozhi.simple.model.TypedChat.valid(text)) return false
        return send(mapOf("type" to "listen", "state" to "detect",
            "text" to text.trim(), "session_id" to sessionId))
    }

    @Synchronized fun startListening(): Boolean = send(mapOf("type" to "listen", "state" to "start", "mode" to "manual", "session_id" to sessionId))
    @Synchronized fun stopListening() { send(mapOf("type" to "listen", "state" to "stop", "session_id" to sessionId)) }
    @Synchronized fun sendAbort() { send(mapOf("type" to "abort", "session_id" to sessionId)) }
    @Synchronized fun sendAudio(audio: ByteArray): Boolean =
        _connectionState.value is ConnectionState.Connected && (socket?.send(audio.toByteString()) ?: false)
    private fun send(value: Any): Boolean =
        _connectionState.value is ConnectionState.Connected && (socket?.send(gson.toJson(value)) ?: false)
    @Synchronized fun disconnect() {
        enabled = false
        generation++
        retry?.cancel(); timeout?.cancel()
        socket?.cancel(); socket = null
        sessionId = ""
        _connectionState.value = ConnectionState.Disconnected
    }
    fun release() { disconnect(); scope.cancel(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
