package com.xiaozhi.simple.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xiaozhi.simple.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Momo text protocol only. Each turn authenticates afresh; submitted turns are never replayed. */
class MomoConversationService internal constructor(private val url: String, private val client: OkHttpClient) {
    constructor() : this(MomoPairingService.SERVER.replace("https://", "wss://") + "/api/device/conversation",
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build())
    class Unsupported : IOException("Momo voice endpoint unavailable. Ask the server owner to enable /api/device/conversation.")
    private val gate = Any()
    private var generation = 0L
    private var active: WebSocket? = null
    private var cancelPending: (() -> Unit)? = null
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    // Connected means the latest authenticated hello succeeded; sockets are deliberately per turn.
    val connectionState: StateFlow<ConnectionState> = mutableState

    suspend fun checkConnection(token: String) { exchange(token, null, "en-US") }
    suspend fun chat(token: String, text: String, language: String): String = exchange(token, text, language)

    private suspend fun exchange(token: String, transcript: String?, language: String): String = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && token.length <= 256 && token.all { it.code in 32..126 }) { "Pair this phone with Momo in Settings first." }
        require(transcript == null || (transcript.isNotBlank() && transcript.codePointCount(0, transcript.length) <= 8000))
        require(language in listOf("en-US", "fil-PH", "taglish"))
        val epoch = synchronized(gate) {
            check(active == null) { "A Momo connection is already in progress." }
            ++generation
            mutableState.value = ConnectionState.Connecting
            generation
        }
        try {
            withTimeout(60000) {
                suspendCancellableCoroutine { continuation ->
                    val requestId = UUID.randomUUID().toString()
                    var submitted = false
                    var hello = false
                    var finished = false
                    fun fail(error: Exception, socket: WebSocket) {
                        if (finished || epoch != generation) return
                        finished = true
                        mutableState.value = ConnectionState.Error(error.message ?: "Momo connection failed.")
                        socket.cancel()
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                    fun success(reply: String, socket: WebSocket) {
                        if (finished || epoch != generation) return
                        finished = true
                        mutableState.value = ConnectionState.Connected
                        socket.close(1000, null)
                        if (continuation.isActive) continuation.resumeWith(Result.success(reply))
                    }
                    val socket = client.newWebSocket(Request.Builder().url(url)
                        .header("X-Device-Token", token).header("Cache-Control", "no-store").build(),
                        object : WebSocketListener() {
                            override fun onMessage(webSocket: WebSocket, text: String) = synchronized(gate) {
                                if (finished || epoch != generation || !continuation.isActive) return@synchronized
                                if (text.length > 262144) { fail(IOException("Momo reply is too large."), webSocket); return@synchronized }
                                try {
                                    val json = JsonParser.parseString(text).asJsonObject
                                    when (json.get("type")?.asString) {
                                        "hello" -> if (!hello) {
                                            if (json.get("version")?.asInt != 1 || json.get("transport")?.asString != "text") {
                                                fail(Unsupported(), webSocket); return@synchronized
                                            }
                                            hello = true
                                            if (transcript == null) { success("", webSocket); return@synchronized }
                                            submitted = true
                                            val body = JsonObject().apply {
                                                addProperty("type", "conversation"); addProperty("request_id", requestId)
                                                addProperty("text", transcript); addProperty("language", language)
                                            }
                                            if (!webSocket.send(body.toString())) fail(IOException("Could not send Momo message. Try again."), webSocket)
                                        }
                                        "reply" -> if (submitted && json.get("request_id")?.asString == requestId) {
                                            val reply = json.get("text")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                                                ?.asString?.takeIf { it.isNotBlank() } ?: throw IOException("Empty Momo reply.")
                                            success(reply, webSocket)
                                        }
                                        "error" -> fail(MomoChatService.ChatException(json.get("status")?.asInt ?: 400,
                                            errorMessage(json.get("status")?.asInt ?: 400)), webSocket)
                                    }
                                } catch (_: Exception) { fail(IOException("Invalid Momo conversation response."), webSocket) }
                            }
                            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = synchronized(gate) {
                                fail(IOException("Momo requires the text conversation protocol."), webSocket)
                            }
                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = synchronized(gate) {
                                fail(if (!submitted && response?.code in listOf(404, 426, 501)) Unsupported()
                                    else MomoChatService.ChatException(response?.code ?: 0, errorMessage(response?.code ?: 0)), webSocket)
                            }
                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = synchronized(gate) {
                                fail(MomoChatService.ChatException(if (code == 1008) 401 else 0,
                                    errorMessage(if (code == 1008) 401 else 0)), webSocket)
                                webSocket.close(code, null)
                                Unit
                            }
                        })
                    synchronized(gate) {
                        if (epoch == generation) {
                            active = socket
                            cancelPending = { continuation.cancel() }
                        } else { socket.cancel(); continuation.cancel() }
                    }
                    continuation.invokeOnCancellation { socket.cancel() }
                }
            }
        } finally {
            synchronized(gate) {
                if (epoch == generation) {
                    active?.cancel(); active = null; cancelPending = null
                    if (mutableState.value is ConnectionState.Connecting) mutableState.value = ConnectionState.Disconnected
                }
            }
        }
    }
    fun disconnect() = synchronized(gate) {
        ++generation; cancelPending?.invoke(); cancelPending = null; active?.cancel(); active = null; mutableState.value = ConnectionState.Disconnected
    }
    fun markError(message: String) { mutableState.value = ConnectionState.Error(message) }
    fun release() {
        disconnect(); client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
    }
    companion object {
        fun errorMessage(status: Int) = when (status) {
            401, 403 -> "Momo device credential is invalid or revoked. Pair this phone again in Settings."
            429 -> "Momo is busy or rate limited. Wait before trying again."
            502, 503, 504 -> "Momo's AI provider is unavailable. Check the assigned agent in the dashboard."
            0 -> "Momo connection lost. A submitted message is not retried automatically."
            else -> "Momo request failed (HTTP $status). Check the assigned agent and server."
        }
    }
}
