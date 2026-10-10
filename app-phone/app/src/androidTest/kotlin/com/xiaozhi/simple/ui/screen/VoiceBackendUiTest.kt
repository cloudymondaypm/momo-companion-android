package com.xiaozhi.simple.ui.screen

import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.xiaozhi.simple.model.*
import org.junit.Rule
import org.junit.Test

/** Run on an Android test device/emulator; tests do not contact either server. */
class VoiceBackendUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsShowOnlyTheSelectedProtocolsFieldsAndPreserveSelection() {
        compose.setContent {
            var backend by remember { mutableStateOf(VoiceBackend.MOMO) }
            MaterialTheme {
                SettingsDialog(XiaozhiConfig(), "", "", false, {}, backend, { backend = it }, { _, _ -> "" },
                    "", "", false, "paired-phone", {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Momo AI Server pairing (Android)").assertExists()
        compose.onNodeWithText("WebSocket server").assertDoesNotExist()
        compose.onNodeWithText("Momo AI Server ▾").performClick()
        compose.onNodeWithText("Xiaozhi server (opt-in)").performClick()
        compose.onNodeWithText("Momo AI Server pairing (Android)").assertDoesNotExist()
        compose.onNodeWithText("WebSocket server").assertExists()
        compose.onNodeWithText("OTA address").assertExists()
        compose.onNodeWithText("Xiaozhi server ▾").performScrollTo().performClick()
        compose.onNodeWithText("Momo AI Server (default)").performClick()
        compose.onNodeWithText("Paired Momo device: paired-phone").assertExists()
        compose.onNodeWithText("Bearer token (optional)").assertDoesNotExist()
    }

    @Test fun selectorFitsFold5CoverWidthAndSwitchesExplicitly() {
        compose.setContent {
            var backend by remember { mutableStateOf(VoiceBackend.MOMO) }
            MaterialTheme { Box(Modifier.width(320.dp)) { VoiceServerPicker(backend) { backend = it } } }
        }
        compose.onNodeWithText("Hybrid Voice server").assertIsDisplayed()
        compose.onNodeWithText("Momo AI Server ▾").assertIsDisplayed().performClick()
        compose.onNodeWithText("Xiaozhi server (opt-in)").performClick()
        compose.onNodeWithText("Xiaozhi server ▾").assertIsDisplayed()
    }
}
