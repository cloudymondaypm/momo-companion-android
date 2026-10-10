package com.xiaozhi.simple.service

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VoiceCredentialEncryptionTest {
    @Test fun scopedCredentialsPersistAsCiphertextAndCanBeRestored() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences("voice-credential-test-$id", 0)
        val vault = VoiceCredentialStore(prefs)
        try {
            vault.bindMomo(MomoPairingService.BoundDevice(id, "momo-test-secret-$id"))
            vault.writeXiaozhi("wss://xiaozhi.example/ws", id, "xiaozhi-test-secret-$id")
            val restored = VoiceCredentialStore(prefs)
            assertEquals("momo-test-secret-$id", restored.readMomo())
            assertEquals("xiaozhi-test-secret-$id", restored.readXiaozhi("wss://xiaozhi.example/ws", id))
            assertEquals("", restored.readXiaozhi("wss://other.example/ws", id))
            assertTrue(prefs.all.values.none { it.toString().contains("test-secret") })
        } finally { prefs.edit().clear().commit() }
    }
}
