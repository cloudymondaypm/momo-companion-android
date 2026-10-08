package com.xiaozhi.simple.model

enum class CompanionMood(val label: String, val caption: String) {
    HAPPY("Happy", "Hello! What shall we discover?"),
    EXCITED("Excited", "Hooray! Let's explore together!"),
    CURIOUS("Curious", "Ooh, tell me more!"),
    THINKING("Thinking", "A little moment to wonder..."),
    CARING("Caring", "I'm here to listen with kindness."),
    SURPRISED("Surprised", "Oh! That's a new discovery!"),
    LOVING("Warm", "Sending a little sprinkle of kindness!"),
    SLEEPY("Sleepy", "Let's take things gently."),
    STEADY("Encouraging", "One little step at a time. You've got this!");

    companion object {
        fun fromServer(raw: String): CompanionMood? = when (raw.trim().lowercase()) {
            "happy", "neutral", "funny", "silly", "confident" -> HAPPY
            "laughing", "excited", "joyful" -> EXCITED
            "curious", "confused", "thinking" -> CURIOUS
            "sad", "crying", "angry", "scared", "worried", "embarrassed", "shocked" -> CARING
            "surprised" -> SURPRISED
            "loving", "love", "kissy", "winking", "delicious" -> LOVING
            "sleepy", "tired", "relaxed" -> SLEEPY
            "determined" -> STEADY
            else -> null
        }
        // A light visual fallback, not an assessment of the child's emotional state.
        fun fromText(text: String): CompanionMood {
            val words = Regex("[\\p{L}']+").findAll(text.lowercase()).map { it.value }.toSet()
            return when {
                words.any { it in setOf("sad", "hurt", "afraid", "scared", "lonely", "worried", "cry", "died", "bully", "angry", "sorry") } -> CARING
                words.any { it in setOf("sleep", "sleepy", "bedtime", "tired") } -> SLEEPY
                words.any { it in setOf("love", "kind", "kindness", "thank", "thanks", "friend") } -> LOVING
                words.any { it in setOf("hooray", "yay", "birthday", "celebrate", "won", "awesome") } -> EXCITED
                words.any { it in setOf("try", "practice", "learn", "brave") } -> STEADY
                '?' in text -> CURIOUS
                else -> HAPPY
            }
        }
    }
}
