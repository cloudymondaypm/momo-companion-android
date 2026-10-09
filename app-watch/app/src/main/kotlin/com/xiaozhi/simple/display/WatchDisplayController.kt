package com.xiaozhi.simple.display

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-only display idle policy, supported on API 26/27. No WRITE_SETTINGS,
 * device-admin locking, hidden APIs or global brightness/timeout changes.
 * Zero window brightness means the panel's minimum; Android owns physical sleep.
 */
class WatchDisplayController(context: Context, private val onIdle: () -> Unit = {}) {
    private val prefs = context.getSharedPreferences("momo_watch_display", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val windows = linkedMapOf<Window, SavedWindow>()
    private var resumed = false
    private val _sleeping = MutableStateFlow(false)
    val sleeping: StateFlow<Boolean> = _sleeping
    private val _timeoutSeconds = MutableStateFlow(
        validTimeout(prefs.getInt("timeout_seconds", DEFAULT_TIMEOUT_SECONDS)))
    val timeoutSeconds: StateFlow<Int> = _timeoutSeconds
    private val expire = Runnable {
        if (resumed) {
            _sleeping.value = true
            onIdle()
            applyWindows()
        }
    }

    private data class SavedWindow(
        val callback: Window.Callback, val brightness: Float, val buttonBrightness: Float
    )

    fun attach(window: Window) {
        if (window in windows) return
        val saved = SavedWindow(window.callback, window.attributes.screenBrightness,
            window.attributes.buttonBrightness)
        windows[window] = saved
        window.callback = object : Window.Callback by saved.callback {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                val wasSleeping = _sleeping.value
                if (event.actionMasked == MotionEvent.ACTION_DOWN || !wasSleeping) interact()
                // Swallow the entire wake gesture, including its release.
                if (wasSleeping || swallowTouch) {
                    swallowTouch = event.actionMasked != MotionEvent.ACTION_UP &&
                        event.actionMasked != MotionEvent.ACTION_CANCEL
                    return true
                }
                return saved.callback.dispatchTouchEvent(event)
            }
            private var swallowTouch = false
            private var wakeKey: Int? = null
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (_sleeping.value) {
                    wakeKey = event.keyCode
                    interact()
                    return true
                }
                if (wakeKey == event.keyCode) {
                    if (event.action == KeyEvent.ACTION_UP) wakeKey = null
                    return true
                }
                interact()
                return saved.callback.dispatchKeyEvent(event)
            }
        }
        applyWindows()
    }

    fun detach(window: Window) {
        val saved = windows.remove(window) ?: return
        window.callback = saved.callback
        restore(window, saved)
    }

    fun resume() {
        resumed = true
        interact()
    }

    fun pause() {
        resumed = false
        handler.removeCallbacks(expire)
        _sleeping.value = false
        applyWindows()
    }

    fun interact() {
        if (!resumed) return
        _sleeping.value = false
        applyWindows()
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, _timeoutSeconds.value * 1000L)
    }

    /** Settings apply immediately and persist without reconnecting the server. */
    fun setTimeout(seconds: Int) {
        _timeoutSeconds.value = validTimeout(seconds)
        prefs.edit().putInt("timeout_seconds", _timeoutSeconds.value).apply()
        interact()
    }

    fun close() {
        pause()
        windows.keys.toList().forEach(::detach)
    }

    private fun restore(window: Window, saved: SavedWindow) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = saved.brightness
            buttonBrightness = saved.buttonBrightness
        }
    }

    private fun applyWindows() {
        windows.forEach { (window, saved) ->
            if (resumed && !_sleeping.value) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            window.attributes = window.attributes.apply {
                screenBrightness = if (_sleeping.value) 0f else saved.brightness
                buttonBrightness = if (_sleeping.value) 0f else saved.buttonBrightness
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 120
        val TIMEOUT_OPTIONS = listOf(30, 60, 120, 300, 600)
        fun validTimeout(seconds: Int): Int =
            seconds.takeIf { it in TIMEOUT_OPTIONS } ?: DEFAULT_TIMEOUT_SECONDS
    }
}
