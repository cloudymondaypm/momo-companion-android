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

/** Text-only device API. Never sends a console cookie or Xiaozhi bearer token. */
class MomoChatService internal constructor(
    private val endpoint: HttpUrl,
    private val client: OkHttpClient
) {
    constructor() : this(
        "${MomoPairingService.SERVER}/api/device/chat".toHttpUrl(),
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    )

    class ChatException(val status: Int, message: String) : IOException(message)

    suspend fun chat(token: String, message: String): String {
        require(token.isNotBlank()) { "Pair this phone in Settings first." }
        require(message.isNotBlank() && message.codePointCount(0, message.length) <= 8000) {
            "Enter a message of 1–8000 characters."
        }
        val body = JsonObject().apply { addProperty("message", message) }
        val request = Request.Builder().url(endpoint)
            .header("X-Device-Token", token).header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWith(Result.failure(IOException("Could not reach Momo AI Server. Check your connection and try again.")))
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching { response.use { parse(it) } }
                        continuation.resumeWith(result)
                    }
                })
            }
        }
    }

    private fun parse(response: Response): String {
        if (response.code == 401 || response.code == 403) throw ChatException(response.code,
            "Momo device credential is invalid or revoked. Pair this phone again in Settings.")
        if (response.code in 300..399) throw ChatException(response.code, "Momo chat returned an unexpected redirect.")
        val type = response.header("Content-Type").orEmpty()
        if (!type.contains("application/json", true)) throw ChatException(response.code,
            "Momo chat is unavailable (HTTP ${response.code}). Ask the server owner to deploy the device chat relay.")
        val source = response.body?.source() ?: throw IOException("Empty Momo response.")
        source.request(262145)
        if (source.buffer.size > 262144) throw IOException("Momo reply is too large.")
        val json = runCatching { JsonParser.parseString(source.readUtf8()).asJsonObject }.getOrNull()
            ?: throw IOException("Momo returned an invalid chat response.")
        if (!response.isSuccessful) {
            // Do not surface arbitrary upstream bodies or credentials in errors.
            throw ChatException(response.code, when (response.code) {
                429 -> "Momo is busy or rate limited. Wait before trying again."
                504 -> "Momo timed out. Try again later."
                502, 503 -> "Momo's AI provider is unavailable. Check the agent's model settings in the dashboard."
                else -> "Momo chat failed (HTTP ${response.code}). Try again or check the server."
            })
        }
        return json.get("reply")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it.isNotBlank() }
            ?: throw IOException("Momo returned an empty or invalid reply.")
    }

    fun release() { client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
