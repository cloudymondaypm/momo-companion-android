package com.xiaozhi.simple.model

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class WatchServerPresetTest {
    @Test fun freshInstallHasRequestedServerAndMatchingSetupEndpoint() {
        val config = XiaozhiConfig()
        assertEquals("wss://xiaozhi.spacecloud.space/xiaozhi/v1/", config.serverUrl)
        assertEquals("https://xiaozhi.spacecloud.space/xiaozhi/ota/", config.otaUrl)
        assertTrue(config.automaticToken)
    }

    @Test fun presetReplacesOldEndpointsWithoutErasingCredentialsOrPreferences() {
        val saved = XiaozhiConfig(serverUrl = "wss://old.example/xiaozhi/v1/",
            otaUrl = "https://old.example/setup/", token = "local-test-token",
            deviceId = "existing-watch", autoConnect = false,
            automaticToken = false, reduceMotion = true)
        val restored = WatchServerPreset.applyTo(saved)
        assertEquals(WatchServerPreset.SERVER_URL, restored.serverUrl)
        assertEquals(WatchServerPreset.OTA_URL, restored.otaUrl)
        assertEquals(saved, restored.copy(serverUrl = saved.serverUrl, otaUrl = saved.otaUrl))
    }
}
