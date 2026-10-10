package com.xiaozhi.simple.service

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class MomoChatServiceTest {
    private fun fixture(test: (MockWebServer, MomoChatService) -> Unit) {
        val server = MockWebServer().apply { start() }
        val service = MomoChatService(server.url("/api/device/chat"), OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build())
        try { test(server, service) } finally { service.release(); server.shutdown() }
    }
    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test fun sendsOnlyPairedDeviceCredentialAndMessage() = fixture { server, service ->
        server.enqueue(json("""{"reply":"Hello from your paired agent","usage":{}}"""))
        val reply = runBlocking { service.chat("momo-paired-token", "Hello \"Momo\"\n你好") }
        assertEquals("Hello from your paired agent", reply)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/api/device/chat", request.path)
        assertEquals("momo-paired-token", request.getHeader("X-Device-Token"))
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertEquals("no-store", request.getHeader("Cache-Control"))
        assertEquals("Hello \"Momo\"\n你好", com.google.gson.JsonParser.parseString(request.body.readUtf8()).asJsonObject.get("message").asString)
    }

    @Test fun refusesBlankTokenAndInvalidMessageBeforeSending() = fixture { server, service ->
        for ((token, text) in listOf("" to "hello", "device" to " ", "device" to "x".repeat(8001))) {
            val error = runCatching { runBlocking { service.chat(token, text) } }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun revokedCredentialTellsUserToPairAgainWithoutExposingBody() = fixture { server, service ->
        server.enqueue(json("""{"detail":"SECRET"}""", 401))
        val error = runCatching { runBlocking { service.chat("device", "hi") } }.exceptionOrNull()!!
        assertEquals(401, (error as MomoChatService.ChatException).status)
        assertTrue(error.message!!.contains("Pair"))
        assertFalse(error.message!!.contains("SECRET"))
    }

    @Test fun rejectsRedirectAndDoesNotSendTokenToOtherHost() = fixture { server, service ->
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/steal")))
            assertTrue(runCatching { runBlocking { service.chat("device", "hi") } }.isFailure)
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }

    @Test fun rejectsHtmlInvalidJsonMissingReplyAndProviderErrors() = fixture { server, service ->
        for (response in listOf(MockResponse().setResponseCode(404).setBody("<html>SECRET</html>"),
            json("{SECRET"), json("{}"), json("""{"reply":" "}"""), json("""{"reply":true}"""),
            json("""{"detail":"SECRET"}""", 429), json("""{"detail":"SECRET"}""", 502))) {
            server.enqueue(response)
            val error = runCatching { runBlocking { service.chat("device", "hi") } }.exceptionOrNull()!!
            assertFalse(error.message.orEmpty().contains("SECRET"))
        }
    }

    @Test fun rejectsOversizedReply() = fixture { server, service ->
        server.enqueue(json("""{"reply":"${"x".repeat(262145)}"}"""))
        assertTrue(runCatching { runBlocking { service.chat("device", "hi") } }.isFailure)
    }

    @Test fun cancelsPendingNetworkRequest() = fixture { server, service ->
        server.enqueue(json("""{"reply":"late"}""").setHeadersDelay(10, TimeUnit.SECONDS))
        runBlocking {
            val job = launch { service.chat("device", "hi") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            withTimeout(2000) { job.cancelAndJoin() }
        }
    }
}
