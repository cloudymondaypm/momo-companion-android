package com.xiaozhi.simple.model

enum class ConversationMode { CHAT, SPEAK;
    companion object {
        fun fromSaved(value: String?) = entries.firstOrNull { it.name == value } ?: SPEAK
    }
}

/** A silent reply stays silent if Speak is selected before that reply finishes. */
class ReplyAudioGate(mode: ConversationMode) {
    var mode = mode
        private set
    var audible = false
        private set
    fun select(value: ConversationMode) {
        mode = value
        audible = false
    }
    fun start() { audible = mode == ConversationMode.SPEAK }
    fun stop() { audible = false }
}
