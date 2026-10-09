package com.xiaozhi.simple.model

/**
 * Xiaozhi configuration
 */
data class XiaozhiConfig(
    val serverUrl: String = WatchServerPreset.SERVER_URL,
    val token: String = "",
    val deviceId: String = android.os.Build.MODEL,
    val autoConnect: Boolean = true,
    val otaUrl: String = WatchServerPreset.OTA_URL,
    val automaticToken: Boolean = true,
    val reduceMotion: Boolean = false
)
