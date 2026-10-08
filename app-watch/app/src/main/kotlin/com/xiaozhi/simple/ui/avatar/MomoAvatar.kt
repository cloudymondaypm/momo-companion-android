package com.xiaozhi.simple.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
    reactionTick: Int = 0,
    style: AvatarStyle = AvatarStyle(),
    onTouch: (AvatarPart) -> Unit = {},
    onPet: (AvatarPart) -> Unit = {},
    onCuddle: () -> Unit = {}
) {
    val renderer = remember { MomoRenderer() }
    // Avoid cancelling ongoing gestures during avatar recompositions.
    val currentTouch by rememberUpdatedState(onTouch)
    val currentPet by rememberUpdatedState(onPet)
    val currentCuddle by rememberUpdatedState(onCuddle)
    var seconds by remember { mutableFloatStateOf(0f) }
    // Each tap restarts its animation, even if the same body part is tapped twice.
    LaunchedEffect(animated, reactionTick) {
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
            .semantics { contentDescription = "Momo, ${mood.name.lowercase()}. Tap to play, swipe to pet, hold to cuddle." }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        if (AvatarTouch.locate(offset.x, offset.y,
                                size.width.toFloat(), size.height.toFloat()) != null) currentCuddle()
                    },
                    onTap = { offset ->
                        AvatarTouch.locate(offset.x, offset.y,
                            size.width.toFloat(), size.height.toFloat())?.let(currentTouch)
                    }
                )
            }
            .pointerInput(Unit) {
                var petPart: AvatarPart? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        petPart = AvatarTouch.locate(offset.x, offset.y,
                            size.width.toFloat(), size.height.toFloat())
                    },
                    onDragEnd = {
                        petPart?.let(currentPet)
                        petPart = null
                    },
                    onDragCancel = { petPart = null },
                    onDrag = { change, _ -> change.consume() }
                )
            }
    ) {
        drawIntoCanvas {
            renderer.draw(it.nativeCanvas, size.width, size.height, mood, seconds, speaking,
                animated, reaction, style)
        }
    }
}
