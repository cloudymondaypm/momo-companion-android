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
    private val momoTokens = TokenStore(prefs, "momo")
    private val momoPairing = MomoPairingService()
    private val momoChat = MomoChatService()
    private var momoChatJob: Job? = null
    private val _momoMode = MutableStateFlow(prefs.getBoolean("momo_chat_mode", true))
    val momoMode: StateFlow<Boolean> = _momoMode
    private val _momoChatBusy = MutableStateFlow(false)
    val momoChatBusy: StateFlow<Boolean> = _momoChatBusy
    private val _momoChatError = MutableStateFlow("")
    val momoChatError: StateFlow<String> = _momoChatError
    private val _momoReady = MutableStateFlow(momoTokens.read().isNotBlank())
    val momoReady: StateFlow<Boolean> = _momoReady
    private val _momoMessages = MutableStateFlow<List<Message>>(emptyList())
    val momoMessages: StateFlow<List<Message>> = _momoMessages
    private var momoPairingJob: Job? = null
    private val _momoCode = MutableStateFlow("")
    val momoCode: StateFlow<String> = _momoCode
    private val _momoPairingInfo = MutableStateFlow("")
    val momoPairingInfo: StateFlow<String> = _momoPairingInfo
    private val _momoPairingBusy = MutableStateFlow(false)
    val momoPairingBusy: StateFlow<Boolean> = _momoPairingBusy
    private val _momoLinkedDevice = MutableStateFlow(prefs.getString("momo_device_id", "") ?: "")
    val momoLinkedDevice: StateFlow<String> = _momoLinkedDevice
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
        animateAvatar = prefs.getBoolean("animate_avatar", true)
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

    init {
        socket.onEmotion = { raw -> viewModelScope.launch {
            if (foreground && owner == null) CompanionMood.fromServer(raw)?.let {
                serverMood = true; _mood.value = it
            }
        } }
        socket.onSttMessage = { text -> viewModelScope.launch {
            if (foreground) { textMood(text); addMessage(Message(type = MessageType.USER, content = text)) }
        } }
        socket.onTextMessage = { text -> viewModelScope.launch { if (!ignoreTts) { textMood(text, reply = true); addMessage(Message(type = MessageType.AI, content = text)) } } }
        socket.onTtsStateChanged = { state -> viewModelScope.launch {
            if (!foreground || owner != null) return@launch
            when (state) {
                "start" -> { ignoreTts = false; _deviceState.value = DeviceState.SPEAKING; audio.startPlayback() }
                "stop" -> if (!ignoreTts) audio.finishPlayback()
            }
        } }
        socket.onAudioData = { data -> viewModelScope.launch {
            if (foreground && owner == null && !ignoreTts) {
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
        if (!foreground || _momoMode.value) return
        val cfg = config.value
        socket.connect(cfg.serverUrl, cfg.deviceId, clientId, cfg.token)
    }
    fun disconnect() { wantsConnection = false; stopInteraction(); socket.disconnect() }
    fun clearMessages() { _messages.value = emptyList() }
    fun clearMomoMessages() { _momoMessages.value = emptyList() }
    fun useMomo(enabled: Boolean) {
        if (_momoMode.value == enabled) return
        stopInteraction(); socket.disconnect()
        momoChatJob?.cancel()
        _momoMode.value = enabled
        prefs.edit().putBoolean("momo_chat_mode", enabled).apply()
        if (!enabled && wantsConnection) connect()
    }
    fun sendMomoMessage(raw: String) {
        if (!_momoMode.value || _momoChatBusy.value) return
        val message = raw.trim()
        if (message.isBlank() || message.codePointCount(0, message.length) > 8000) {
            _momoChatError.value = "Enter a message of 1–8000 characters."; return
        }
        val token = momoTokens.read()
        if (token.isBlank()) {
            _momoReady.value = false
            _momoChatError.value = "Pair this phone in Settings first."; return
        }
        momoChatJob = viewModelScope.launch {
            _momoChatBusy.value = true
            _momoChatError.value = ""
            _momoMessages.value = (_momoMessages.value + Message(type = MessageType.USER, content = message)).takeLast(100)
            serverMood = false; _mood.value = CompanionMood.THINKING
            try {
                val reply = momoChat.chat(token, message)
                _momoMessages.value = (_momoMessages.value + Message(type = MessageType.AI, content = reply)).takeLast(100)
                textMood(reply, reply = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is MomoChatService.ChatException && e.status in listOf(401, 403)) _momoReady.value = false
                _momoChatError.value = e.message ?: "Momo chat failed. Try again."
            } finally {
                _momoChatBusy.value = false
                if (_mood.value == CompanionMood.THINKING) _mood.value = CompanionMood.HAPPY
            }
        }
    }
    fun getServerSetup() {
        if (_setupBusy.value) return
        val cfg = config.value
        endPtt()
        setupJob = viewModelScope.launch {
            _setupBusy.value = true
            _setupInfo.value = "Checking your configured OTA server…"
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
    /**
     * Bind to Momo AI Server independently of the legacy Xiaozhi voice socket.
     * Newly issued Momo tokens authorize its HTTP device-chat endpoint only;
     * never overwrite the existing Xiaozhi WebSocket bearer token.
     */
    private fun keepBoundDevice(bound: MomoPairingService.BoundDevice) {
        require(bound.token.isNotBlank() && bound.deviceId.isNotBlank())
        momoTokens.write(bound.token)
        check(prefs.edit().putString("momo_device_id", bound.deviceId).commit())
        _momoLinkedDevice.value = bound.deviceId
        _momoReady.value = true
        _momoChatError.value = ""
        useMomo(true)
        _momoPairingInfo.value = "Device bound to Momo AI Server. Open Momo chat to send a message. Momo voice is unavailable."
        _momoCode.value = ""
    }

    fun requestMomoVerificationCode() {
        if (_momoPairingBusy.value || _momoChatBusy.value) return
        momoPairingJob?.cancel()
        momoPairingJob = viewModelScope.launch {
            _momoPairingBusy.value = true
            _momoPairingInfo.value = "Requesting a six-digit code from Momo AI Server…"
            _momoCode.value = ""
            try {
                val current = momoPairing.requestCode("Momo Android phone")
                _momoCode.value = current.code
                _momoPairingInfo.value = "Enter this six-digit code in the Momo AI Server dashboard. Expires in five minutes."
                while (isActive && System.currentTimeMillis() / 1000 < current.expiresAt) {
                    delay(2500)
                    val result = momoPairing.poll(current)
                    if (result.bound != null) {
                        keepBoundDevice(result.bound)
                        // Confirm receipt only after the Keystore write succeeds.
                        // If the acknowledgement fails, the saved credential
                        // remains usable and the server expires temporary state.
                        runCatching { momoPairing.acknowledge(current) }
                        return@launch
                    }
                    if (result.status == "expired") break
                }
                _momoPairingInfo.value = "Code expired. Request another verification code."
                _momoCode.value = ""
            } catch (_: CancellationException) {
                _momoCode.value = ""
                throw CancellationException()
            } catch (e: Exception) {
                _momoPairingInfo.value = e.message?.take(160) ?: "Unable to contact Momo AI Server."
                _momoCode.value = ""
            } finally {
                _momoPairingBusy.value = false
            }
        }
    }

    fun claimMomoQr(rawQr: String) {
        if (_momoPairingBusy.value || _momoChatBusy.value) return
        momoPairingJob?.cancel()
        momoPairingJob = viewModelScope.launch {
            _momoPairingBusy.value = true
            _momoCode.value = ""
            _momoPairingInfo.value = "Linking this Android phone to your Momo workspace…"
            try {
                keepBoundDevice(momoPairing.claimQr(rawQr, "Momo Android phone"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _momoPairingInfo.value = e.message?.take(160) ?: "QR binding failed. Please generate a new QR."
            } finally {
                _momoPairingBusy.value = false
            }
        }
    }

    fun cancelMomoPairing() {
        momoPairingJob?.cancel()
        _momoCode.value = ""
        _momoPairingBusy.value = false
        _momoPairingInfo.value = "Pairing cancelled on this phone. Verification codes expire automatically."
    }

    fun stopReply() { ignoreTts = true; socket.sendAbort(); audio.stopPlayback(); if (owner == null) _deviceState.value = DeviceState.IDLE }
    fun beginPtt(source: String = "touch") {
        if (_momoMode.value || !foreground || !focused || settingsOpen || owner != null || connectionState.value !is ConnectionState.Connected) return
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _notice.value = "Allow microphone access, then press and hold again."; return
        }
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
    private fun stopInteraction() { endPtt(); moodReset?.cancel(); serverMood = false; _mood.value = CompanionMood.HAPPY; ignoreTts = true; audio.stopPlayback(); _deviceState.value = DeviceState.IDLE }
    fun handleHardwareKey(event: KeyEvent): Boolean {
        if (_momoMode.value) return false
        val key = config.value.volumePtt
        if (key !in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN) ||
            event.keyCode != key || !foreground || !focused || settingsOpen) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) beginPtt("volume")
        if (event.action == KeyEvent.ACTION_UP || event.isCanceled) endPtt("volume")
        return true
    }
    fun saveConfig(value: XiaozhiConfig): Boolean {
        val cfg = value.copy(serverUrl = value.serverUrl.trim(), otaUrl = value.otaUrl.trim(),
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
                .putInt("volume_ptt", cfg.volumePtt).putBoolean("animate_avatar", cfg.animateAvatar).commit())
            stopInteraction(); socket.disconnect()
            _config.value = cfg
            _notice.value = ""
            wantsConnection = true
            connect()
            true
        } catch (_: Exception) { _notice.value = "Settings could not be saved securely. Try again."; false }
    }
    private fun addMessage(message: Message) { _messages.value = (_messages.value + message).takeLast(100) }
    override fun onCleared() { foreground = false; stopInteraction(); momoChatJob?.cancel(); momoChat.release(); momoPairingJob?.cancel(); momoPairing.release(); audio.release(); socket.release(); setup.release(); super.onCleared() }
}
