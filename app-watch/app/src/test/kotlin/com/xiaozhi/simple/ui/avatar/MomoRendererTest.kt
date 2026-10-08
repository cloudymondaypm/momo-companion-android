package com.xiaozhi.simple.ui.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.xiaozhi.simple.model.AvatarMood
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomoRendererTest {
    private fun frame(seconds: Float, animated: Boolean): Bitmap {
        val bitmap=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
        MomoRenderer().draw(Canvas(bitmap),160f,160f,AvatarMood.HAPPY,seconds,true,animated)
        return bitmap
    }
    @Test fun talkingAnimatesAndReducedMotionIsStable() {
        val first=frame(0f,true); val talking=frame(1.2f,true); val blink=frame(4.45f,true)
        assertFalse(first.sameAs(talking)); assertFalse(first.sameAs(blink))
        val still=frame(0f,false); val later=frame(4.45f,false)
        assertTrue(still.sameAs(later))
        listOf(first,talking,blink,still,later).forEach { it.recycle() }
    }
    @Test fun allExpressionsRenderAtWatchSizesAndStayInsideCanvas() {
        for (dimension in listOf(64, 128, 240)) {
            for (mood in AvatarMood.entries) {
                val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                MomoRenderer().draw(canvas,dimension.toFloat(),dimension.toFloat(),mood,1.2f,true,true)
                assertTrue("$mood should have a visible face", Color.alpha(bitmap.getPixel(dimension/2,dimension/2)) > 0)
                for (i in 0 until dimension) {
                    assertEquals("Drawing clipped at top: $mood",0,Color.alpha(bitmap.getPixel(i,0)))
                    assertEquals("Drawing clipped at bottom: $mood",0,Color.alpha(bitmap.getPixel(i,dimension-1)))
                }
                bitmap.recycle()
            }
        }
    }
    @Test fun generateActualRendererExpressionSheet() {
        val moods = listOf(AvatarMood.HAPPY,AvatarMood.EXCITED,AvatarMood.CURIOUS,
            AvatarMood.CARING,AvatarMood.LISTENING,AvatarMood.THINKING,AvatarMood.CALM,AvatarMood.HAPPY)
        val bitmap = Bitmap.createBitmap(960, 580, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(255,249,240))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(85,77,108);textSize=18f;textAlign=Paint.Align.CENTER }
        moods.forEachIndexed { index,mood ->
            val x=(index%4)*240f;val y=(index/4)*290f
            canvas.save();canvas.translate(x,y+10)
            MomoRenderer().draw(canvas,240f,240f,mood,1.2f,index==7,true)
            canvas.restore()
            canvas.drawText(if(index==7) "TALKING" else mood.name,x+120,y+266,text)
        }
        val file=File("build/reports/momo-expressions.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
        run {
            val icon=org.robolectric.RuntimeEnvironment.getApplication().resources
                .getDrawable(com.xiaozhi.simple.R.drawable.ic_launcher_watch,null)
            val iconBitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
            icon.setBounds(0,0,192,192)
            icon.draw(Canvas(iconBitmap))
            File("build/reports/momo-companion-icon.png").outputStream().use { iconBitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            iconBitmap.recycle()
        }
    }
}
