package com.xiaozhi.simple.service

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class MomoSpeechApiTest {
    @Test fun fallbackTranscribesAndSynthesizesUsingOnlyPairedCredentials() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":"hello Momo"}"""))
            enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("mp3-bytes")); start()
        }
        val api = MomoSpeechApi(server.url("/api/device/voice/"), OkHttpClient())
        try {
            assertEquals("hello Momo", api.transcribe("paired-token", byteArrayOf(1, 2, 3), "taglish"))
            assertEquals("mp3-bytes", api.speak("paired-token", "Hello!").toString(Charsets.UTF_8))
            val stt = server.takeRequest(2, TimeUnit.SECONDS)!!
            val tts = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/api/device/voice/transcribe", stt.path)
            assertTrue(stt.body.readUtf8().contains("audio/mp4"))
            assertEquals("/api/device/voice/speak", tts.path)
            assertEquals("""{"text":"Hello!"}""", tts.body.readUtf8())
            for (request in listOf(stt, tts)) {
                assertEquals("paired-token", request.getHeader("X-Device-Token"))
                assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            }
            assertEquals(2, server.requestCount) // Speech fallback does not call the agent again.
        } finally { api.release(); server.shutdown() }
    }

    @Test fun authErrorsExplainPairingAndNeverFollowRedirects() = runBlocking {
        for (status in listOf(401, 403, 302, 404, 503)) {
            val server = MockWebServer().apply {
                enqueue(MockResponse().setResponseCode(status).setHeader("Location", "/other-server")
                    .setBody("secret-upstream-detail")); start()
            }
            val api = MomoSpeechApi(server.url("/api/device/voice/"), OkHttpClient.Builder().followRedirects(false).build())
            try {
                val error = runCatching { api.speak("paired-token", "hello") }.exceptionOrNull() as MomoSpeechApi.SpeechException
                assertEquals(status, error.status)
                assertFalse(error.message!!.contains("secret-upstream-detail"))
                if (status == 401) assertTrue(error.message!!.contains("Pair this phone again"))
                else assertFalse(error.message!!.contains("invalid or revoked"))
                assertEquals(1, server.requestCount)
            } finally { api.release(); server.shutdown() }
        }
    }

    @Test fun disabledSpeechIdentifiesTheStageWithoutClaimingDeviceRevocation() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Speech recognition is disabled for this agent"}"""))
            enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Speech synthesis is disabled for this agent"}"""))
            start()
        }
        val api = MomoSpeechApi(server.url("/api/device/voice/"), OkHttpClient())
        try {
            val recognition = runCatching { api.transcribe("paired-token", byteArrayOf(1), "en-US") }.exceptionOrNull() as MomoSpeechApi.SpeechException
            val synthesis = runCatching { api.speak("paired-token", "hello") }.exceptionOrNull() as MomoSpeechApi.SpeechException
            assertEquals("transcribe", recognition.action)
            assertEquals("speak", synthesis.action)
            assertTrue(recognition.message!!.contains("speech recognition is disabled or denied"))
            assertTrue(synthesis.message!!.contains("speech synthesis is disabled or denied"))
            for (error in listOf(recognition, synthesis)) {
                assertEquals(403, error.status)
                assertTrue(error.message!!.contains("QR credential is still saved"))
                assertFalse(error.message!!.contains("Pair this phone again"))
            }
            assertEquals(2, server.requestCount)
        } finally { api.release(); server.shutdown() }
    }

    @Test fun speechRejectsWrongContentTypeAndOversizedReplies() = runBlocking {
        for (response in listOf(MockResponse().setHeader("Content-Type", "text/html").setBody("login"),
            MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("x".repeat(2_000_001)))) {
            val server = MockWebServer().apply { enqueue(response); start() }
            val api = MomoSpeechApi(server.url("/api/device/voice/"), OkHttpClient())
            try { assertTrue(runCatching { api.speak("paired-token", "hello") }.isFailure) }
            finally { api.release(); server.shutdown() }
        }
    }
}
