package com.xiaozhi.simple.service

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OTAServiceTest {
    private fun withServer(body: String, status: Int = 200, check: (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(status).setBody(body))
            check(server)
        } finally { server.shutdown() }
    }

    @Test fun tokenUsesActualWatchAndClientIdentity() = withServer(
        """{"websocket":{"url":"ws://placeholder.invalid/","token":"issued-token"}}"""
    ) { server ->
        val result = runBlocking { OTAService().fetchConfig("02:00:00:00:00:01", "watch-client", server.url("/xiaozhi/ota/").toString()) }
        assertEquals("issued-token", result.token)
        assertNull(result.bindingCode)
        val request = server.takeRequest()
        assertEquals("02:00:00:00:00:01", request.getHeader("Device-Id"))
        assertEquals("watch-client", request.getHeader("Client-Id"))
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("02:00:00:00:00:01"))
    }

    @Test fun bindingCodeDoesNotRequireChallenge() = withServer(
        """{"websocket":{"token":""},"activation":{"code":"123456"}}"""
    ) { server ->
        val result = runBlocking { OTAService().fetchConfig("watch", "client", server.url("/").toString()) }
        assertEquals("123456", result.bindingCode)
        assertEquals("", result.token)
    }

    @Test fun invalidConfigurationShowsUsefulError() = withServer("{}") { server ->
        try {
            runBlocking { OTAService().fetchConfig("watch", "client", server.url("/").toString()) }
            fail("Expected missing configuration error")
        } catch (e: IOException) { assertTrue(e.message!!.contains("server.websocket")) }
    }

    @Test fun rejectedSetupShowsHttpStatus() = withServer("Forbidden", 403) { server ->
        try {
            runBlocking { OTAService().fetchConfig("watch", "client", server.url("/").toString()) }
            fail("Expected HTTP error")
        } catch (e: IOException) { assertTrue(e.message!!.contains("403")) }
    }
}
