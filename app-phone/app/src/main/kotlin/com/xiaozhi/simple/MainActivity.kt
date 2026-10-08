package com.xiaozhi.simple

import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import com.xiaozhi.simple.display.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.xiaozhi.simple.ui.screen.MainScreen
import com.xiaozhi.simple.ui.theme.XiaozhiSimpleTheme
import com.xiaozhi.simple.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: MainViewModel
    private lateinit var display: WatchDisplayController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[MainViewModel::class.java]
        display = WatchDisplayController(this) { model.endPtt() }
        display.attach(window)
        setContent { XiaozhiSimpleTheme {
            CompositionLocalProvider(LocalWatchDisplay provides display) {
                MainScreen(model)
                WatchSleepScreen(display)
            }
        } }
    }
    override fun onResume() { super.onResume(); display.resume(); model.foreground(true) }
    override fun onPause() { display.pause(); model.foreground(false); super.onPause() }
    override fun onDestroy() { display.close(); super.onDestroy() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::model.isInitialized) model.focus(hasFocus)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (::model.isInitialized && model.handleHardwareKey(event)) true else super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (::model.isInitialized && model.handleHardwareKey(event)) true else super.onKeyUp(keyCode, event)
}
