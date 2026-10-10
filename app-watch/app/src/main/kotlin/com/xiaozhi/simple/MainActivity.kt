package com.xiaozhi.simple

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            MomoCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.foreground(true)
    }
    override fun onPause() {
        if (::viewModel.isInitialized) viewModel.foreground(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::viewModel.isInitialized) viewModel.foreground(hasFocus)
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
