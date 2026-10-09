package com.xiaozhi.simple.model

import org.junit.Assert.*
import org.junit.Test

class ConversationModeTest {
    @Test fun savedModeAndLegacyDefault() {
        assertEquals(ConversationMode.SPEAK, ConversationMode.fromSaved(null))
        assertEquals(ConversationMode.SPEAK, ConversationMode.fromSaved("bad"))
        assertEquals(ConversationMode.CHAT, ConversationMode.fromSaved("CHAT"))
    }
    @Test fun chatNeverEnablesAudioAndSwitchingCannotUnmuteAnExistingReply() {
        val gate = ReplyAudioGate(ConversationMode.CHAT)
        gate.start()
        assertFalse(gate.audible)
        gate.select(ConversationMode.SPEAK)
        assertFalse(gate.audible)
        gate.start()
        assertTrue(gate.audible)
        gate.select(ConversationMode.CHAT)
        assertFalse(gate.audible)
        gate.start()
        assertFalse(gate.audible)
        gate.stop()
        assertFalse(gate.audible)
    }
    @Test fun latePacketsAfterStopStayMuted() {
        val gate = ReplyAudioGate(ConversationMode.SPEAK)
        gate.start()
        gate.stop()
        assertFalse(gate.audible)
    }
}
