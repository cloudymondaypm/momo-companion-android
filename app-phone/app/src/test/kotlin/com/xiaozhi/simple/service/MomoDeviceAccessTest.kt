package com.xiaozhi.simple.service

import com.xiaozhi.simple.model.ConnectionState
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Regression: a rejected voice route must not erase a working QR pairing or block typed chat. */
class MomoDeviceAccessTest {
    @Test fun disabledSpeechDoesNotRequireReconnectingOrRePairing() = runBlocking {
        val f = Fixture()
        val api = MomoSpeechApi(f.server.url("/api/device/voice/"), OkHttpClient())
        f.server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"type":"hello","version":1,"transport":"text"}""") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }))
        repeat(2) { f.server.enqueue(MockResponse().setResponseCode(403)) }
        f.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":"retry works"}"""))
        f.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"reply":"chat works"}"""))
        try {
            f.access.checkVoice()
            for (action in listOf("transcribe", "speak")) {
                val error = runCatching {
                    if (action == "transcribe") api.transcribe(f.access.credential(), byteArrayOf(1), "en-US")
                    else api.speak(f.access.credential(), "hello")
                }.exceptionOrNull() as MomoSpeechApi.SpeechException
                f.access.reportVoiceFailure(error, speechOnly = true)
                assertEquals(ConnectionState.Connected, f.socket.connectionState.value)
                assertTrue(f.access.paired.value)
            }
            assertEquals("retry works", api.transcribe(f.access.credential(), byteArrayOf(1), "en-US"))
            assertEquals("chat works", f.access.chat("hello"))
            assertEquals(5, f.server.requestCount)
            repeat(5) { assertEquals("paired-qr-token", f.server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("X-Device-Token")) }
        } finally { api.release(); f.close() }
    }

    @Test fun speechAuthenticationRejectionStillRequiresANewConnectionCheck() {
        val f = Fixture()
        try {
            f.access.reportVoiceFailure(MomoSpeechApi.SpeechException(401, "transcribe", "Device rejected"), speechOnly = true)
            assertTrue(f.socket.connectionState.value is ConnectionState.Error)
            assertTrue(f.access.paired.value)
            assertEquals(0, f.server.requestCount)
        } finally { f.close() }
    }
    private class Fixture {
        val server = MockWebServer().apply { start() }
        var persistedToken = "paired-qr-token"
        val http = MomoChatService(server.url("/api/device/chat"), OkHttpClient())
        val socket = MomoConversationService(server.url("/api/device/conversation").toString(), OkHttpClient())
        val access = MomoDeviceAccess({ persistedToken }, http, socket)
        fun close() { http.release(); socket.release(); server.shutdown() }
    }

    @Test fun voiceHandshakeRejectionKeepsPairingAndWorkingHttpChat() = runBlocking {
        val f = Fixture()
        f.server.enqueue(MockResponse().setResponseCode(403))
        f.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"reply":"Momo chat works"}"""))
        f.server.enqueue(MockResponse().setResponseCode(403))
        try {
            val error = runCatching { f.access.checkVoice() }.exceptionOrNull() as MomoConversationService.HandshakeRejected
            assertEquals(403, error.status)
            assertTrue(error.message!!.contains("voice connection was rejected"))
            assertFalse(error.message!!.contains("invalid or revoked"))
            assertTrue(f.access.paired.value)
            assertEquals("Momo chat works", f.access.chat("Are you there?"))
            assertTrue(f.access.paired.value)
            // A retry reaches the server with the same QR token, rather than a cached not-paired flag.
            assertTrue(runCatching { f.access.checkVoice() }.exceptionOrNull() is MomoConversationService.HandshakeRejected)
            val requests = (1..3).map { f.server.takeRequest(2, TimeUnit.SECONDS)!! }
            assertEquals(listOf("/api/device/conversation", "/api/device/chat", "/api/device/conversation"), requests.map { it.path })
            for (request in requests) {
                assertEquals("paired-qr-token", request.getHeader("X-Device-Token"))
                assertNull(request.getHeader("Authorization"))
            }
        } finally { f.close() }
    }

    @Test fun chatRejectionDoesNotReplaceSavedPairingOrPreventVoiceRetry() = runBlocking {
        val f = Fixture()
        f.server.enqueue(MockResponse().setResponseCode(401))
        f.server.enqueue(MockResponse().setResponseCode(403))
        try {
            val error = runCatching { f.access.chat("hello") }.exceptionOrNull() as MomoChatService.ChatException
            assertEquals(401, error.status)
            assertTrue(error.message!!.contains("invalid or revoked")) // This is a fresh HTTP chat rejection.
            assertTrue(f.access.paired.value)
            assertTrue(runCatching { f.access.checkVoice() }.exceptionOrNull() is MomoConversationService.HandshakeRejected)
            assertEquals(2, f.server.requestCount)
        } finally { f.close() }
    }

    @Test fun qrPairingAfterScreenCreationIsReadFreshByBothRoutes() = runBlocking {
        val f = Fixture()
        f.persistedToken = ""
        val access = MomoDeviceAccess({ f.persistedToken }, f.http, f.socket)
        assertFalse(access.paired.value)
        f.persistedToken = "new-qr-token"
        f.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"reply":"hello"}"""))
        f.server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"type":"hello","version":1,"transport":"text"}""") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }))
        try {
            assertEquals("hello", access.chat("test"))
            access.checkVoice()
            assertTrue(access.paired.value)
            repeat(2) { assertEquals("new-qr-token", f.server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("X-Device-Token")) }
        } finally { f.close() }
    }

    @Test fun onlyMissingStoredCredentialPromptsPairingWithoutANetworkRequest() = runBlocking {
        val f = Fixture()
        try {
            f.persistedToken = ""
            assertTrue(runCatching { f.access.checkVoice() }.exceptionOrNull() is IllegalArgumentException)
            assertFalse(f.access.paired.value)
            assertTrue(runCatching { f.access.chat("test") }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, f.server.requestCount)
        } finally { f.close() }
    }
}
