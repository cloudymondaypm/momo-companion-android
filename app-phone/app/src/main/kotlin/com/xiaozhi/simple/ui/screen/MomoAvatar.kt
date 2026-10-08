package com.xiaozhi.simple.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import com.xiaozhi.simple.model.CompanionMood
import kotlinx.coroutines.delay
import kotlin.math.*

/** Original, resolution-independent plush bunny. Avatar taps never activate the microphone. */
@Composable
fun MomoAvatar(mood: CompanionMood, listening: Boolean, speaking: Boolean,
    animated: Boolean, modifier: Modifier = Modifier) {
    var wave by remember { mutableStateOf(false) }
    LaunchedEffect(wave) { if (wave) { delay(1800); wave = false } }
    val p = if (animated) motionPhase() else 0f
    val bob = if (animated) sin(p * 2 * PI).toFloat() * 3 else 0f
    val ink = Color(0xFF344E59)
    val mint = Color(0xFFBDEDDC)
    val pink = Color(0xFFF7A8BB)
    Canvas(modifier.semantics {
        contentDescription = "Momo, a cute mint bunny. ${mood.label}. Tap to wave."
        onClick(label = "Wave to Momo") { wave = true; true }
    }.pointerInput(Unit) { detectTapGestures { wave = true } }) {
        val unit = min(size.width, size.height) / 320f
        withTransform({ translate((size.width - 320 * unit) / 2, (size.height - 320 * unit) / 2); scale(unit, unit, Offset.Zero) }) {
            drawCircle(Color(0xFFFFEBD0), 126f, Offset(160f, 163f))
            drawCircle(Color(0xFFFFF4E1), 115f, Offset(154f, 157f))
            drawOval(ink.copy(alpha = .09f), Offset(90f, 281f), Size(140f, 15f))
            fun star(x: Float, y: Float, r: Float, color: Color) {
                val path = Path()
                repeat(10) { i ->
                    val a = i * PI / 5 - PI / 2
                    val radius = if (i % 2 == 0) r else r * .46
                    val px = x + cos(a).toFloat() * radius.toFloat()
                    val py = y + sin(a).toFloat() * radius.toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close(); drawPath(path, color)
            }
            star(45f, 117f + bob, 10f, Color(0xFFE9B950))
            star(278f, 172f - bob, if (mood == CompanionMood.EXCITED) 15f else 9f, Color(0xFFC0A0EB))
            withTransform({ translate(0f, bob) }) {
                rotate(-14f + bob, Offset(120f, 101f)) {
                    drawOval(mint, Offset(88f, 29f), Size(49f, 113f))
                    drawOval(Color(0xFFFFCED9), Offset(100f, 42f), Size(24f, 70f))
                }
                rotate(14f - bob, Offset(201f, 101f)) {
                    drawOval(mint, Offset(181f, 29f), Size(49f, 113f))
                    drawOval(Color(0xFFFFCED9), Offset(193f, 42f), Size(24f, 70f))
                }
                drawOval(mint, Offset(95f, 187f), Size(130f, 98f))
                drawOval(Color(0xFFE9FFF5), Offset(119f, 204f), Size(82f, 68f))
                drawOval(mint, Offset(80f, 264f), Size(67f, 27f))
                drawOval(mint, Offset(173f, 264f), Size(67f, 27f))
                drawOval(mint, Offset(68f, 96f), Size(184f, 135f))
                drawOval(Color(0xFFD7F7EA), Offset(82f, 103f), Size(156f, 115f))
                drawOval(pink.copy(alpha = .68f), Offset(88f, 170f), Size(34f, 19f))
                drawOval(pink.copy(alpha = .68f), Offset(199f, 170f), Size(34f, 19f))
                val blink = animated && p > .92f && p < .96f
                listOf(128f, 192f).forEach { x ->
                    when {
                        blink || mood == CompanionMood.SLEEPY -> drawLine(ink, Offset(x - 10f, 155f), Offset(x + 10f, 155f), 5f, StrokeCap.Round)
                        mood == CompanionMood.EXCITED || mood == CompanionMood.LOVING -> drawArc(ink, 195f, 150f, false, Offset(x - 11f, 144f), Size(22f, 23f), style = Stroke(5f, cap = StrokeCap.Round))
                        else -> {
                            val tall = if (mood == CompanionMood.SURPRISED) 32f else 26f
                            drawOval(ink, Offset(x - 9f, 143f), Size(18f, tall))
                            drawCircle(Color.White, 4f, Offset(x - 2f, 149f))
                            drawCircle(Color.White.copy(alpha = .65f), 2f, Offset(x + 4f, 158f))
                        }
                    }
                }
                if (mood == CompanionMood.CARING || mood == CompanionMood.THINKING || listening) {
                    drawLine(ink, Offset(117f, 134f), Offset(137f, 130f), 3f, StrokeCap.Round)
                    drawLine(ink, Offset(183f, 130f), Offset(203f, 134f), 3f, StrokeCap.Round)
                }
                if (mood == CompanionMood.STEADY) {
                    drawLine(ink, Offset(117f, 131f), Offset(137f, 135f), 3f, StrokeCap.Round)
                    drawLine(ink, Offset(183f, 135f), Offset(203f, 131f), 3f, StrokeCap.Round)
                }
                drawOval(pink, Offset(155f, 173f), Size(10f, 7f))
                if (speaking || mood == CompanionMood.SURPRISED) {
                    val open = if (speaking && animated) 10f + abs(sin(p * 16 * PI)).toFloat() * 13f else 14f
                    drawOval(ink, Offset(150f, 185f), Size(20f, open))
                    drawOval(pink, Offset(154f, 187f + open * .55f), Size(12f, open * .35f))
                } else drawArc(ink, 15f, 150f, false, Offset(144f, 178f), Size(32f, if (mood == CompanionMood.CARING) 14f else 22f), style = Stroke(3.5f, cap = StrokeCap.Round))
                drawRoundRect(Color(0xFFCAB8EF), Offset(111f, 218f), Size(98f, 18f), androidx.compose.ui.geometry.CornerRadius(9f))
                drawRoundRect(Color(0xFFCAB8EF), Offset(183f, 225f), Size(18f, 35f), androidx.compose.ui.geometry.CornerRadius(7f))
                star(160f, 229f, 12f, Color(0xFFFFD36D))
                drawOval(mint, Offset(79f, 224f), Size(38f, 40f))
                rotate(if (wave && animated) sin(p * 12 * PI).toFloat() * 22f - 30f else if (listening) -18f else 10f, Offset(220f, 233f)) {
                    drawOval(mint, Offset(207f, if (wave || listening) 193f else 224f), Size(36f, 44f))
                    drawCircle(pink.copy(alpha = .6f), 7f, Offset(225f, if (wave || listening) 210f else 242f))
                }
                if (mood == CompanionMood.LOVING) {
                    drawCircle(pink, 7f, Offset(270f, 80f)); drawCircle(pink, 7f, Offset(281f, 80f))
                    drawPath(Path().apply { moveTo(263f, 82f); lineTo(288f, 82f); lineTo(275f, 98f); close() }, pink)
                }
                if (mood == CompanionMood.THINKING || mood == CompanionMood.CURIOUS) {
                    repeat(3) { drawCircle(Color(0xFFB89BDF), 3f + it * 2, Offset(249f + it * 15, 103f - it * 14)) }
                }
            }
        }
    }
}

@Composable
private fun motionPhase(): Float {
    val cycle = rememberInfiniteTransition(label = "Momo breathing")
    val phase by cycle.animateFloat(0f, 1f,
        infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "gentle motion")
    return phase
}
