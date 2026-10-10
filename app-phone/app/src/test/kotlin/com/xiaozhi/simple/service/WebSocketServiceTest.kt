package com.xiaozhi.simple.service

import com.google.gson.JsonParser
import com.xiaozhi.simple.model.ConnectionState
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class WebSocketServiceTest {
    @Test fun microphoneProtocolWaitsForHelloAndUsesConfiguredIdentity() {
        val server = MockWebServer()
        val incoming = LinkedBlockingQueue<String>()
        val open = CountDownLatch(1)
        lateinit var remote: WebSocket
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { remote = webSocket; open.countDown() }
            override fun onMessage(webSocket: WebSocket, text: String) { incoming.add(text) }
        }))
        server.start()
        val service = WebSocketService(allowLocalTestServer = true)
        try {
            service.connect(server.url("/xiaozhi/v1/").toString().replace("http:", "ws:"), "02:ab:cd:ef:01:23", "test-client", "secret")
            assertTrue(open.await(5, TimeUnit.SECONDS))
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("02:ab:cd:ef:01:23", request.getHeader("Device-Id"))
            assertEquals("test-client", request.getHeader("Client-Id"))
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            val hello = JsonParser.parseString(incoming.poll(5, TimeUnit.SECONDS)).asJsonObject
            assertEquals("opus", hello.getAsJsonObject("audio_params").get("format").asString)
            assertEquals(60, hello.getAsJsonObject("audio_params").get("frame_duration").asInt)
            assertEquals(1, hello.getAsJsonObject("features").get("hybrid_voice").asInt)
            assertFalse(hello.getAsJsonObject("features").has("mcp"))
            assertTrue(service.connectionState.value is ConnectionState.Connecting)
            assertFalse(service.startListening())
            remote.send("""{"type":"hello","transport":"websocket","session_id":"test-session","audio_params":{"format":"opus","sample_rate":24000,"channels":1}}""")
            awaitState(service) { it is ConnectionState.Connected }
            assertTrue(service.startListening())
            val start = JsonParser.parseString(incoming.poll(5, TimeUnit.SECONDS)).asJsonObject
            assertEquals("manual", start.get("mode").asString)
            assertEquals("test-session", start.get("session_id").asString)
            service.stopListening()
            assertEquals("stop", JsonParser.parseString(incoming.poll(5, TimeUnit.SECONDS)).asJsonObject.get("state").asString)
            val spoken = LinkedBlockingQueue<String>()
            service.onTextMessage = { spoken.add(it) }
            remote.send("""{"type":"tts","state":"sentence_start","text":"Hello"}""")
            assertEquals("Hello", spoken.poll(5, TimeUnit.SECONDS))
        } finally { service.release(); server.shutdown() }
    }
    @Test fun disconnectCancelsReconnectAndAudio() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"hello","transport":"websocket","session_id":"s"}""")
            }
        }))
        server.start()
        val service = WebSocketService(true)
        try {
            service.connect(server.url("/").toString().replace("http:", "ws:"), "device", "client", "")
            awaitState(service) { it is ConnectionState.Connected }
            service.disconnect()
            assertTrue(service.connectionState.value is ConnectionState.Disconnected)
            assertFalse(service.sendAudio(byteArrayOf(1)))
            Thread.sleep(2500)
            assertEquals(1, server.requestCount)
            assertTrue(service.connectionState.value is ConnectionState.Disconnected)
        } finally { service.release(); server.shutdown() }
    }
    @Test fun retriesAfterServerDisconnectWithFreshHandshake() {
        val server = MockWebServer()
        val ready = CountDownLatch(1)
        lateinit var first: WebSocket
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                first = webSocket
                webSocket.send("""{"type":"hello","transport":"websocket","session_id":"first"}""")
                ready.countDown()
            }
        }))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"hello","transport":"websocket","session_id":"second"}""")
            }
        }))
        server.start()
        val service = WebSocketService(true)
        try {
            service.connect(server.url("/").toString().replace("http:", "ws:"), "device", "client", "")
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            awaitState(service) { it is ConnectionState.Connected }
            first.close(1000, "test")
            val until = System.currentTimeMillis() + 7000
            while (server.requestCount < 2 && System.currentTimeMillis() < until) Thread.sleep(10)
            assertEquals(2, server.requestCount)
            awaitState(service) { it is ConnectionState.Connected }
        } finally { service.release(); server.shutdown() }
    }
    @Test fun rejectsUnencryptedEndpointsAndHeaderInjection() {
        val service = WebSocketService()
        try {
            service.connect("ws://example.com/", "device", "client", "")
            assertTrue(service.connectionState.value is ConnectionState.Error)
            service.connect("wss://example.com/", "device", "client", "abc\r\ninjected")
            assertTrue(service.connectionState.value is ConnectionState.Error)
        } finally { service.release() }
    }
    @Test fun textualAuthenticationFailureStopsAutomaticRetries() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("认证失败")
                webSocket.close(1000, "")
            }
        }))
        server.start()
        val service = WebSocketService(true)
        try {
            service.connect(server.url("/").toString().replace("http:", "ws:"), "device", "client", "")
            awaitState(service) { it is ConnectionState.Error }
            assertTrue((service.connectionState.value as ConnectionState.Error).message.contains("authentication"))
            assertFalse(service.startListening())
            Thread.sleep(2500)
            assertEquals(1, server.requestCount)
        } finally { service.release(); server.shutdown() }
    }
    private fun awaitState(service: WebSocketService, condition: (ConnectionState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition(service.connectionState.value) && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("Unexpected state: ${service.connectionState.value}", condition(service.connectionState.value))
    }
}
