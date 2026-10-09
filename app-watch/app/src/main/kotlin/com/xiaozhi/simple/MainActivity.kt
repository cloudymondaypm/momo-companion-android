package com.xiaozhi.simple

import android.os.Bundle
import android.view.KeyEvent
import com.xiaozhi.simple.display.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.xiaozhi.simple.ui.screen.MainScreen
import com.xiaozhi.simple.ui.theme.MomoCompanionTheme
import com.xiaozhi.simple.viewmodel.MainViewModel

/**
 * Kiumo ZH23 watch entry point.
 *
 * Hardware keys are forwarded to the ViewModel so a delivered Android key can
 * be learned and used as push-to-talk. Android normally does NOT deliver the
 * power key to foreground apps; the learn screen makes that limitation easy to
 * diagnose on the real watch.
 */
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var display: WatchDisplayController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        display = WatchDisplayController(this) { viewModel.onPressEnd() }
        display.attach(window)

        setContent {
            MomoCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompositionLocalProvider(LocalWatchDisplay provides display) {
                        MainScreen(viewModel = viewModel)
                        WatchSleepScreen(display)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        display.resume()
    }

    override fun onDestroy() {
        display.close()
        super.onDestroy()
    }

    override fun onPause() {
        display.pause()
        if (::viewModel.isInitialized) viewModel.onPressEnd()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::viewModel.isInitialized) viewModel.onPressEnd()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::viewModel.isInitialized && viewModel.handleHardwareKey(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (::viewModel.isInitialized && viewModel.handleHardwareKey(event)) return true
        return super.onKeyUp(keyCode, event)
    }
}
