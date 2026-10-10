package com.xiaozhi.simple.service

import android.content.SharedPreferences
import com.xiaozhi.simple.model.VoiceBackend
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class VoiceCredentialStoreTest {
    // Platform interfaces backed by a test map; crypto is independently checked on Android.
    private class Fixture {
        val preferences = mutableMapOf<String, String>()
        val secrets = mutableMapOf<String, String>()
        var rejectWrites = false
        val prefs: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> preferences[args!![0]] ?: args[1]
                "edit" -> Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)) { editor, edit, values ->
                    when (edit.name) {
                        "putString" -> { preferences[values!![0] as String] = values[1] as String; editor }
                        "commit" -> true
                        else -> error(edit.name)
                    }
                }
                else -> error(method.name)
            }
        } as SharedPreferences
        val store = VoiceCredentialStore(prefs) { purpose -> object : CredentialCipherStore {
            override fun read() = secrets[purpose].orEmpty()
            override fun write(token: String) { check(!rejectWrites); secrets[purpose] = token }
        } }
    }

    @Test fun migratesLegacyPairingAndXiaozhiWithoutCrossingIdentities() {
        val f = Fixture()
        f.preferences["momo_device_id"] = "paired-device"
        f.secrets["momo"] = "paired-token"
        f.secrets["server"] = "xiaozhi-token"
        f.store.migrate("wss://xiaozhi.example/ws", "phone-id")
        assertEquals("paired-token", f.store.readMomo())
        assertEquals("xiaozhi-token", f.store.readXiaozhi("wss://xiaozhi.example/ws", "phone-id"))
        assertEquals("", f.secrets["momo"])
        assertEquals("", f.secrets["server"])
        assertTrue(f.preferences.values.none { it.contains("token") })
        assertEquals("", f.store.readXiaozhi("wss://other.example/ws", "phone-id"))
        assertEquals("", f.store.readXiaozhi("wss://xiaozhi.example/ws", "watch-id"))
        f.store.migrate("wss://xiaozhi.example/ws", "phone-id")
        assertEquals("paired-token", f.store.readMomo())
    }

    @Test fun rePairingRestoresOnlyTheCredentialForThatMomoDevice() {
        val f = Fixture()
        f.store.bindMomo(MomoPairingService.BoundDevice("first", "first-token"))
        f.store.bindMomo(MomoPairingService.BoundDevice("second", "second-token"))
        assertEquals("second-token", f.store.readMomo())
        f.preferences["momo_device_id"] = "first"
        assertEquals("first-token", f.store.readMomo())
        assertEquals("", f.store.readXiaozhi("wss://example.com", "first"))
    }

    @Test fun unsuccessfulMigrationKeepsTheExistingCiphertext() {
        val f = Fixture()
        f.preferences["momo_device_id"] = "paired-device"
        f.secrets["momo"] = "existing-token"
        f.rejectWrites = true
        assertTrue(runCatching { f.store.migrate("wss://example.com", "phone") }.isFailure)
        assertEquals("existing-token", f.secrets["momo"])
        f.rejectWrites = false
        f.store.migrate("wss://example.com", "phone")
        assertEquals("existing-token", f.store.readMomo())
    }

    @Test fun legacySettingsDoNotOptInAndSelectionIsStoredPerDevice() {
        val f = Fixture()
        f.preferences["server_url"] = "wss://legacy.example/ws"
        val first = VoiceBackend.selectionKey("first-paired-device")
        val second = VoiceBackend.selectionKey("second-paired-device")
        assertEquals(VoiceBackend.MOMO, VoiceBackend.fromSaved(f.preferences[first]))
        f.prefs.edit().putString(first, VoiceBackend.XIAOZHI.name).commit()
        assertEquals(VoiceBackend.XIAOZHI, VoiceBackend.fromSaved(f.preferences[first]))
        assertEquals(VoiceBackend.MOMO, VoiceBackend.fromSaved(f.preferences[second]))
        assertEquals(VoiceBackend.MOMO, VoiceBackend.fromSaved("obsolete"))
    }

    @Test fun xiaozhiCannotUseTheMomoOriginOrUrlCredentials() {
        assertTrue(VoiceBackend.validXiaozhiEndpoint("wss://xiaozhi.example/ws", "wss"))
        for (url in listOf("wss://ai.momolegend.fun/api/device/conversation", "wss://AI.MOMOLEGEND.FUN/ws",
            "wss://ai.momolegend.fun.:443/ws", "wss://token@xiaozhi.example/ws", "wss://xiaozhi.example/ws?token=secret",
            "ws://xiaozhi.example/ws", "wss://xiaozhi.example/ws#secret")) {
            assertFalse(url, VoiceBackend.validXiaozhiEndpoint(url, "wss"))
        }
    }
}
