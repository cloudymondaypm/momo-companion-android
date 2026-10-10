package com.xiaozhi.simple.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Speech-only endpoints: never submit a conversation again to synthesize its reply. */
class MomoSpeechApi internal constructor(private val base: HttpUrl, private val client: OkHttpClient) {
    class SpeechException(val status: Int, val action: String, message: String) : IOException(message)
    constructor() : this((MomoPairingService.SERVER + "/api/device/voice/").toHttpUrl(),
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build())

    suspend fun transcribe(token: String, audio: ByteArray, language: String): String {
        require(audio.isNotEmpty() && audio.size <= 1024 * 1024)
        require(language in listOf("en-US", "fil-PH", "taglish"))
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("language", language)
            .addFormDataPart("file", "recording.m4a", audio.toRequestBody("audio/mp4".toMediaType())).build()
        val bytes = request(token, "transcribe", body, 65536, "application/json")
        return runCatching { JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            .get("text")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
            .getOrNull()?.takeIf { it.isNotBlank() && it.codePointCount(0, it.length) <= 8000 }
            ?: throw IOException("Momo returned an empty or invalid transcription.")
    }

    suspend fun speak(token: String, text: String): ByteArray {
        require(text.isNotBlank() && text.codePointCount(0, text.length) <= 1500)
        val body = JsonObject().apply { addProperty("text", text) }.toString()
            .toRequestBody("application/json".toMediaType())
        return request(token, "speak", body, 2_000_000, "audio/mpeg")
    }

    private suspend fun request(token: String, action: String, body: RequestBody, limit: Long, type: String): ByteArray = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && token.length <= 256 && token.all { it.code in 32..126 }) { "Pair this phone with Momo in Settings first." }
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(base.newBuilder().addPathSegment(action).build())
                .header("X-Device-Token", token).header("Cache-Control", "no-store").post(body).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IOException("Momo speech service unavailable. Check your connection.")))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        val speech = if (action == "transcribe") "speech recognition" else "speech synthesis"
                        if (!it.isSuccessful) throw SpeechException(it.code, action, when (it.code) {
                            401 -> MomoConversationService.errorMessage(it.code)
                            403 -> "Momo server $speech is disabled or denied (HTTP 403). Your QR credential is still saved. Check $speech for the assigned agent in the Momo dashboard, or enable local phone speech in Settings."
                            409 -> "Momo server $speech is not configured (HTTP 409). Select its model connection for the assigned agent in the Momo dashboard."
                            404 -> "Momo speech endpoint unavailable. Ask the server owner to enable device voice fallback."
                            else -> "Momo server $speech failed (HTTP ${it.code}). Check the assigned agent's speech provider in the Momo dashboard."
                        })
                        if (!it.header("Content-Type").orEmpty().substringBefore(';').trim().equals(type, true)) throw IOException("Invalid Momo speech response.")
                        val source = it.body?.source() ?: throw IOException("Empty Momo speech response.")
                        source.request(limit + 1)
                        if (source.buffer.size > limit) throw IOException("Momo speech response is too large.")
                        source.readByteArray().takeIf { bytes -> bytes.isNotEmpty() } ?: throw IOException("Empty Momo speech response.")
                    } }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }
    fun release() { client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
