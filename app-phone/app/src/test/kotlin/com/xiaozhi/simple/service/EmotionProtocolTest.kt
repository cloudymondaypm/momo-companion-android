package com.xiaozhi.simple.service

import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class EmotionProtocolTest {
    @Test fun acceptsEmotionWithoutTurningEmojiTextIntoSpokenConversation() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.send("""{"type":"hello","transport":"websocket","session_id":"s"}""")
                webSocket.send("""{"type":"llm","emotion":"happy","text":"😀"}""")
                webSocket.send("""{"type":"llm","emotion":{"malformed":true}}""")
                webSocket.send("""{"type":"llm","emotion":"loving"}""")
            }
        }))
        server.start()
        val client = WebSocketService(true)
        val emotions = LinkedBlockingQueue<String>()
        val words = LinkedBlockingQueue<String>()
        client.onEmotion = { emotions.add(it) }
        client.onTextMessage = { words.add(it) }
        try {
            client.connect(server.url("/").toString().replace("http:", "ws:"), "device", "client", "")
            assertEquals("happy", emotions.poll(5, TimeUnit.SECONDS))
            assertEquals("loving", emotions.poll(5, TimeUnit.SECONDS))
            assertTrue(words.isEmpty())
        } finally { client.release(); server.shutdown() }
    }
}
