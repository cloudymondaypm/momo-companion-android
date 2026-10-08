package com.xiaozhi.simple.model

import java.util.UUID

/**
 * Message type
 */
enum class MessageType {
    USER,    // User message
    AI,      // AI response
    SYSTEM   // System message
}

/**
 * Message data model
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val type: MessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
