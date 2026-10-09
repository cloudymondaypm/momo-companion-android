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
import org.junit.Rule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhoneMomoSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun offlinePhoneRendersDepthAndKeepsPlayDressAndTypingAccessible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("fold5_config",0).edit()
            .putBoolean("auto_connect",false).putBoolean("depth_graphics",true)
            .putBoolean("animate_avatar",false).commit()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.text("Momo Companion")),10000))
            compose.onNodeWithText("Chat").performClick()
            compose.waitUntil(5000) {
                compose.onNodeWithText("Text replies only Â· Momo's voice is off").isDisplayed()
            }
            compose.onNodeWithText("Text replies only Â· Momo's voice is off").assertIsDisplayed()
            scenario.recreate()
            compose.waitUntil(5000) {
                compose.onNodeWithText("Text replies only Â· Momo's voice is off").isDisplayed()
            }
            compose.onNodeWithText("Text replies only Â· Momo's voice is off").assertIsDisplayed()
            compose.onNodeWithText("Speak").performClick()
            compose.waitUntil(5000) {
                compose.onNodeWithText("Voice replies on Â· hold to talk or type below").isDisplayed()
            }
            compose.onNodeWithText("Voice replies on Â· hold to talk or type below").assertIsDisplayed()
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
                val caption = device.wait(Until.findObject(By.textContains("Hi!")),5000)!!
                val bounds = caption.visibleBounds
                val background = shot.getPixel((bounds.left - 3).coerceAtLeast(0),bounds.centerY())
                assertTrue("3D surface must preserve the light card and readable captions",
                    Color.red(background)>100 && Color.green(background)>100)
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
            compose.onNodeWithText("Dress").performScrollTo().performClick()
            compose.onNodeWithText("Momo's wardrobe").assertIsDisplayed()
            device.pressBack()
            compose.onNodeWithText("Play").performScrollTo().performClick()
            compose.onNodeWithText("Play Momo Says").assertIsDisplayed()
            compose.onNodeWithText("Play Momo Says").performClick()
            compose.onNodeWithText("Stop").performScrollTo().performClick()
            // Compose resolves current nodes after IME resize instead of retaining stale handles.
            compose.onNode(hasSetTextAction()).assertIsDisplayed().performClick()
            device.waitForIdle()
            compose.onNode(hasSetTextAction()).performTextInput("Hello Momo")
            compose.onNodeWithText("Hello Momo").assertIsDisplayed()
            compose.onNodeWithContentDescription("Send typed message").assertIsDisplayed()
            compose.onNodeWithText("Send").assertIsNotEnabled()
            device.pressBack()
            compose.onNodeWithText("Hello Momo").assertIsDisplayed()
        }
    }
}
