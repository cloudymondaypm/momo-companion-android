package com.xiaozhi.simple.service

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MomoPairingServiceTest {
    @Test fun codePairingUsesPublicHttpsUrlAndIndependentSecretUntilAcknowledged() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val responses = ArrayDeque(listOf(
            """{"pairing_id":"session-id","code":"001234","poll_secret":"secret-for-phone-only","expires_at":1791636500}""",
            """{"status":"pending"}""",
            """{"status":"ready","device_id":"device-id","token":"device-token"}""",
            """{"status":"completed","device_id":"device-id"}"""
        ))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(responses.removeFirst().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val service = MomoPairingService(client)
        try {
            val session = service.requestCode("Test phone")
            assertEquals("001234", session.code)
            assertEquals("pending", service.poll(session).status)
            val ready = service.poll(session)
            assertEquals("device-token", ready.bound!!.token)
            service.acknowledge(session)
            assertEquals(listOf("request-code", "poll", "poll", "ack"),
                requests.map { it.url.pathSegments.last() })
            assertTrue(requests.all { it.url.scheme == "https" && it.url.host == "ai.momolegend.fun" })
            for (request in requests.drop(1)) {
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                val payload = JsonParser.parseString(buffer.readUtf8()).asJsonObject
                assertEquals("secret-for-phone-only", payload.get("poll_secret").asString)
                assertFalse(payload.has("code"))
            }
        } finally { service.release() }
    }

    @Test fun failureDoesNotProduceDeviceCredentials() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(429).message("Limited")
                .body("""{"detail":"Please wait"}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val service = MomoPairingService(client)
        try {
            try { service.requestCode("Phone"); fail("Rate limit must fail") }
            catch (error: IOException) { assertEquals("Please wait", error.message) }
        } finally { service.release() }
    }
}
