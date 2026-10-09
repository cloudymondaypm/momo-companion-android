package com.xiaozhi.simple.model

object TypedChat {
    const val MAX_LENGTH = 2000
    fun valid(text: String): Boolean =
        text.trim().isNotEmpty() && text.trim().length <= MAX_LENGTH &&
        text.none { it == '\u0000' }
}
