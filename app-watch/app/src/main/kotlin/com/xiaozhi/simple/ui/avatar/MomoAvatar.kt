package com.xiaozhi.simple.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xiaozhi.simple.model.AvatarMood
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MomoAvatar(mood: AvatarMood, speaking: Boolean, animated: Boolean, modifier: Modifier = Modifier) {
    val renderer = remember { MomoRenderer() }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        seconds = 0f
        if (animated) {
            val start = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                seconds = ((android.os.SystemClock.elapsedRealtime()-start) % 23000L)/1000f
                delay(50) // Gentle 20 fps, limited to this canvas while the app is visible.
            }
        }
    }
    Canvas(modifier.semantics { contentDescription = "Momo, ${mood.name.lowercase()}. Hold to talk." }) {
        drawIntoCanvas { renderer.draw(it.nativeCanvas,size.width,size.height,mood,seconds,speaking,animated) }
    }
}
