package com.xiaozhi.simple.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ActivationService(private val context: Context) {
    
    companion object {
        private const val TAG = "ActivationService"
        private const val MAX_RETRIES = 60
        private const val RETRY_INTERVAL = 5000L
    }
    
    private val gson = Gson()
    private val deviceFingerprint = DeviceFingerprint.getInstance(context)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    data class ActivationData(
        val challenge: String,
        val code: String,
        val message: String = "Register this device with your Xiaozhi server"
    )
    
    suspend fun processActivation(
        activationData: ActivationData,
        otaUrl: String,
        deviceId: String,
        clientId: String,
        onProgress: ((String) -> Unit)? = null
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting activation")
            Log.i(TAG, "Code: ${activationData.code}")
            Log.i(TAG, "Message: ${activationData.message}")
            
            val (serialNumber, _, _) = deviceFingerprint.ensureDeviceIdentity()
            Log.i(TAG, "Serial number: $serialNumber")
            
            val hmacSignature = deviceFingerprint.generateHmac(activationData.challenge)
            if (hmacSignature.isEmpty()) {
                Log.e(TAG, "HMAC generation failed")
                onProgress?.invoke("Signature generation failed")
                return@withContext false
            }
            
            val payload = mapOf(
                "Payload" to mapOf(
                    "algorithm" to "hmac-sha256",
                    "serial_number" to serialNumber,
                    "challenge" to activationData.challenge,
                    "hmac" to hmacSignature
                )
            )
            
            val activateUrl = if (otaUrl.endsWith("/")) {
                "${otaUrl}activate"
            } else {
                "$otaUrl/activate"
            }
            
            Log.i(TAG, "Activation URL: $activateUrl")
            
            var lastError: String? = null
            
            for (attempt in 1..MAX_RETRIES) {
                try {
                    onProgress?.invoke("Activating ($attempt/$MAX_RETRIES)...")
                    
                    val jsonPayload = gson.toJson(payload)
                    Log.d(TAG, "Request payload: $jsonPayload")
                    
                    val requestBody = jsonPayload.toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )
                    
                    val request = Request.Builder()
                        .url(activateUrl)
                        .addHeader("Activation-Version", "2")
                        .addHeader("Device-Id", deviceId)
                        .addHeader("Client-Id", clientId)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()
                    
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    
                    Log.d(TAG, "Response status: ${response.code}")
                    Log.d(TAG, "Response body: $responseBody")
                    
                    when (response.code) {
                        200 -> {
                            Log.i(TAG, "Activation successful!")
                            deviceFingerprint.setActivationStatus(true)
                            onProgress?.invoke("Activation successful!")
                            return@withContext true
                        }
                        202 -> {
                            Log.i(TAG, "Waiting for code input...")
                            onProgress?.invoke("Waiting for code (${activationData.code})...")
                            delay(RETRY_INTERVAL)
                        }
                        else -> {
                            val errorMsg = try {
                                val errorJson = gson.fromJson(responseBody, JsonObject::class.java)
                                errorJson.get("error")?.asString ?: "Unknown error"
                            } catch (e: Exception) {
                                "Server error (${response.code})"
                            }
                            
                            if (errorMsg != lastError) {
                                Log.w(TAG, "Server response: $errorMsg")
                                lastError = errorMsg
                            }
                            
                            onProgress?.invoke("Waiting: $errorMsg")
                            delay(RETRY_INTERVAL)
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Activation request failed: ${e.message}", e)
                    onProgress?.invoke("Network error, retrying...")
                    delay(RETRY_INTERVAL)
                }
            }
            
            Log.e(TAG, "Activation failed: max retries reached")
            onProgress?.invoke("Activation timeout, please retry")
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "Activation error: ${e.message}", e)
            onProgress?.invoke("Activation failed: ${e.message}")
            return@withContext false
        }
    }
    
    fun checkActivationStatus(): Boolean {
        return deviceFingerprint.isActivated()
    }
    
    fun getSerialNumber(): String {
        return deviceFingerprint.getSerialNumber()
    }
}
