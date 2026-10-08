package com.xiaozhi.simple.model

import org.junit.Assert.*
import org.junit.Test

class AvatarMoodTest {
    @Test fun careComesBeforeCelebration() {
        assertEquals(AvatarMood.CARING, AvatarMoodResolver.fromText("I won a game, but I am sad and scared"))
        assertEquals(AvatarMood.CARING, AvatarMoodResolver.fromText("Malungkot ako"))
    }
    @Test fun questionCelebrationAndRestHaveDifferentExpressions() {
        assertEquals(AvatarMood.CURIOUS, AvatarMoodResolver.fromText("Why is the sky blue?"))
        assertEquals(AvatarMood.EXCITED, AvatarMoodResolver.fromText("Hooray! Great job!"))
        assertEquals(AvatarMood.CALM, AvatarMoodResolver.fromText("Let's breathe and relax"))
    }
    @Test fun wordsAreNotMatchedInsideOtherWords() {
        assertEquals(AvatarMood.HAPPY, AvatarMoodResolver.fromText("The window has a curtain"))
        assertEquals(AvatarMood.HAPPY, AvatarMoodResolver.fromText("A sadle is a made up word"))
    }
    @Test fun serverEmotionsUseGentleChildFriendlyExpressions() {
        assertEquals(AvatarMood.CARING, AvatarMoodResolver.fromServer("angry"))
        assertEquals(AvatarMood.CURIOUS, AvatarMoodResolver.fromServer("thinking"))
        assertNull(AvatarMoodResolver.fromServer("unknown"))
    }
}
