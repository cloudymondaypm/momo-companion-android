package com.xiaozhi.simple.model

import org.junit.Assert.*
import org.junit.Test

class CompanionMoodTest {
    @Test fun difficultTopicsReceiveGentleSupportEvenAlongsideCelebrationWords() {
        assertEquals(CompanionMood.CARING, CompanionMood.fromText("It's my birthday but I feel sad and lonely"))
        assertEquals(CompanionMood.CARING, CompanionMood.fromServer("angry"))
        assertEquals(CompanionMood.CARING, CompanionMood.fromServer("crying"))
    }
    @Test fun unknownMetadataCanFallBackWithoutCrashing() {
        assertNull(CompanionMood.fromServer("future_emotion"))
        assertEquals(CompanionMood.HAPPY, CompanionMood.fromText("A strawberry is red"))
        assertEquals(CompanionMood.EXCITED, CompanionMood.fromText("Yay, we won!"))
        assertEquals(CompanionMood.CURIOUS, CompanionMood.fromText("How does a rainbow work?"))
        assertEquals(CompanionMood.SLEEPY, CompanionMood.fromText("It is bedtime"))
    }
}
