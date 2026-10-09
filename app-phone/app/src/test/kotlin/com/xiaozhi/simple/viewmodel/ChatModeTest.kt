package com.xiaozhi.simple.viewmodel

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.xiaozhi.simple.model.*
import com.xiaozhi.simple.service.WebSocketService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ChatModeTest {
    @Test fun chatKeepsTextButNeverCreatesNativeAudioPlaybackAndPersists() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("fold5_config", 0)
        prefs.edit().clear().putBoolean("auto_connect", false).commit()
        val model = MainViewModel(app)
        val store = ViewModelStore().apply { put("test", model) }
        try {
            model.foreground(true)
            model.focus(true)
            model.setConversationMode(ConversationMode.CHAT)
            shadowOf(Looper.getMainLooper()).idle()
            val field = MainViewModel::class.java.getDeclaredField("socket").apply { isAccessible = true }
            val socket = field.get(model) as WebSocketService
            socket.onTtsStateChanged?.invoke("start")
            socket.onTextMessage?.invoke("Hello from Momo")
            socket.onAudioData?.invoke(byteArrayOf(1, 2, 3))
            socket.onTtsStateChanged?.invoke("stop")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Hello from Momo", model.messages.value.single().content)
            assertEquals(DeviceState.IDLE, model.deviceState.value)
            assertEquals("", model.notice.value)
            model.beginPtt()
            assertFalse(model.isRecording.value)
            assertEquals("CHAT", prefs.getString("conversation_mode", null))
            val recreated = MainViewModel(app)
            val recreatedStore = ViewModelStore().apply { put("test", recreated) }
            try { assertEquals(ConversationMode.CHAT, recreated.config.value.conversationMode) }
            finally { recreatedStore.clear() }
        } finally { store.clear() }
    }
}
