package com.xiaozhi.simple

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.xiaozhi.simple.ui.screen.MainScreen
import com.xiaozhi.simple.ui.theme.XiaozhiSimpleTheme
import com.xiaozhi.simple.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: MainViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[MainViewModel::class.java]
        setContent { XiaozhiSimpleTheme { MainScreen(model) } }
    }
    override fun onResume() { super.onResume(); model.foreground(true) }
    override fun onPause() { model.foreground(false); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::model.isInitialized) model.focus(hasFocus)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (::model.isInitialized && model.handleHardwareKey(event)) true else super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (::model.isInitialized && model.handleHardwareKey(event)) true else super.onKeyUp(keyCode, event)
}
