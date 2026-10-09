package com.xiaozhi.simple.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhi.simple.model.*
import com.xiaozhi.simple.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.URI
import java.security.SecureRandom
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("fold5_config", Context.MODE_PRIVATE)
    private val tokens = TokenStore(prefs)
    private val socket = WebSocketService()
    private val audio = AudioService(application)
    private val setup = ServerSetupService()
    private var setupJob: Job? = null
    private val _setupInfo = MutableStateFlow("")
    val setupInfo: StateFlow<String> = _setupInfo
    private val _setupBusy = MutableStateFlow(false)
    val setupBusy: StateFlow<Boolean> = _setupBusy
    private val clientId = prefs.getString("client_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("client_id", it).apply()
    }
    private fun newDeviceId(): String {
        val bytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
        bytes[0] = ((bytes[0].toInt() or 2) and 254).toByte()
        return bytes.joinToString(":") { "%02x".format(it.toInt() and 255) }
    }
    private val defaultDeviceId = prefs.getString("device_id", null) ?: newDeviceId().also {
        prefs.edit().putString("device_id", it).apply()
    }
    private val _config = MutableStateFlow(XiaozhiConfig(
        serverUrl = prefs.getString("server_url", null) ?: XiaozhiConfig().serverUrl,
        otaUrl = prefs.getString("ota_url", null) ?: XiaozhiConfig().otaUrl,
        token = tokens.read(), deviceId = defaultDeviceId,
        autoConnect = prefs.getBoolean("auto_connect", true),
        volumePtt = prefs.getInt("volume_ptt", 0),
        animateAvatar = prefs.getBoolean("animate_avatar", true),
        depthGraphics = prefs.getBoolean("depth_graphics", false),
        conversationMode = ConversationMode.fromSaved(prefs.getString("conversation_mode", null))
    ))
    val config: StateFlow<XiaozhiConfig> = _config
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _deviceState = MutableStateFlow(DeviceState.IDLE)
    val deviceState: StateFlow<DeviceState> = _deviceState
    val connectionState = socket.connectionState
    val isRecording = audio.isRecording
    private val _notice = MutableStateFlow("")
    val notice: StateFlow<String> = _notice
    private val _mood = MutableStateFlow(CompanionMood.HAPPY)
    val mood: StateFlow<CompanionMood> = _mood
    private var serverMood = false
    private var moodReset: Job? = null
    private fun textMood(text: String, reply: Boolean = false) {
        if (serverMood) return
        val next = CompanionMood.fromText(text)
        // Keep an empathetic topic expression through neutral reply sentences.
        if (!reply || next != CompanionMood.HAPPY || _mood.value == CompanionMood.THINKING)
            _mood.value = next
    }
    private var foreground = false
    private var focused = false
    private var settingsOpen = false
    private var wantsConnection = _config.value.autoConnect
    private var owner: String? = null
    private var ignoreTts = false
    private val replyAudio = ReplyAudioGate(_config.value.conversationMode)
    private val pendingTypedEchoes = java.util.ArrayDeque<String>()

    init {
        socket.onEmotion = { raw -> viewModelScope.launch {
            if (foreground && owner == null) CompanionMood.fromServer(raw)?.let {
                serverMood = true; _mood.value = it
            }
        } }
        socket.onSttMessage = { text -> viewModelScope.launch {
            if (foreground) {
                if (!pendingTypedEchoes.remove(text.trim())) { textMood(text); addMessage(Message(type = MessageType.USER, content = text)) }
            }
        } }
        socket.onTextMessage = { text -> viewModelScope.launch { if (!ignoreTts) { textMood(text, reply = true); addMessage(Message(type = MessageType.AI, content = text)) } } }
        socket.onTtsStateChanged = { state -> viewModelScope.launch {
            if (!foreground || owner != null) return@launch
            when (state) {
                "start" -> {
                    ignoreTts = false
                    replyAudio.start()
                    if (replyAudio.audible) {
                        _deviceState.value = DeviceState.SPEAKING
                        audio.startPlayback()
                    } else _deviceState.value = DeviceState.IDLE
                }
                "stop" -> {
                    if (!ignoreTts && replyAudio.audible) audio.finishPlayback()
                    else _deviceState.value = DeviceState.IDLE
                    replyAudio.stop()
                }
            }
        } }
        socket.onAudioData = { data -> viewModelScope.launch {
            if (foreground && owner == null && !ignoreTts && replyAudio.audible) {
                _deviceState.value = DeviceState.SPEAKING
                audio.playAudio(data)
            }
        } }
        socket.onError = { text -> viewModelScope.launch { _notice.value = text; stopInteraction() } }
        audio.onAudioData = { packet -> if (!socket.sendAudio(packet)) viewModelScope.launch {
            _notice.value = "Audio could not be sent. Hold PTT again after reconnecting."
            stopInteraction()
        } }
        audio.onError = { text -> viewModelScope.launch { _notice.value = text; stopInteraction() } }
        audio.onPlaybackFinished = { viewModelScope.launch { if (owner == null) _deviceState.value = DeviceState.IDLE } }
        viewModelScope.launch { connectionState.collect { state ->
            if (state !is ConnectionState.Connected) stopInteraction() else _notice.value = ""
        } }
        viewModelScope.launch { isRecording.collect { recording ->
            if (!recording && owner != null) endPtt()
        } }
    }

    fun foreground(active: Boolean) {
        foreground = active
        if (active) { if (wantsConnection) connect() }
        else { focused = false; setupJob?.cancel(); stopInteraction(); socket.disconnect() }
    }
    fun focus(hasFocus: Boolean) { focused = hasFocus; if (!hasFocus) endPtt() }
    fun settings(open: Boolean) { settingsOpen = open; endPtt() }
    fun connect() {
        wantsConnection = true
        if (!foreground) return
        val cfg = config.value
        socket.connect(cfg.serverUrl, cfg.deviceId, clientId, cfg.token)
    }
    fun disconnect() { wantsConnection = false; stopInteraction(); socket.disconnect() }
    fun connectToPresetServer() {
        if (saveConfig(config.value.copy(
            serverUrl = XiaozhiConfig().serverUrl, otaUrl = XiaozhiConfig().otaUrl))) connect()
    }
    /** Typing needs the server, but has no microphone-permission dependency. */
    fun sendText(text: String): Boolean {
        if (!foreground || !focused || settingsOpen ||
            connectionState.value !is ConnectionState.Connected || !TypedChat.valid(text)) {
            _notice.value = "Connect to the server to send a message (up to 2000 characters)."
            return false
        }
        endPtt()
        stopReply()
        if (!socket.sendText(text)) {
            _notice.value = "Message was not sent. Reconnect and try again."
            return false
        }
        val sent = text.trim()
        pendingTypedEchoes.addLast(sent)
        while (pendingTypedEchoes.size > 100) pendingTypedEchoes.removeFirst()
        // Keep stale playback suppressed until the server starts the new TTS reply.
        serverMood = false
        textMood(sent)
        addMessage(Message(type = MessageType.USER, content = sent))
        _notice.value = ""
        _mood.value = CompanionMood.THINKING
        moodReset?.cancel()
        moodReset = viewModelScope.launch {
            delay(20000)
            if (_mood.value == CompanionMood.THINKING) _mood.value = CompanionMood.HAPPY
        }
        return true
    }

    fun clearMessages() { _messages.value = emptyList() }
    fun getServerSetup() {
        if (_setupBusy.value) return
        val cfg = config.value
        endPtt()
        setupJob = viewModelScope.launch {
            _setupBusy.value = true
            _setupInfo.value = "Checking your configured OTA serverâ€¦"
            try {
                val result = setup.fetch(cfg.otaUrl, cfg.deviceId, clientId)
                if (config.value != cfg || !foreground) return@launch
                if (!result.token.isNullOrBlank() && !saveConfig(cfg.copy(token = result.token))) return@launch
                _setupInfo.value = buildString {
                    if (!result.activationCode.isNullOrBlank()) append("Register this device in your self-hosted dashboard using code ${result.activationCode.take(64)}. Then reconnect. ")
                    else append("Server setup received. ")
                    if (!result.token.isNullOrBlank()) append("Token saved securely. ")
                    append("Your configured secure WebSocket address is retained.")
                }
            } catch (_: CancellationException) { _setupInfo.value = "Server setup was cancelled." }
            catch (_: Exception) { _setupInfo.value = "Could not read server setup. Check your OTA address or enter your server token manually." }
            finally { _setupBusy.value = false }
        }
    }
    fun stopReply() { replyAudio.stop(); ignoreTts = true; socket.sendAbort(); audio.stopPlayback(); if (owner == null) _deviceState.value = DeviceState.IDLE }
    fun setConversationMode(mode: ConversationMode) {
        if (mode == config.value.conversationMode) return
        if (!prefs.edit().putString("conversation_mode", mode.name).commit()) {
            _notice.value = "Could not save conversation mode. Try again."
            return
        }
        endPtt()
        replyAudio.select(mode)
        audio.stopPlayback()
        _deviceState.value = DeviceState.IDLE
        _config.value = config.value.copy(conversationMode = mode)
    }
    fun beginPtt(source: String = "touch") {
        if (config.value.conversationMode != ConversationMode.SPEAK || !foreground || !focused || settingsOpen || owner != null || connectionState.value !is ConnectionState.Connected) return
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _notice.value = "Allow microphone access, then press and hold again."; return
        }
        pendingTypedEchoes.clear()
        stopReply()
        moodReset?.cancel(); serverMood = false; _mood.value = CompanionMood.CURIOUS
        _notice.value = ""
        if (!socket.startListening()) return
        owner = source
        if (audio.startRecording()) _deviceState.value = DeviceState.LISTENING
        else { owner = null; socket.stopListening(); _deviceState.value = DeviceState.IDLE }
    }
    fun endPtt(source: String? = null) {
        if (owner == null || (source != null && source != owner)) return
        owner = null
        audio.stopRecording()
        socket.stopListening()
        _deviceState.value = DeviceState.IDLE
        _mood.value = CompanionMood.THINKING
        moodReset?.cancel()
        moodReset = viewModelScope.launch {
            delay(12000)
            if (_mood.value == CompanionMood.THINKING) _mood.value = CompanionMood.HAPPY
        }
        // Abort suppresses stale audio until a fresh server TTS start.
    }
    private fun stopInteraction() { replyAudio.stop(); pendingTypedEchoes.clear(); endPtt(); moodReset?.cancel(); serverMood = false; _mood.value = CompanionMood.HAPPY; ignoreTts = true; audio.stopPlayback(); _deviceState.value = DeviceState.IDLE }
    fun handleHardwareKey(event: KeyEvent): Boolean {
        val key = config.value.volumePtt
        if (key !in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN) ||
            event.keyCode != key || config.value.conversationMode != ConversationMode.SPEAK || !foreground || !focused || settingsOpen) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) beginPtt("volume")
        if (event.action == KeyEvent.ACTION_UP || event.isCanceled) endPtt("volume")
        return true
    }
    fun saveConfig(value: XiaozhiConfig): Boolean {
        val cfg = value.copy(conversationMode = config.value.conversationMode, serverUrl = value.serverUrl.trim(), otaUrl = value.otaUrl.trim(),
            token = value.token.trim().removePrefix("Bearer "), deviceId = value.deviceId.trim())
        fun validUrl(url: String, scheme: String) = runCatching { URI(url) }.getOrNull()?.let {
            it.scheme == scheme && !it.host.isNullOrBlank() && it.rawUserInfo == null && it.fragment == null
        } == true
        if (!validUrl(cfg.serverUrl, "wss") || !validUrl(cfg.otaUrl, "https") ||
            cfg.deviceId.isBlank() || cfg.deviceId.length > 128 ||
            (cfg.deviceId + cfg.token).any { it.code !in 32..126 }) {
            _notice.value = "Use wss:// and https:// addresses, and a plain ASCII device ID and token."; return false
        }
        return try {
            tokens.write(cfg.token)
            check(prefs.edit().putString("server_url", cfg.serverUrl).putString("ota_url", cfg.otaUrl)
                .putString("device_id", cfg.deviceId).putBoolean("auto_connect", cfg.autoConnect)
                .putInt("volume_ptt", cfg.volumePtt).putBoolean("animate_avatar", cfg.animateAvatar)
                .putBoolean("depth_graphics", cfg.depthGraphics).commit())
            val reconnect = cfg.serverUrl != config.value.serverUrl ||
                cfg.otaUrl != config.value.otaUrl || cfg.token != config.value.token ||
                cfg.deviceId != config.value.deviceId
            if (reconnect) { stopInteraction(); socket.disconnect() }
            _config.value = cfg
            _notice.value = ""
            if (reconnect) { wantsConnection = true; connect() }
            true
        } catch (_: Exception) { _notice.value = "Settings could not be saved securely. Try again."; false }
    }
    private fun addMessage(message: Message) { _messages.value = (_messages.value + message).takeLast(100) }
    override fun onCleared() { foreground = false; stopInteraction(); audio.release(); socket.release(); setup.release(); super.onCleared() }
}
