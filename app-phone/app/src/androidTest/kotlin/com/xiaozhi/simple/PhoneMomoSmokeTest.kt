package com.xiaozhi.simple

import android.graphics.Bitmap
import android.graphics.Color
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.xiaozhi.simple.ui.avatar.MomoDepthView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhoneMomoSmokeTest {
    @Test fun offlinePhoneRendersDepthAndKeepsPlayDressAndTypingAccessible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("fold5_config",0).edit()
            .putBoolean("auto_connect",false).putBoolean("depth_graphics",true)
            .putBoolean("animate_avatar",false).commit()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.text("Momo Companion")),10000))
            device.waitForIdle()
            var depth: MomoDepthView? = null
            fun find(view: View): MomoDepthView? {
                if (view is MomoDepthView) return view
                if (view is ViewGroup) for(i in 0 until view.childCount)
                    find(view.getChildAt(i))?.let { return it }
                return null
            }
            scenario.onActivity { depth = find(it.window.decorView) }
            assertNotNull("3D view should be present, not silently replaced",depth)
            val bitmap = Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
            var result = -1
            repeat(5) {
                if (result != PixelCopy.SUCCESS) {
                    val copied = CountDownLatch(1)
                    scenario.onActivity {
                        PixelCopy.request(depth!!,bitmap,{ code -> result = code; copied.countDown() },
                            Handler(Looper.getMainLooper()))
                    }
                    assertTrue(copied.await(5,TimeUnit.SECONDS))
                    if(result != PixelCopy.SUCCESS) Thread.sleep(200)
                }
            }
            assertEquals(PixelCopy.SUCCESS,result)
            var mintPixels = 0
            for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x,y)
                if(Color.alpha(color)>100 && Color.green(color)>Color.red(color)+8 &&
                    Color.green(color)>120) mintPixels++
            }
            assertTrue("GL output must contain the mint character",mintPixels>100)
            bitmap.recycle()
            instrumentation.uiAutomation.takeScreenshot()?.let { shot ->
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,"phone-momo-depth.png")
                    put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Momo")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
                context.contentResolver.openOutputStream(uri)!!.use {
                    shot.compress(Bitmap.CompressFormat.PNG,100,it)
                }
                shot.recycle()
            }
            assertTrue(device.wait(Until.hasObject(By.text("Dress")),5000))
            device.findObject(By.text("Dress")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Momo's wardrobe")),5000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Play")),5000))
            device.findObject(By.text("Play")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Play Momo Says")),5000))
            device.findObject(By.text("Play Momo Says")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Stop")),5000))
            device.findObject(By.text("Stop")).click()
            val composer = device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)
            assertNotNull("Typed chat editor must be available offline",composer)
            composer.text = "Hello Momo"
            val sendButton = device.findObject(By.desc("Send typed message"))
            assertNotNull("Accessible Send button must be present",sendButton)
            assertFalse("Send must be disabled offline",sendButton.isEnabled)
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Hello Momo")),5000))
        }
    }
}
