package com.xiaozhi.simple.model
data class XiaozhiConfig(
    val serverUrl: String = "wss://xiaozhi.spacecloud.space/xiaozhi/v1/",
    val otaUrl: String = "https://xiaozhi.spacecloud.space/xiaozhi/ota/",
    val token: String = "",
    val deviceId: String = "",
    val autoConnect: Boolean = true,
    val volumePtt: Int = 0,
    val animateAvatar: Boolean = true,
    val localStt: Boolean = true,
    val localTts: Boolean = true,
    val speechLanguage: String = "en-US",
    val voiceName: String = "",
    val speechRate: Float = 1f,
    val speechPitch: Float = 1f
)
