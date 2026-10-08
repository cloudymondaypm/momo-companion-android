package com.xiaozhi.simple.model

/**
 * Xiaozhi configuration
 */
data class XiaozhiConfig(
    val serverUrl: String = "wss://xiaozhi.spacecloud.space/xiaozhi/v1/",
    val token: String = "",
    val deviceId: String = android.os.Build.MODEL,
    val autoConnect: Boolean = true,
    val otaUrl: String = "https://xiaozhi.spacecloud.space/xiaozhi/ota/",
    val automaticToken: Boolean = true,
    val reduceMotion: Boolean = false
)
