package com.xiaozhi.simple.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DeviceFingerprint private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceFingerprint"
        private const val PREFS_NAME = "device_fingerprint"
        private const val KEY_SERIAL_NUMBER = "serial_number"
        private const val KEY_HMAC_KEY = "hmac_key"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_IS_ACTIVATED = "is_activated"
        
        @Volatile
        private var instance: DeviceFingerprint? = null
        
        fun getInstance(context: Context): DeviceFingerprint {
            return instance ?: synchronized(this) {
                instance ?: DeviceFingerprint(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    fun ensureDeviceIdentity(): Triple<String, String, Boolean> {
        var serialNumber = prefs.getString(KEY_SERIAL_NUMBER, null)
        var hmacKey = prefs.getString(KEY_HMAC_KEY, null)
        val isActivated = prefs.getBoolean(KEY_IS_ACTIVATED, false)
        
        if (serialNumber.isNullOrEmpty() || hmacKey.isNullOrEmpty()) {
            Log.i(TAG, "Generating new device identity")
            serialNumber = generateSerialNumber()
            hmacKey = generateHmacKey()
            
            prefs.edit().apply {
                putString(KEY_SERIAL_NUMBER, serialNumber)
                putString(KEY_HMAC_KEY, hmacKey)
                apply()
            }
            
            Log.i(TAG, "Device identity generated: $serialNumber")
        } else {
            Log.i(TAG, "Using existing device identity: $serialNumber")
        }
        
        return Triple(serialNumber, hmacKey, isActivated)
    }
    
    private fun generateSerialNumber(): String {
        return try {
            val deviceInfo = buildString {
                append(Build.MANUFACTURER)
                append(Build.MODEL)
                append(Build.BRAND)
                append(Build.DEVICE)
                
                try {
                    val androidId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                    if (!androidId.isNullOrEmpty()) {
                        append(androidId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot get Android ID: ${e.message}")
                }
            }
            
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceInfo.toByteArray())
            
            val macAddress = hash.take(6)
                .joinToString(":") { "%02x".format(it) }
                .lowercase()
            
            Log.i(TAG, "Generated MAC format device ID: $macAddress")
            macAddress
            
        } catch (e: Exception) {
            Log.e(TAG, "MAC address generation failed: ${e.message}", e)
            val random = ByteArray(6)
            java.security.SecureRandom().nextBytes(random)
            random.joinToString(":") { "%02x".format(it) }.lowercase()
        }
    }
    
    private fun generateHmacKey(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
    
    fun getSerialNumber(): String {
        return prefs.getString(KEY_SERIAL_NUMBER, null) ?: run {
            Log.w(TAG, "Serial number not found, regenerating")
            ensureDeviceIdentity().first
        }
    }
    
    fun getHmacKey(): String {
        return prefs.getString(KEY_HMAC_KEY, null) ?: run {
            Log.w(TAG, "HMAC key not found, regenerating")
            ensureDeviceIdentity().second
        }
    }
    
    fun getClientId(): String {
        return prefs.getString(KEY_CLIENT_ID, null) ?: run {
            val clientId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_ID, clientId).apply()
            Log.i(TAG, "Generated new Client ID: $clientId")
            clientId
        }
    }
    
    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_IS_ACTIVATED, false)
    }
    
    fun setActivationStatus(activated: Boolean): Boolean {
        return try {
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, activated).commit()
        } catch (e: Exception) {
            Log.e(TAG, "Set activation status failed: ${e.message}", e)
            false
        }
    }
    
    fun generateHmac(challenge: String): String {
        return try {
            val hmacKey = getHmacKey()
            val secretKey = SecretKeySpec(hmacKey.toByteArray(), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(challenge.toByteArray())
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "HMAC generation failed: ${e.message}", e)
            ""
        }
    }
    
    fun resetDeviceIdentity() {
        prefs.edit().apply {
            remove(KEY_SERIAL_NUMBER)
            remove(KEY_HMAC_KEY)
            remove(KEY_IS_ACTIVATED)
            apply()
        }
        Log.i(TAG, "Device identity reset")
    }
}
