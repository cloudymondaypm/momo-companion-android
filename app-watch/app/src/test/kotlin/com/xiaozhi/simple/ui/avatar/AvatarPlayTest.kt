package com.xiaozhi.simple.ui.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import com.xiaozhi.simple.model.AvatarMood
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarPlayTest {
    @Test fun touchesMapToCorrectBodyParts() {
        assertEquals(AvatarPart.EARS, AvatarTouch.locate(65f, 45f, 200f, 200f))
        assertEquals(AvatarPart.HEAD, AvatarTouch.locate(100f, 95f, 200f, 200f))
        assertEquals(AvatarPart.NOSE, AvatarTouch.locate(100f, 122f, 200f, 200f))
        assertEquals(AvatarPart.BELLY, AvatarTouch.locate(100f, 158f, 200f, 200f))
        assertEquals(AvatarPart.FEET, AvatarTouch.locate(70f, 178f, 200f, 200f))
        assertEquals(AvatarPart.HANDS, AvatarTouch.locate(48f, 151f, 200f, 200f))
        assertNull(AvatarTouch.locate(2f, 2f, 200f, 200f))
        assertNull(AvatarTouch.locate(100f, 100f, 0f, 0f))
    }

    @Test fun touchZonesAccountForLetterboxing() {
        // 200x300 view: vector stage starts 50px below the top.
        assertEquals(AvatarPart.NOSE, AvatarTouch.locate(100f, 172f, 200f, 300f))
        assertNull(AvatarTouch.locate(100f, 15f, 200f, 300f))
        // 300x200 view: vector stage starts 50px from the left.
        assertEquals(AvatarPart.BELLY, AvatarTouch.locate(150f, 158f, 300f, 200f))
        assertNull(AvatarTouch.locate(20f, 100f, 300f, 200f))
    }

    @Test fun everyOutfitAndAccessoryRendersAndReducedMotionIsStable() {
        val renderer = MomoRenderer()
        val plain = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(plain), 200f, 200f, AvatarMood.HAPPY, 1.0f,
            false, false)
        var differences = 0
        for (outfit in AvatarOutfit.entries) {
            for (accessory in AvatarAccessory.entries) {
                val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
                val style = AvatarStyle(outfit, accessory)
                renderer.draw(Canvas(bmp), 200f, 200f, AvatarMood.HAPPY, 0f,
                    false, false, AvatarReaction.WAVE, style)
                val later = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(later), 200f, 200f, AvatarMood.HAPPY, 9f,
                    false, false, AvatarReaction.WAVE, style)
                assertTrue("Reduce-motion must freeze animation", bmp.sameAs(later))
                if (!plain.sameAs(bmp)) differences++
                bmp.recycle()
                later.recycle()
            }
        }
        assertTrue("Outfits and accessories should produce visible differences", differences > 0)
        plain.recycle()
    }
}
