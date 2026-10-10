package com.xiaozhi.simple.service

import android.content.SharedPreferences
import com.xiaozhi.simple.model.VoiceBackend

/** Separate Keystore namespaces for each protocol and device; no plaintext token persistence. */
class VoiceCredentialStore internal constructor(private val prefs: SharedPreferences,
    private val cipherStore: (String) -> CredentialCipherStore) {
    constructor(prefs: SharedPreferences) : this(prefs, { purpose -> TokenStore(prefs, purpose) })
    private fun momo(id: String) = cipherStore("momo.device." + VoiceBackend.scope(id))
    private fun xiaozhi(url: String, id: String) = cipherStore("xiaozhi.device." + VoiceBackend.scope("$url\n$id"))

    fun migrate(xiaozhiUrl: String, xiaozhiId: String) {
        fun move(old: CredentialCipherStore, target: CredentialCipherStore) {
            val token = old.read()
            if (token.isNotBlank()) {
                if (target.read().isBlank()) target.write(token)
                old.write("") // Only remove the legacy ciphertext after the scoped write succeeds.
            }
        }
        prefs.getString("momo_device_id", null)?.takeIf { it.isNotBlank() }?.let {
            move(cipherStore("momo"), momo(it))
        }
        move(cipherStore("server"), xiaozhi(xiaozhiUrl, xiaozhiId))
    }

    fun readMomo(): String = prefs.getString("momo_device_id", null)?.takeIf { it.isNotBlank() }
        ?.let { momo(it).read() }.orEmpty()

    fun bindMomo(device: MomoPairingService.BoundDevice) {
        require(device.deviceId.isNotBlank() && device.token.isNotBlank())
        momo(device.deviceId).write(device.token)
        check(prefs.edit().putString("momo_device_id", device.deviceId).commit())
    }

    fun readXiaozhi(url: String, id: String) = xiaozhi(url, id).read()
    fun writeXiaozhi(url: String, id: String, token: String) = xiaozhi(url, id).write(token)
}
