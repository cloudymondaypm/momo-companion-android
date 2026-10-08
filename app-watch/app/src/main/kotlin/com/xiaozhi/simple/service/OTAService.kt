package com.xiaozhi.simple.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Only contacts the user's configured server; never downloads firmware. */
class OTAService(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()) {
    companion object { const val OTA_URL = "https://xiaozhi.spacecloud.space/xiaozhi/ota/" }
    data class OTAResult(val token: String, val bindingCode: String?)
    private val gson = Gson()

    suspend fun fetchConfig(deviceId: String, clientId: String, url: String = OTA_URL): OTAResult =
        withContext(Dispatchers.IO) {
            val payload = mapOf(
                "application" to mapOf("version" to "0.2.1-momo-companion", "elf_sha256" to deviceId.replace(":", "")),
                "board" to mapOf("type" to "android", "name" to "kiumo-zh23-yl-rf", "mac" to deviceId)
            )
            val request = Request.Builder().url(url)
                .header("Device-Id", deviceId).header("Client-Id", clientId)
                .header("User-Agent", "Momo-Companion/0.2.1")
                .post(gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Device setup failed (HTTP ${response.code})")
                val json = try { gson.fromJson(response.body?.string(), JsonObject::class.java) }
                    catch (_: Exception) { throw IOException("OTA endpoint did not return device configuration") }
                if (json == null || !json.has("websocket") || !json.get("websocket").isJsonObject)
                    throw IOException("OTA response has no WebSocket configuration; check server.websocket")
                val websocket = json.getAsJsonObject("websocket")
                val token = websocket.get("token")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                val activation = json.get("activation")?.takeIf { it.isJsonObject }?.asJsonObject
                val code = activation?.get("code")?.takeUnless { it.isJsonNull }?.asString
                    ?.takeIf { it.isNotBlank() }
                // Keep the user's WebSocket URL: it may advertise a placeholder or private address.
                OTAResult(token, code)
            }
        }
}
