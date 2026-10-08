package com.xiaozhi.simple.model

import java.util.Locale

enum class AvatarMood(val caption: String) {
    HAPPY("Hi! Let's explore!"), EXCITED("Hooray! You did it!"),
    CURIOUS("Ooh, let's discover!"), CARING("I'm here with you"),
    CALM("One little breath"), LISTENING("I'm listening"),
    THINKING("Hmm, let me think"), SLEEPY("Ready when you are")
}

/** A small local expression cue, not a psychological assessment. No extra network requests. */
object AvatarMoodResolver {
    private fun contains(text: String, words: List<String>): Boolean = words.any { word ->
        if (word.any { it.code > 127 }) text.contains(word)
        else Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").containsMatchIn(text)
    }
    fun fromText(input: String): AvatarMood {
        val text = input.lowercase(Locale.ROOT)
        // Empathy takes precedence over celebration in mixed conversations.
        if (contains(text, listOf("sad", "cry", "crying", "scared", "afraid", "worried", "lonely",
                "hurt", "angry", "upset", "miss", "sorry", "hard day", "malungkot", "takot",
                "难过", "害怕", "伤心", "😢", "😔", "😭"))) return AvatarMood.CARING
        if (contains(text, listOf("breathe", "calm", "relax", "rest", "sleep", "goodnight",
                "bedtime", "quiet", "peaceful", "晚安", "睡觉"))) return AvatarMood.CALM
        if (contains(text, listOf("hooray", "yay", "awesome", "congratulations", "well done",
                "great job", "you did it", "won", "birthday", "excited", "haha", "hahaha",
                "太棒", "开心", "🎉", "🥳", "😂"))) return AvatarMood.EXCITED
        if (text.contains('?') || text.contains('？') || contains(text,
                listOf("why", "how", "wonder", "discover", "explore", "imagine", "learn", "bakit", "为什么")))
            return AvatarMood.CURIOUS
        return AvatarMood.HAPPY
    }
    fun fromServer(emotion: String): AvatarMood? = when (emotion.lowercase(Locale.ROOT)) {
        "happy", "smiling", "loving" -> AvatarMood.HAPPY
        "laughing", "excited", "funny", "silly", "cool", "confident" -> AvatarMood.EXCITED
        "curious", "surprised", "confused", "thinking" -> AvatarMood.CURIOUS
        "sad", "crying", "angry", "scared", "shocked", "embarrassed" -> AvatarMood.CARING
        "calm", "relaxed", "sleepy" -> AvatarMood.CALM
        else -> null
    }
}
