package com.xiaozhi.simple.model

/** User-selected endpoints only; credentials and pairing identity remain private on-device. */
object WatchServerPreset {
    const val SERVER_URL = "wss://xiaozhi.spacecloud.space/xiaozhi/v1/"
    const val OTA_URL = "https://xiaozhi.spacecloud.space/xiaozhi/ota/"

    /** Restores endpoints without discarding the watch's token, identity or preferences. */
    fun applyTo(config: XiaozhiConfig): XiaozhiConfig =
        config.copy(serverUrl = SERVER_URL, otaUrl = OTA_URL)
}
