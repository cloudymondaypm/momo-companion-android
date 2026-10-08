package com.xiaozhi.simple.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit metadata-only request to the user-configured OTA endpoint. No firmware is fetched. */
class ServerSetupService {
    data class Result(val token: String?, val activationCode: String?, val advertisedUrl: String?)
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    suspend fun fetch(url: String, deviceId: String, clientId: String): Result = suspendCancellableCoroutine { continuation ->
        val body = Gson().toJson(mapOf("application" to mapOf("version" to "1.0.0-fold5"),
            "board" to mapOf("type" to "android", "name" to "xiaozhi-fold5", "mac" to deviceId)))
        val call = client.newCall(Request.Builder().url(url).header("Device-Id", deviceId)
            .header("Client-Id", clientId).header("User-Agent", "xiaozhi-fold5/1.0.0")
            .post(body.toRequestBody("application/json".toMediaType())).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("OTA setup could not be reached."))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        check(it.isSuccessful) { "OTA setup returned HTTP ${it.code}." }
                        val source = it.body?.source() ?: error("Empty OTA response")
                        source.request(65537)
                        check(source.buffer.size <= 65536) { "OTA response too large." }
                        val text = source.readUtf8(source.buffer.size)
                        val json = Gson().fromJson(text, JsonObject::class.java)
                        val ws = json.getAsJsonObject("websocket")
                        val activation = json.getAsJsonObject("activation")
                        Result(ws?.get("token")?.asString, activation?.get("code")?.asString,
                            ws?.get("url")?.asString)
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
    fun release() { client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
