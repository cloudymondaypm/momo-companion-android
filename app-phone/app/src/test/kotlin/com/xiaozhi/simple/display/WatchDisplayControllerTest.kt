package com.xiaozhi.simple.display

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@LooperMode(LooperMode.Mode.PAUSED)
class WatchDisplayControllerTest {
    private lateinit var activity: Activity
    private lateinit var display: WatchDisplayController
    private var idleCalls = 0

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("momo_watch_display", Context.MODE_PRIVATE).edit().clear().commit()
        display = WatchDisplayController(activity) { idleCalls++ }
        display.attach(activity.window)
        display.resume()
    }

    private fun advance(seconds: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    private fun keptOn() = activity.window.attributes.flags and
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    @Test fun defaultExpiresAtTwoMinutesAndDoesNotChangeGlobalSettings() {
        val resolver = activity.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 45000)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 180)
        advance(119)
        assertFalse(display.sleeping.value)
        assertTrue(keptOn())
        advance(1)
        assertTrue(display.sleeping.value)
        assertEquals(1, idleCalls)
        assertFalse(keptOn())
        assertEquals(0f, activity.window.attributes.screenBrightness, 0f)
        assertEquals(45000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT))
        assertEquals(180, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
        display.close()
    }

    @Test fun interactionResetsDeadlineAndChangingTimeoutPersists() {
        advance(100)
        display.interact()
        advance(100)
        assertFalse(display.sleeping.value)
        display.setTimeout(30)
        advance(29)
        assertFalse(display.sleeping.value)
        advance(1)
        assertTrue(display.sleeping.value)
        val next = WatchDisplayController(activity)
        assertEquals(30, next.timeoutSeconds.value)
        next.close()
        display.close()
    }

    @Test fun pauseCancelsTimerAndRestoresWindowBrightness() {
        display.setTimeout(30)
        advance(30)
        display.pause()
        assertFalse(display.sleeping.value)
        assertFalse(keptOn())
        assertEquals(-1f, activity.window.attributes.screenBrightness, 0f)
        advance(300)
        assertEquals(1, idleCalls)
        display.resume()
        assertTrue(keptOn())
        advance(30)
        assertEquals(2, idleCalls)
        display.close()
    }

    @Test fun wakeTouchAndHardwareKeyAreConsumedIncludingRelease() {
        display.setTimeout(30)
        advance(30)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 10f, 10f, 0)
        assertTrue(activity.window.callback.dispatchTouchEvent(down))
        assertFalse(display.sleeping.value)
        assertTrue(activity.window.callback.dispatchTouchEvent(up))
        down.recycle()
        up.recycle()
        advance(30)
        assertTrue(activity.window.callback.dispatchKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
        assertFalse(display.sleeping.value)
        assertTrue(activity.window.callback.dispatchKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
        display.close()
    }

    @Test fun dialogsShareDeadlineAndRestoreOnDetach() {
        val dialog = android.app.Dialog(activity)
        dialog.show()
        val window = dialog.window!!
        val callback = window.callback
        window.attributes = window.attributes.apply { screenBrightness = 0.7f }
        display.attach(window)
        display.setTimeout(30)
        advance(30)
        assertEquals(0f, window.attributes.screenBrightness, 0f)
        assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        display.interact()
        assertEquals(0.7f, window.attributes.screenBrightness, 0f)
        display.detach(window)
        assertSame(callback, window.callback)
        dialog.dismiss()
        display.close()
    }

    @Test fun invalidPreferenceFallsBackToDefault() {
        assertEquals(120, WatchDisplayController.validTimeout(-1))
        assertEquals(120, WatchDisplayController.validTimeout(Int.MAX_VALUE))
        assertEquals(600, WatchDisplayController.validTimeout(600))
        activity.getSharedPreferences("momo_watch_display", Context.MODE_PRIVATE)
            .edit().putInt("timeout_seconds", -1).commit()
        val next = WatchDisplayController(activity)
        assertEquals(120, next.timeoutSeconds.value)
        next.close()
        display.close()
    }
}
