package com.xiaozhi.simple.model

import java.net.URI
import java.security.MessageDigest

enum class VoiceBackend(val label: String) {
    MOMO("Momo AI Server"), XIAOZHI("Xiaozhi server");

    companion object {
        // Legacy Xiaozhi settings never count as opting in to Xiaozhi voice.
        fun fromSaved(value: String?): VoiceBackend = values().firstOrNull { it.name == value } ?: MOMO
        fun selectionKey(deviceId: String) = "voice_backend." + scope(deviceId)
        fun scope(identity: String): String = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

        fun validXiaozhiEndpoint(url: String, scheme: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == scheme && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                uri.fragment == null && uri.rawQuery == null &&
                // This origin implements Momo's paired-device protocol, not Xiaozhi.
                !uri.host.trimEnd('.').equals("ai.momolegend.fun", ignoreCase = true)
        }.getOrDefault(false)
    }
}
