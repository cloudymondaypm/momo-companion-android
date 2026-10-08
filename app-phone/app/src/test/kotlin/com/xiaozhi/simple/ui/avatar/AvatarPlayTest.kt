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
@Config(sdk = [29])
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

    @Test fun touchTargetsRejectEmptyCanvasAndOutOfBounds() {
        for (side in listOf(80f, 160f, 240f)) {
            val scale = side / 200f
            assertNull(AvatarTouch.locate(100f * scale, 15f * scale, side, side))
            assertNull(AvatarTouch.locate(35f * scale, 20f * scale, side, side))
            assertNull(AvatarTouch.locate(165f * scale, 20f * scale, side, side))
            assertEquals(AvatarPart.HEAD, AvatarTouch.locate(100f * scale, 95f * scale, side, side))
            assertEquals(AvatarPart.EARS, AvatarTouch.locate(65f * scale, 45f * scale, side, side))
            assertEquals(AvatarPart.NOSE, AvatarTouch.locate(100f * scale, 122f * scale, side, side))
            assertEquals(AvatarPart.HANDS, AvatarTouch.locate(48f * scale, 151f * scale, side, side))
        }
        assertNull(AvatarTouch.locate(-1f, 100f, 200f, 200f))
        assertNull(AvatarTouch.locate(Float.NaN, 100f, 200f, 200f))
        assertNull(AvatarTouch.locate(100f, 100f, Float.POSITIVE_INFINITY, 200f))
    }

    @Test fun gameRulesRejectWrongMovesAndAwardCompletionOnlyOnce() {
        val order = listOf(AvatarPart.HEAD, AvatarPart.EARS)
        assertEquals(GameMove(false, 0, false), AvatarGames.move(
            AvatarGame.MOMO_SAYS, 0, AvatarPart.NOSE, order))
        assertEquals(GameMove(true, 1, false), AvatarGames.move(
            AvatarGame.MOMO_SAYS, 0, AvatarPart.HEAD, order))
        assertEquals(GameMove(true, 2, true), AvatarGames.move(
            AvatarGame.MOMO_SAYS, 1, AvatarPart.EARS, order))
        assertEquals(GameMove(false, 2, false), AvatarGames.move(
            AvatarGame.MOMO_SAYS, 2, AvatarPart.EARS, order))
        assertEquals(GameMove(false, 0, false), AvatarGames.move(
            AvatarGame.NONE, 0, AvatarPart.BELLY))
        assertEquals(GameMove(false, 0, false), AvatarGames.move(
            AvatarGame.TICKLE_RACE, 0, AvatarPart.FEET))
        assertEquals(GameMove(true, 8, true), AvatarGames.move(
            AvatarGame.TICKLE_RACE, 7, AvatarPart.BELLY))
        assertEquals(GameMove(true, 6, true), AvatarGames.move(
            AvatarGame.DANCE_PARTY, 5, AvatarPart.FEET))
        assertEquals(GameMove(false, -1, false), AvatarGames.move(
            AvatarGame.DANCE_PARTY, -1, AvatarPart.FEET))
    }

    @Test fun surprisePolicyNeverInterruptsSpeakingOrGames() {
        assertTrue(AvatarIdle.canSurprise(true, false, false, false, false))
        assertFalse(AvatarIdle.canSurprise(false, false, false, false, false))
        assertFalse(AvatarIdle.canSurprise(true, true, false, false, false))
        assertFalse(AvatarIdle.canSurprise(true, false, true, false, false))
        assertFalse(AvatarIdle.canSurprise(true, false, false, true, false))
        assertFalse(AvatarIdle.canSurprise(true, false, false, false, true))
        assertEquals(16000L, AvatarIdle.SURPRISE_DELAY_MS)
        assertEquals(4, AvatarIdle.surprises.size)
        assertEquals(AvatarReaction.PEEK, AvatarIdle.nextSurprise(0))
        assertEquals(AvatarReaction.SILLY, AvatarIdle.nextSurprise(1))
        assertEquals(AvatarReaction.DANCE, AvatarIdle.nextSurprise(-1))
    }

    @Test fun peekAndSillyReactionsDrawDifferentExpressions() {
        val renderer = MomoRenderer()
        val peek = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val silly = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(peek), 200f, 200f, AvatarMood.HAPPY, 0f,
            false, false, AvatarReaction.PEEK)
        renderer.draw(Canvas(silly), 200f, 200f, AvatarMood.HAPPY, 0f,
            false, false, AvatarReaction.SILLY)
        assertFalse("Peekaboo and silly tongue must look different", peek.sameAs(silly))
        val later = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(later), 200f, 200f, AvatarMood.HAPPY, 9f,
            false, false, AvatarReaction.SILLY)
        assertTrue("Reduced motion should keep the silly pose static", silly.sameAs(later))
        peek.recycle()
        silly.recycle()
        later.recycle()
    }

    @Test fun hugTimeNeedsRealLongPressesNotTaps() {
        assertEquals(GameMove(false, 0, false),
            AvatarGames.move(AvatarGame.HUG_TIME, 0, AvatarPart.HEAD))
        assertEquals(GameMove(true, 1, false),
            AvatarGames.move(AvatarGame.HUG_TIME, 0, null))
        assertEquals(GameMove(true, 3, true),
            AvatarGames.move(AvatarGame.HUG_TIME, 2, null))
        assertEquals(GameMove(false, 3, false),
            AvatarGames.move(AvatarGame.HUG_TIME, 3, null))
        assertEquals(GameMove(false, 0, false),
            AvatarGames.move(AvatarGame.MOMO_SAYS, 0, null))
        assertEquals(3, AvatarGames.HUG_GOAL)
    }

    @Test fun adaptiveFrameRateUsesLowerIdleFrequency() {
        assertEquals(90L, AvatarMotion.frameDelayMs(false, AvatarReaction.NONE))
        assertEquals(40L, AvatarMotion.frameDelayMs(true, AvatarReaction.NONE))
        assertEquals(40L, AvatarMotion.frameDelayMs(false, AvatarReaction.CUDDLE))
        assertTrue(AvatarMotion.IDLE_FRAME_MS > AvatarMotion.ACTIVE_FRAME_MS)
    }

    @Test fun swipePettingHasUniqueFeedbackForEveryPart() {
        val messages = AvatarPart.entries.map { AvatarTouch.strokeCaption(it) }
        assertEquals(6, messages.size)
        assertEquals(6, messages.toSet().size)
        assertTrue(messages.all { it.isNotBlank() })
        assertEquals(AvatarReaction.TICKLE, AvatarTouch.reaction(AvatarPart.BELLY))
    }

    @Test fun playfulInteractionsHaveDistinctReactionsAndDanceSequence() {
        assertEquals(6, AvatarPart.entries.size)
        assertEquals(6, AvatarPart.entries.map { AvatarTouch.reaction(it) }.toSet().size)
        assertEquals(6, AvatarGames.danceSteps.size)
        assertEquals(AvatarPart.FEET, AvatarGames.danceSteps.first())
        assertEquals(AvatarPart.FEET, AvatarGames.danceSteps.last())
        assertTrue(AvatarGames.danceSteps.contains(AvatarPart.HANDS))
        assertEquals(7, AvatarOutfit.entries.size)
        assertEquals(7, AvatarAccessory.entries.size)
        assertEquals(5, AvatarGame.entries.size)
    }

    @Test fun newCuddleReactionRenders() {
        val renderer = MomoRenderer()
        val baseline = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(baseline), 200f, 200f, AvatarMood.HAPPY, 0f,
            false, false)
        val cuddle = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(cuddle), 200f, 200f, AvatarMood.HAPPY, 0f,
            false, false, AvatarReaction.CUDDLE)
        assertFalse("Cuddling should change the face, arms and hearts", baseline.sameAs(cuddle))
        baseline.recycle()
        cuddle.recycle()
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
