package com.xiaozhi.simple.model
data class XiaozhiConfig(
    val serverUrl: String = "wss://xiaozhi.spacecloud.space/xiaozhi/v1/",
    val otaUrl: String = "https://xiaozhi.spacecloud.space/xiaozhi/ota/",
    val token: String = "",
    val deviceId: String = "",
    val autoConnect: Boolean = true,
    val volumePtt: Int = 0,
    val animateAvatar: Boolean = true,
    val depthGraphics: Boolean = false,
    val conversationMode: ConversationMode = ConversationMode.SPEAK
)
