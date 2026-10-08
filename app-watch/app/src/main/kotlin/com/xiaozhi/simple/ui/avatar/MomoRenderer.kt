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
             seconds: Float, speaking: Boolean, animated: Boolean) {
        val scale = minOf(width, height) / 200f
        if (scale <= 0f) return
        val wave = if (animated) sin(seconds * 2.5f) else 0f
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
        canvas.translate(0f, wave*hop)
        canvas.rotate(if (mood == AvatarMood.CURIOUS) -5f+wave else wave*1.5f,100f,135f)
        // Soft ears, little feet, and rounded marshmallow face.
        fill(mint); canvas.drawOval(52f,13f,83f,100f,paint); canvas.drawOval(117f,13f,148f,100f,paint)
        fill(Color.rgb(255,205,216)); canvas.drawOval(60f,23f,75f,79f,paint); canvas.drawOval(125f,23f,140f,79f,paint)
        fill(mint); canvas.drawOval(60f,159f,91f,183f,paint); canvas.drawOval(109f,159f,140f,183f,paint)
        paint.shader=gradient; canvas.drawOval(35f,58f,165f,178f,paint); paint.shader=null
        fill(Color.argb(110,255,255,255)); canvas.drawOval(49f,70f,80f,88f,paint)
        // Tiny cloud tuft.
        fill(Color.rgb(222,255,232)); canvas.drawCircle(92f,62f,9f,paint); canvas.drawCircle(104f,60f,11f,paint)
        fill(Color.argb(150,255,153,180)); canvas.drawOval(48f,121f,71f,134f,paint); canvas.drawOval(129f,121f,152f,134f,paint)
        val softEyes = blink || mood == AvatarMood.CALM || mood == AvatarMood.SLEEPY || mood == AvatarMood.EXCITED
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
        if (speaking || mood==AvatarMood.EXCITED) {
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
        fill(Color.rgb(168,233,211)); canvas.drawOval(40f,143f,64f,165f,paint); canvas.drawOval(136f,143f,160f,165f,paint)
        if (mood==AvatarMood.CARING) { fill(Color.rgb(255,218,117)); star(canvas,100f,158f,12f) }
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
        canvas.restore()
    }
}
