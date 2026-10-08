package com.xiaozhi.simple.model

/** Local play never depends on transport state. Voice requires a completed handshake. */
object WatchAvailability {
    fun canTalk(state: ConnectionState): Boolean = state is ConnectionState.Connected
    fun mood(state: ConnectionState, device: DeviceState, awaitingReply: Boolean,
             serverMood: AvatarMood): AvatarMood = when {
        !canTalk(state) -> AvatarMood.HAPPY
        device == DeviceState.LISTENING -> AvatarMood.LISTENING
        awaitingReply -> AvatarMood.THINKING
        else -> serverMood
    }
}
