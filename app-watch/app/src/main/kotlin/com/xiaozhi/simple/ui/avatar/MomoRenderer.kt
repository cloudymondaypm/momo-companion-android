package com.xiaozhi.simple.ui.avatar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.xiaozhi.simple.model.AvatarMood
import kotlin.math.sin

/** Native vector art: scales to the watch display without bitmap or video assets. */
class MomoRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val mint = Color.rgb(184, 242, 219)
    private val ink = Color.rgb(45, 70, 77)
    private val pink = Color.rgb(255, 153, 180)
    private val gradient = LinearGradient(45f, 65f, 155f, 162f,
        Color.rgb(226, 255, 235), mint, Shader.TileMode.CLAMP)

    private fun fill(color: Int) { paint.shader = null; paint.color = color; paint.style = Paint.Style.FILL }
    private fun line(color: Int, width: Float = 3f) {
        fill(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
    }
    private fun curve(canvas: Canvas, x: Float, y: Float, cx: Float, cy: Float, endX: Float, endY: Float) {
        path.reset(); path.moveTo(x, y); path.quadTo(cx, cy, endX, endY); canvas.drawPath(path, paint)
    }
    private fun star(canvas: Canvas, x: Float, y: Float, radius: Float) {
        path.reset(); path.moveTo(x, y-radius); path.lineTo(x+radius*.28f, y-radius*.28f)
        path.lineTo(x+radius, y); path.lineTo(x+radius*.28f, y+radius*.28f)
        path.lineTo(x, y+radius); path.lineTo(x-radius*.28f, y+radius*.28f)
        path.lineTo(x-radius, y); path.lineTo(x-radius*.28f, y-radius*.28f); path.close()
        canvas.drawPath(path, paint)
    }
    private fun heart(canvas: Canvas, x: Float, y: Float) {
        path.reset(); path.moveTo(x, y+7); path.cubicTo(x-18,y-3,x-8,y-14,x,y-5)
        path.cubicTo(x+8,y-14,x+18,y-3,x,y+7); path.close(); canvas.drawPath(path,paint)
    }

    fun draw(canvas: Canvas, width: Float, height: Float, mood: AvatarMood,
             seconds: Float, speaking: Boolean, animated: Boolean,
             reaction: AvatarReaction = AvatarReaction.NONE,
             style: AvatarStyle = AvatarStyle()) {
        val scale = minOf(width, height) / 200f
        if (scale <= 0f) return
        val wave = if (animated) sin(seconds * 2.5f) else 0f
        val wiggle = if (animated) sin(seconds * 14f) else 0f
        // Reactions burst then settle over the 1.9-second gesture window.
        // Reduced-motion mode always shows a stable pose, regardless of elapsed time.
        val burst = if (animated) (1f - seconds / 1.9f).coerceIn(0f, 1f) else 1f
        val dance = if (animated && reaction == AvatarReaction.DANCE) sin(seconds * 12f) * 12f * burst else 0f
        val tickle = if (animated && reaction == AvatarReaction.TICKLE) wiggle * 5f * burst else 0f
        val jump = if (animated && reaction == AvatarReaction.CELEBRATE) -kotlin.math.abs(sin(seconds * 8f)) * 15f * burst else 0f
        val hop = if (mood == AvatarMood.EXCITED) 5f else 2.4f
        val blink = animated && (seconds % 4.6f) > 4.36f
        canvas.save()
        canvas.translate((width-200f*scale)/2f, (height-200f*scale)/2f)
        canvas.scale(scale, scale)
        fill(Color.argb(24, 81, 139, 125))
        canvas.drawOval(56f+wave, 180f, 144f-wave, 190f, paint)
        if (mood == AvatarMood.LISTENING) {
            line(Color.argb(110, 70, 190, 158), 2.3f)
            canvas.drawCircle(100f,110f,75f+wave*2f,paint)
            canvas.drawArc(12f,71f,35f,147f,115f,125f,false,paint)
            canvas.drawArc(165f,71f,188f,147f,-60f,125f,false,paint)
        }
        canvas.save()
        canvas.translate(tickle, wave * hop + jump)
        val tilt = when (reaction) {
            AvatarReaction.PAT -> -6f
            AvatarReaction.BOOP -> 7f
            AvatarReaction.WIGGLE -> wiggle * 8f
            AvatarReaction.WAVE -> 5f
            AvatarReaction.CUDDLE -> -5f
            else -> 0f
        }
        canvas.rotate((if (mood == AvatarMood.CURIOUS) -5f + wave else wave * 1.5f) +
            tilt + dance, 100f, 135f)
        // Independently animated ears and feet make reactions more expressive.
        for ((baseX, direction) in listOf(52f to -1f, 117f to 1f)) {
            canvas.save()
            val earSway = if (animated) sin(seconds * 2.1f + direction) * 2.2f else 0f
            val earWiggle = if (animated && reaction == AvatarReaction.WIGGLE)
                wiggle * 11f * burst * direction else 0f
            canvas.rotate(earSway + earWiggle, baseX + 15.5f, 95f)
            fill(mint); canvas.drawOval(baseX,13f,baseX+31f,100f,paint)
            fill(Color.rgb(255,205,216))
            canvas.drawOval(baseX+8f,23f,baseX+23f,79f,paint)
            canvas.restore()
        }
        // The superhero cape sits behind Momo.
        if (style.outfit == AvatarOutfit.HERO) {
            fill(Color.rgb(255, 111, 147))
            path.reset(); path.moveTo(70f, 139f); path.quadTo(52f, 165f, 44f, 182f)
            path.lineTo(156f, 182f); path.quadTo(148f, 165f, 130f, 139f); path.close()
            canvas.drawPath(path, paint)
        }
        val leftKick = if (animated && reaction == AvatarReaction.DANCE)
            sin(seconds * 13f) * 8f * burst else 0f
        fill(mint); canvas.drawOval(60f,159f+leftKick,91f,183f+leftKick,paint)
        canvas.drawOval(109f,159f-leftKick,140f,183f-leftKick,paint)
        paint.shader=gradient; canvas.drawOval(35f,58f,165f,178f,paint); paint.shader=null
        if (style.outfit != AvatarOutfit.CLASSIC) {
            // Rounded tunic below the face, scaled as vector art even on tiny watches.
            fill(style.outfit.color)
            canvas.drawRoundRect(68f,145f,132f,173f,13f,13f,paint)
            fill(Color.argb(90,255,255,255))
            canvas.drawOval(79f,148f,121f,154f,paint)
            when (style.outfit) {
                AvatarOutfit.PAJAMAS -> {
                    fill(Color.rgb(255, 247, 175)); star(canvas, 99f, 159f, 6f)
                    fill(Color.rgb(255, 247, 175))
                    canvas.drawCircle(82f, 160f, 2f, paint)
                    canvas.drawCircle(118f, 165f, 2f, paint)
                }
                AvatarOutfit.HERO -> {
                    fill(Color.rgb(255, 238, 130)); star(canvas, 100f, 159f, 9f)
                    fill(Color.rgb(255, 111, 147)); star(canvas, 100f, 159f, 4f)
                }
                else -> Unit
            }
        }
        fill(Color.argb(110,255,255,255)); canvas.drawOval(49f,70f,80f,88f,paint)
        // Tiny cloud tuft.
        fill(Color.rgb(222,255,232)); canvas.drawCircle(92f,62f,9f,paint); canvas.drawCircle(104f,60f,11f,paint)
        fill(Color.argb(150,255,153,180)); canvas.drawOval(48f,121f,71f,134f,paint); canvas.drawOval(129f,121f,152f,134f,paint)
        val softEyes = blink || reaction == AvatarReaction.PAT || reaction == AvatarReaction.TICKLE ||
            reaction == AvatarReaction.CELEBRATE || reaction == AvatarReaction.CUDDLE || mood == AvatarMood.CALM ||
            mood == AvatarMood.SLEEPY || mood == AvatarMood.EXCITED
        for (x in listOf(76f,124f)) {
            if (softEyes) {
                line(ink,3.4f)
                curve(canvas,x-7f,110f,x,if(mood==AvatarMood.EXCITED)100f else 116f,x+7f,110f)
            } else {
                val glance = if (mood==AvatarMood.THINKING) 2f else 0f
                fill(ink); canvas.drawOval(x-5.5f+glance,99f,x+5.5f+glance,115f,paint)
                fill(Color.WHITE); canvas.drawCircle(x-1.5f+glance,103f,2.3f,paint)
            }
        }
        if (mood==AvatarMood.CARING) {
            line(ink,2f); curve(canvas,69f,93f,76f,96f,82f,90f); curve(canvas,118f,90f,124f,96f,131f,93f)
        } else if (mood==AvatarMood.CURIOUS || mood==AvatarMood.THINKING) {
            line(ink,2f); curve(canvas,116f,92f,123f,86f,131f,90f)
        }
        fill(pink); canvas.drawOval(96f,118f,104f,123f,paint)
        if (speaking || mood==AvatarMood.EXCITED || reaction==AvatarReaction.BOOP ||
            reaction==AvatarReaction.TICKLE || reaction==AvatarReaction.CELEBRATE) {
            val opening = if (speaking && animated) 7f+5f*(.5f+.5f*sin(seconds*13f)) else 9f
            fill(ink); canvas.drawOval(90f,131f,110f,131f+opening,paint)
            fill(pink); canvas.drawOval(94f,134f+opening*.3f,106f,130f+opening,paint)
        } else if (mood==AvatarMood.CURIOUS || mood==AvatarMood.THINKING) {
            fill(ink); canvas.drawOval(97f,132f,104f,140f,paint)
        } else {
            line(ink,2.7f); curve(canvas,88f,131f,94f,140f,100f,132f)
            curve(canvas,100f,132f,106f,140f,112f,131f)
        }
        // Arms hug a little star when offering comfort.
        fill(Color.rgb(168,233,211))
        if (reaction == AvatarReaction.WAVE) {
            canvas.drawOval(30f,112f - wiggle * 5f,53f,143f - wiggle * 5f,paint)
            canvas.drawOval(136f,143f,160f,165f,paint)
        } else if (reaction == AvatarReaction.CUDDLE) {
            // Both paws fold inward to offer a hug.
            canvas.drawOval(66f, 139f, 92f, 158f, paint)
            canvas.drawOval(108f, 139f, 134f, 158f, paint)
        } else {
            canvas.drawOval(40f,143f,64f,165f,paint)
            canvas.drawOval(136f,143f,160f,165f,paint)
        }
        if (mood==AvatarMood.CARING) { fill(Color.rgb(255,218,117)); star(canvas,100f,158f,12f) }
        when (style.accessory) {
            AvatarAccessory.NONE -> Unit
            AvatarAccessory.BOW -> {
                fill(Color.rgb(252,123,166))
                canvas.drawOval(125f,43f,144f,57f,paint)
                canvas.drawOval(141f,43f,159f,57f,paint)
                fill(Color.rgb(255,214,114)); canvas.drawCircle(142f,50f,5f,paint)
            }
            AvatarAccessory.HAT -> {
                fill(Color.rgb(159,132,223))
                path.reset(); path.moveTo(72f,71f); path.lineTo(105f,8f)
                path.lineTo(130f,71f); path.close(); canvas.drawPath(path,paint)
                fill(Color.rgb(255,211,117)); canvas.drawCircle(105f,9f,7f,paint)
                line(Color.rgb(255,150,181),4f); canvas.drawLine(74f,69f,129f,69f,paint)
            }
            AvatarAccessory.GLASSES -> {
                line(ink,3f); canvas.drawCircle(76f,107f,18f,paint)
                canvas.drawCircle(124f,107f,18f,paint)
                curve(canvas,94f,103f,100f,99f,106f,103f)
            }
            AvatarAccessory.SCARF -> {
                fill(Color.rgb(180,131,231))
                canvas.drawRoundRect(66f,141f,134f,151f,5f,5f,paint)
                canvas.drawRoundRect(116f,147f,127f,166f,4f,4f,paint)
            }
            AvatarAccessory.CROWN -> {
                fill(Color.rgb(255, 202, 73))
                path.reset(); path.moveTo(69f,74f); path.lineTo(69f,52f)
                path.lineTo(84f,63f); path.lineTo(101f,42f); path.lineTo(117f,63f)
                path.lineTo(133f,52f); path.lineTo(133f,74f); path.close()
                canvas.drawPath(path,paint)
                fill(Color.rgb(255, 127, 172)); canvas.drawCircle(101f,62f,5f,paint)
            }
            AvatarAccessory.HEADPHONES -> {
                line(Color.rgb(120, 105, 191), 5f)
                canvas.drawArc(60f,65f,140f,147f,188f,164f,false,paint)
                fill(Color.rgb(120, 105, 191))
                canvas.drawRoundRect(52f,100f,65f,129f,6f,6f,paint)
                canvas.drawRoundRect(135f,100f,148f,129f,6f,6f,paint)
            }
        }
        canvas.restore()
        when(mood) {
            AvatarMood.HAPPY, AvatarMood.CARING -> { fill(pink); heart(canvas,172f,58f+wave*2f) }
            AvatarMood.EXCITED -> {
                fill(Color.rgb(255,203,92)); star(canvas,27f,58f-wave*3f,9f); star(canvas,174f,87f+wave*3f,11f)
                fill(Color.rgb(183,161,237)); star(canvas,160f,28f,6f)
            }
            AvatarMood.CURIOUS, AvatarMood.THINKING -> {
                for(i in 0..2) { fill(Color.rgb(157,137,214))
                    canvas.drawCircle(157f+i*12f,43f,2.6f+if(animated) (1f+sin(seconds*3f-i))*.8f else 0f,paint) }
            }
            AvatarMood.CALM, AvatarMood.SLEEPY -> {
                fill(Color.rgb(183,161,237)); canvas.drawCircle(171f,48f,10f,paint)
                fill(Color.rgb(255,249,240)); canvas.drawCircle(175f,44f,9f,paint)
            }
            else -> Unit
        }
        if (reaction == AvatarReaction.CUDDLE) {
            fill(Color.rgb(255, 126, 171))
            val floatUp = if (animated) (1f - burst) * 12f else 0f
            heart(canvas, 28f, 88f - floatUp)
            heart(canvas, 174f, 106f - floatUp)
        }
        if (reaction == AvatarReaction.BOOP || reaction == AvatarReaction.TICKLE ||
            reaction == AvatarReaction.CELEBRATE) {
            fill(Color.rgb(255,195,98))
            star(canvas,24f,88f + wave * 3f,7f)
            star(canvas,175f,72f - wave * 3f,9f)
        }
        canvas.restore()
    }
}
