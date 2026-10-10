package com.xiaozhi.simple.service

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
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

    @Test fun lostConnectionAfterSubmissionIsNeverRetried() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"type":"hello","version":1}""") }
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
