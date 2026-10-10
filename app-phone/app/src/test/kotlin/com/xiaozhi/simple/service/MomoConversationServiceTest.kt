package com.xiaozhi.simple.service

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class MomoConversationServiceTest {
    @Test fun pairedSocketWaitsForHelloAndSendsLanguageWithoutConsoleToken() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"hello","version":1,"transport":"text"}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JsonParser.parseString(text).asJsonObject
                assertEquals("conversation", json.get("type").asString)
                assertEquals("Kumusta?", json.get("text").asString)
                assertEquals("taglish", json.get("language").asString)
                assertFalse(json.has("agent_id"))
                webSocket.send("""{"type":"reply","request_id":"${json.get("request_id").asString}","text":"Hello po!"}""")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }))
        server.start()
        val service = MomoConversationService(server.url("/api/device/conversation").toString().replace("http:", "ws:"), OkHttpClient())
        try {
            assertEquals("Hello po!", runBlocking { service.chat("paired-token", "Kumusta?", "taglish") })
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("paired-token", request.getHeader("X-Device-Token"))
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
        } finally { service.release(); server.shutdown() }
    }

    @Test fun onlyUnsupportedHandshakeAllowsHttpFallback() {
        for (status in listOf(404, 401, 403, 500)) {
            val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(status)); start() }
            val service = MomoConversationService(server.url("/").toString().replace("http:", "ws:"), OkHttpClient())
            try {
                val error = runCatching { runBlocking { service.chat("token", "hello", "en-US") } }.exceptionOrNull()
                assertEquals(status == 404, error is MomoConversationService.Unsupported)
                assertEquals(1, server.requestCount)
            } finally { service.release(); server.shutdown() }
        }
    }

    @Test fun connectionCheckAuthenticatesWithoutSubmittingATurn() {
        val received = java.util.concurrent.atomic.AtomicInteger()
        val server = MockWebServer().apply {
            enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"type":"hello","version":1,"transport":"text"}""") }
                override fun onMessage(webSocket: WebSocket, text: String) { received.incrementAndGet() }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            })); start()
        }
        val service = MomoConversationService(server.url("/").toString(), OkHttpClient())
        try {
            runBlocking { service.checkConnection("paired-token") }
            assertTrue(service.connectionState.value is com.xiaozhi.simple.model.ConnectionState.Connected)
            assertEquals(0, received.get())
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("paired-token", request.getHeader("X-Device-Token"))
            assertNull(request.getHeader("Authorization"))
        } finally { service.release(); server.shutdown() }
    }

    @Test fun xiaozhiHelloIsRejectedBeforeAnyConversationIsSent() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"type":"hello","version":1,"transport":"websocket","audio_params":{"format":"opus"}}""") }
            })); start()
        }
        val service = MomoConversationService(server.url("/").toString(), OkHttpClient())
        try {
            val error = runCatching { runBlocking { service.chat("paired-token", "hello", "en-US") } }.exceptionOrNull()
            assertTrue(error is MomoConversationService.Unsupported)
            assertTrue(service.connectionState.value is com.xiaozhi.simple.model.ConnectionState.Error)
        } finally { service.release(); server.shutdown() }
    }

    @Test fun revokedHandshakeExplainsMomoPairingAndDoesNotRetry() {
        val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(403)); start() }
        val service = MomoConversationService(server.url("/").toString(), OkHttpClient())
        try {
            val error = runCatching { runBlocking { service.checkConnection("revoked-token") } }.exceptionOrNull()
            assertEquals(403, (error as MomoChatService.ChatException).status)
            assertTrue(error.message!!.contains("Pair this phone again"))
            assertFalse(error.message!!.contains("Get server setup"))
            assertEquals(1, server.requestCount)
        } finally { service.release(); server.shutdown() }
    }

    @Test fun disconnectCancelsPendingHandshakeAndCannotReviveState() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {})); start()
        }
        val service = MomoConversationService(server.url("/").toString(), OkHttpClient())
        try {
            val pending = async { service.checkConnection("paired-token") }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            service.disconnect()
            kotlinx.coroutines.withTimeout(2000) { pending.join() }
            assertTrue(pending.isCancelled)
            assertTrue(service.connectionState.value is com.xiaozhi.simple.model.ConnectionState.Disconnected)
        } finally { service.release(); server.shutdown() }
    }

    @Test fun lostConnectionAfterSubmissionIsNeverRetried() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"type":"hello","version":1,"transport":"text"}""") }
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.close(1011, "lost") }
        }))
        server.start()
        val service = MomoConversationService(server.url("/").toString().replace("http:", "ws:"), OkHttpClient())
        try {
            val error = runCatching { runBlocking { service.chat("token", "hello", "en-US") } }.exceptionOrNull()
            assertNotNull(error); assertFalse(error is MomoConversationService.Unsupported)
            assertEquals(1, server.requestCount)
        } finally { service.release(); server.shutdown() }
    }
}
