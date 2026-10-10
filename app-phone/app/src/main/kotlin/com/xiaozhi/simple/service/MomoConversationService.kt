package com.xiaozhi.simple.service

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One request per socket. Never replay a submitted agent/tool turn after connection loss. */
class MomoConversationService internal constructor(private val url: String, private val client: OkHttpClient) {
    private val sockets = java.util.Collections.synchronizedSet(mutableSetOf<WebSocket>())
    constructor() : this(MomoPairingService.SERVER.replace("https://", "wss://") + "/api/device/conversation",
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build())
    class Unsupported : IOException("Text socket unavailable on this server")

    suspend fun chat(token: String, text: String, language: String): String = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && text.isNotBlank() && text.codePointCount(0, text.length) <= 8000)
        val transcript = text
        withTimeout(60000) {
            suspendCancellableCoroutine { continuation ->
                val requestId = UUID.randomUUID().toString()
                var submitted = false
                var finished = false
                fun fail(error: Exception) {
                    if (!finished) { finished = true; if (continuation.isActive) continuation.resumeWithException(error) }
                }
                val socket = client.newWebSocket(Request.Builder().url(url).header("X-Device-Token", token).build(),
                    object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (finished || !continuation.isActive) return
                            if (text.length > 262144) { fail(IOException("Momo reply too large")); webSocket.cancel(); return }
                            try {
                                val json = JsonParser.parseString(text).asJsonObject
                                when (json.get("type")?.asString) {
                                    "hello" -> if (!submitted) {
                                        if (json.get("version")?.asInt != 1) { fail(Unsupported()); webSocket.cancel(); return }
                                        submitted = true
                                        val body = com.google.gson.JsonObject().apply {
                                            addProperty("type", "conversation"); addProperty("request_id", requestId)
                                            addProperty("text", transcript); addProperty("language", language)
                                        }
                                        if (!webSocket.send(body.toString())) { fail(IOException("Could not send Momo message")); webSocket.cancel() }
                                    }
                                    "reply" -> if (submitted && json.get("request_id")?.asString == requestId) {
                                        val reply = json.get("text")?.asString?.takeIf { it.isNotBlank() }
                                            ?: throw IOException("Empty Momo reply")
                                        finished = true; webSocket.close(1000, null); continuation.resume(reply)
                                    }
                                    "error" -> {
                                        val status = json.get("status")?.asInt ?: 400
                                        fail(MomoChatService.ChatException(status, "Momo voice request failed (HTTP $status). Check the agent settings."))
                                        webSocket.cancel()
                                    }
                                }
                            } catch (_: Exception) { fail(IOException("Invalid Momo conversation response")); webSocket.cancel() }
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            sockets.remove(webSocket)
                            fail(if (!submitted && response?.code in listOf(404, 426, 501)) Unsupported()
                                else MomoChatService.ChatException(response?.code ?: 0,
                                    "Momo connection failed. A submitted message is not retried automatically."))
                        }
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            fail(MomoChatService.ChatException(if (code == 1008) 401 else 0, "Momo connection closed. Check pairing and try again."))
                            webSocket.close(code, null)
                        }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { sockets.remove(webSocket) }
                    })
                sockets.add(socket)
                continuation.invokeOnCancellation { sockets.remove(socket); socket.cancel() }
            }
        }
    }
    fun release() {
        synchronized(sockets) { sockets.forEach { it.cancel() }; sockets.clear() }
        client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
    }
}
