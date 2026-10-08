package com.xiaozhi.simple.model

/**
 * Connection state
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Device state
 */
enum class DeviceState {
    IDLE,        // Idle
    LISTENING,   // Listening
    SPEAKING     // Speaking
}
