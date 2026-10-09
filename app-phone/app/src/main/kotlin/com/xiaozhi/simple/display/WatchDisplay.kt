package com.xiaozhi.simple.display

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

val LocalWatchDisplay = staticCompositionLocalOf<WatchDisplayController> {
    error("Watch display controller missing")
}

/** Dialog windows also receive idle/wake input and release their screen-on flags. */
@Composable
fun BindWatchDialog() {
    val controller = LocalWatchDisplay.current
    val window = (LocalView.current.parent as DialogWindowProvider).window
    DisposableEffect(controller, window) {
        controller.attach(window)
        onDispose { controller.detach(window) }
    }
}

@Composable
fun WatchSleepScreen(controller: WatchDisplayController) {
    val sleeping by controller.sleeping.collectAsState()
    if (sleeping) {
        Dialog(
            onDismissRequest = { controller.interact() },
            properties = DialogProperties(usePlatformDefaultWidth = false,
                dismissOnClickOutside = false)
        ) {
            BindWatchDialog()
            val window = (LocalView.current.parent as DialogWindowProvider).window
            DisposableEffect(window) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                onDispose { }
            }
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}
