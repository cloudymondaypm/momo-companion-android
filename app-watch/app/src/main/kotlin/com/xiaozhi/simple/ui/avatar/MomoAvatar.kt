package com.xiaozhi.simple.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xiaozhi.simple.model.AvatarMood
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The avatar is a pettable play area. Voice input has its own PTT control. */
@Composable
fun MomoAvatar(
    mood: AvatarMood,
    speaking: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier,
    reaction: AvatarReaction = AvatarReaction.NONE,
    style: AvatarStyle = AvatarStyle(),
    onTouch: (AvatarPart) -> Unit = {}
) {
    val renderer = remember { MomoRenderer() }
    var seconds by remember { mutableFloatStateOf(0f) }
    // Restart the gesture movement when a new reaction is requested.
    var reactionId by remember { mutableIntStateOf(0) }
    LaunchedEffect(reaction) { reactionId++ }
    LaunchedEffect(animated, reactionId) {
        seconds = 0f
        if (animated) {
            val start = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                seconds = ((android.os.SystemClock.elapsedRealtime() - start) % 23000L) / 1000f
                delay(50) // 20fps; paused while offscreen or reduce-motion is enabled.
            }
        }
    }
    Canvas(
        modifier
            .semantics { contentDescription = "Momo, ${mood.name.lowercase()}. Tap to pet or play." }
            .pointerInput(onTouch) {
                detectTapGestures { offset ->
                    AvatarTouch.locate(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                        ?.let(onTouch)
                }
            }
    ) {
        drawIntoCanvas {
            renderer.draw(it.nativeCanvas, size.width, size.height, mood, seconds, speaking,
                animated, reaction, style)
        }
    }
}
