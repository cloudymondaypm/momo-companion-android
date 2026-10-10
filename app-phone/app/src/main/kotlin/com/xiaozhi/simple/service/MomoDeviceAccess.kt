package com.xiaozhi.simple.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stored pairing is distinct from authorization on each independently checked endpoint. */
class MomoDeviceAccess(
    private val tokenProvider: () -> String,
    private val textChat: MomoChatService,
    private val conversation: MomoConversationService
) {
    private val storedPairing = MutableStateFlow(tokenProvider().isNotBlank())
    val paired: StateFlow<Boolean> = storedPairing

    fun credential(): String = tokenProvider().also { storedPairing.value = it.isNotBlank() }

    private fun requireCredential(): String = credential().also {
        require(it.isNotBlank()) { "No saved Momo credential is available. Pair this phone in Settings." }
    }

    // Typed chat retains its working HTTP route; a voice rejection cannot disable it.
    suspend fun chat(text: String): String = textChat.chat(requireCredential(), text)
    suspend fun voice(text: String, language: String): String = conversation.chat(requireCredential(), text, language)
    suspend fun checkVoice() { conversation.checkConnection(requireCredential()) }
}
