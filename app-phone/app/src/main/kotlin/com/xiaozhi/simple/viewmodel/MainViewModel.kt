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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import java.net.URI
import java.security.SecureRandom
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("fold5_config", Context.MODE_PRIVATE)
    private val tokens = TokenStore(prefs)
    private val momoTokens = TokenStore(prefs, "momo")
    private val momoPairing = MomoPairingService()
    private val momoChat = MomoChatService()
    private val momoConversation = MomoConversationService()
    private val momoVoice = MomoVoiceService(application)
    private var momoVoiceJob: Job? = null
    private var momoVoiceCapture = false
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
    private val speech = DeviceSpeechService(application)
    private val localRecording = MutableStateFlow(false)
    private var localCapture = false
    private var finalizingRecognition = false
    private var suppressReplayText = false
    private var localReplyTurn = ""
    private fun speechLocale() = if (config.value.speechLanguage == "taglish") "fil-PH" else config.value.speechLanguage
    private fun localOutput() = config.value.localTts && speech.canSpeak(speechLocale(), config.value.voiceName)
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
        localStt = prefs.getBoolean("local_stt", true),
        localTts = prefs.getBoolean("local_tts", true),
        speechLanguage = prefs.getString("speech_language", "en-US") ?: "en-US",
        voiceName = prefs.getString("voice_name", "") ?: "",
        speechRate = prefs.getFloat("speech_rate", 1f),
        speechPitch = prefs.getFloat("speech_pitch", 1f)
    ))
    val config: StateFlow<XiaozhiConfig> = _config
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _deviceState = MutableStateFlow(DeviceState.IDLE)
    val deviceState: StateFlow<DeviceState> = _deviceState
    val connectionState = socket.connectionState
    val isRecording = combine(audio.isRecording, localRecording) { server, local -> server || local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
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
        momoVoice.onLimit = { endPtt() }
        socket.onDeviceReply = { text, turn -> viewModelScope.launch {
            if (!_momoMode.value && foreground && !ignoreTts) {
                localReplyTurn = turn
                textMood(text, reply = true)
                addMessage(Message(type = MessageType.AI, content = text))
                _deviceState.value = DeviceState.SPEAKING
                val cfg = config.value
                val fallback = {
                    if (foreground && !ignoreTts && localReplyTurn == turn) {
                        suppressReplayText = true
                        localReplyTurn = ""
                        if (!socket.requestServerTts(turn)) {
                            suppressReplayText = false
                            _notice.value = "Local speech failed and server speech is unavailable. The reply is shown above."
                            _deviceState.value = DeviceState.IDLE
                        }
                    }
                }
                if (!speech.speak(text, speechLocale(), cfg.voiceName, cfg.speechRate, cfg.speechPitch,
                    done = { if (localReplyTurn == turn) { localReplyTurn = ""; _deviceState.value = DeviceState.IDLE } },
                    failed = fallback)) fallback()
            }
        } }
        socket.onEmotion = { raw -> viewModelScope.launch {
            if (!_momoMode.value && foreground && owner == null) CompanionMood.fromServer(raw)?.let {
                serverMood = true; _mood.value = it
            }
        } }
        socket.onSttMessage = { text -> viewModelScope.launch {
            if (!_momoMode.value && foreground) { textMood(text); addMessage(Message(type = MessageType.USER, content = text)) }
        } }
        socket.onTextMessage = { text -> viewModelScope.launch { if (!_momoMode.value && !ignoreTts && !suppressReplayText) { textMood(text, reply = true); addMessage(Message(type = MessageType.AI, content = text)) } } }
        socket.onTtsStateChanged = { state -> viewModelScope.launch {
            if (_momoMode.value || !foreground || owner != null) return@launch
            when (state) {
                "start" -> { ignoreTts = false; _deviceState.value = DeviceState.SPEAKING; audio.startPlayback() }
                "stop" -> if (!ignoreTts && localReplyTurn.isBlank()) audio.finishPlayback()
            }
        } }
        socket.onAudioData = { data -> viewModelScope.launch {
            if (!_momoMode.value && foreground && owner == null && !ignoreTts) {
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
            if (!_momoMode.value) {
                if (state !is ConnectionState.Connected) stopInteraction() else _notice.value = ""
            }
        } }
        viewModelScope.launch { isRecording.collect { recording ->
            if (!recording && owner != null && !localCapture) endPtt()
        } }
    }

    fun foreground(active: Boolean) {
        foreground = active
        if (active) { if (wantsConnection) connect() }
        else { focused = false; setupJob?.cancel(); stopInteraction(); socket.disconnect() }
    }
    fun focus(hasFocus: Boolean) { focused = hasFocus; if (!hasFocus) stopInteraction() }
    fun settings(open: Boolean) { settingsOpen = open; if (open) stopInteraction() }
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
    fun sendMomoMessage(raw: String, spoken: Boolean = false) {
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
                val reply = try { momoConversation.chat(token, message, config.value.speechLanguage) }
                    catch (_: MomoConversationService.Unsupported) { momoChat.chat(token, message) }
                _momoMessages.value = (_momoMessages.value + Message(type = MessageType.AI, content = reply)).takeLast(100)
                textMood(reply, reply = true)
                if (spoken && foreground && focused && !settingsOpen && _momoMode.value) speakMomoReply(token, reply)
            } catch (_: TimeoutCancellationException) {
                _momoChatError.value = "Momo took too long to reply. The message was not retried automatically."
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
    private fun speakMomoReply(token: String, reply: String) {
        momoVoiceJob?.cancel()
        speech.stopSpeaking()
        _deviceState.value = DeviceState.SPEAKING
        val fallback = {
            momoVoiceJob = viewModelScope.launch {
                try { momoVoice.speak(token, reply) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _momoChatError.value = e.message ?: "Momo server voice unavailable; read the reply above." }
                finally { _deviceState.value = DeviceState.IDLE }
            }
        }
        val cfg = config.value
        if (!cfg.localTts || !speech.speak(reply, speechLocale(), cfg.voiceName, cfg.speechRate, cfg.speechPitch,
            done = { _deviceState.value = DeviceState.IDLE }, failed = fallback)) fallback()
    }
    private fun beginMomoPtt(source: String) {
        if (!foreground || !focused || settingsOpen || owner != null || finalizingRecognition ||
            _momoChatBusy.value || !_momoReady.value) return
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _momoChatError.value = "Allow microphone access, then hold to talk."; return
        }
        momoVoiceJob?.cancel(); speech.stopSpeaking(); speech.cancelRecognition()
        owner = source; momoVoiceCapture = true; localRecording.value = true
        _deviceState.value = DeviceState.LISTENING
        if (config.value.localStt && speech.canRecognize(speechLocale())) {
            localCapture = true
            if (speech.recognize(speechLocale(), result = { text ->
                owner = null; localCapture = false; momoVoiceCapture = false
                localRecording.value = false; finalizingRecognition = false
                _deviceState.value = DeviceState.IDLE
                if (foreground && focused && !settingsOpen) sendMomoMessage(text, spoken = true)
            }, failure = {
                localCapture = false; finalizingRecognition = false
                if (owner != null && foreground && focused && !settingsOpen && momoVoice.startRecording()) {
                    _momoChatError.value = "Local recognition failed. Server microphone is active; please repeat."
                } else {
                    owner = null; momoVoiceCapture = false; localRecording.value = false
                    _deviceState.value = DeviceState.IDLE
                    _momoChatError.value = "Local recognition unavailable. Hold again to use server recognition."
                }
            })) return
            localCapture = false
        }
        if (!momoVoice.startRecording()) {
            owner = null; momoVoiceCapture = false; localRecording.value = false
            _deviceState.value = DeviceState.IDLE; _momoChatError.value = "Microphone could not start. Try again."
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
        _momoPairingInfo.value = "Device bound to Momo AI Server. Open Momo chat to send a message. Hybrid Momo voice is ready. Server fallback requires STT/TTS enabled for your assigned agent."
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

    fun stopReply() { momoVoiceJob?.cancel(); momoVoice.cancelRecording(); momoVoiceCapture = false; speech.cancelRecognition(); localRecording.value = false; localCapture = false; finalizingRecognition = false; speech.stopSpeaking(); localReplyTurn = ""; suppressReplayText = false; ignoreTts = true; socket.sendAbort(); audio.stopPlayback(); if (owner == null) _deviceState.value = DeviceState.IDLE }
    fun beginPtt(source: String = "touch") {
        if (_momoMode.value) { beginMomoPtt(source); return }
        if (_momoMode.value || !foreground || !focused || settingsOpen || owner != null || finalizingRecognition || connectionState.value !is ConnectionState.Connected) return
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _notice.value = "Allow microphone access, then press and hold again."; return
        }
        stopReply()
        moodReset?.cancel(); serverMood = false; _mood.value = CompanionMood.CURIOUS
        _notice.value = ""
        ignoreTts = false
        owner = source
        if (config.value.localStt && socket.hybridSupported && speech.canRecognize(speechLocale())) {
            localCapture = true
            localRecording.value = true
            _deviceState.value = DeviceState.LISTENING
            if (speech.recognize(speechLocale(), result = { text ->
                if (foreground && !settingsOpen) {
                    owner = null; localCapture = false; localRecording.value = false; finalizingRecognition = false
                    _deviceState.value = DeviceState.IDLE
                    _mood.value = CompanionMood.THINKING
                    if (!socket.sendText(text, localOutput())) _notice.value = "Transcript could not be sent. Try again."
                }
            }, failure = {
                localCapture = false; localRecording.value = false; finalizingRecognition = false
                if (owner != null && foreground && focused && !settingsOpen) {
                    _notice.value = "Local recognition unavailable; server microphone is active. Please repeat."
                    startServerCapture()
                } else {
                    _notice.value = "Local recognition failed. Hold to talk again; server recognition will be used."
                    _deviceState.value = DeviceState.IDLE
                }
            })) return
            localCapture = false; localRecording.value = false
        }
        startServerCapture()
    }
    private fun startServerCapture() {
        if (!socket.startListening(socket.hybridSupported && localOutput())) { owner = null; return }
        if (audio.startRecording()) _deviceState.value = DeviceState.LISTENING
        else { owner = null; socket.stopListening(); _deviceState.value = DeviceState.IDLE }
    }
    fun endPtt(source: String? = null) {
        if (owner == null || (source != null && source != owner)) return
        owner = null
        if (momoVoiceCapture && !localCapture) {
            momoVoiceCapture = false; localRecording.value = false
            val file = momoVoice.finishRecording()
            if (file == null) { _deviceState.value = DeviceState.IDLE; return }
            _deviceState.value = DeviceState.IDLE
            momoVoiceJob = viewModelScope.launch {
                finalizingRecognition = true
                try {
                    val text = momoVoice.transcribe(momoTokens.read(), file, config.value.speechLanguage)
                    if (foreground && focused && !settingsOpen) sendMomoMessage(text, spoken = true)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _momoChatError.value = e.message ?: "Momo server recognition failed." }
                finally { file.delete(); finalizingRecognition = false }
            }
            return
        }
        if (localCapture) {
            localRecording.value = false
            finalizingRecognition = true
            speech.finishRecognition()
            _deviceState.value = DeviceState.IDLE
            _mood.value = CompanionMood.THINKING
            return
        }
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
    private fun stopInteraction() { owner = null; momoVoiceJob?.cancel(); momoVoice.cancelRecording(); momoVoiceCapture = false; speech.cancelRecognition(); localCapture = false; localRecording.value = false; finalizingRecognition = false; speech.stopSpeaking(); localReplyTurn = ""; suppressReplayText = false; audio.stopRecording(); moodReset?.cancel(); serverMood = false; _mood.value = CompanionMood.HAPPY; ignoreTts = true; audio.stopPlayback(); _deviceState.value = DeviceState.IDLE }
    fun handleHardwareKey(event: KeyEvent): Boolean {
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
            cfg.speechLanguage !in listOf("en-US", "fil-PH", "taglish") ||
            !cfg.speechRate.isFinite() || cfg.speechRate !in 0.5f..2f ||
            !cfg.speechPitch.isFinite() || cfg.speechPitch !in 0.5f..2f || cfg.voiceName.length > 200 ||
            cfg.deviceId.isBlank() || cfg.deviceId.length > 128 ||
            (cfg.deviceId + cfg.token).any { it.code !in 32..126 }) {
            _notice.value = "Use wss:// and https:// addresses, and a plain ASCII device ID and token."; return false
        }
        return try {
            tokens.write(cfg.token)
            check(prefs.edit().putString("server_url", cfg.serverUrl).putString("ota_url", cfg.otaUrl)
                .putString("device_id", cfg.deviceId).putBoolean("auto_connect", cfg.autoConnect)
                .putInt("volume_ptt", cfg.volumePtt).putBoolean("animate_avatar", cfg.animateAvatar)
                .putBoolean("local_stt", cfg.localStt).putBoolean("local_tts", cfg.localTts)
                .putString("speech_language", cfg.speechLanguage).putString("voice_name", cfg.voiceName)
                .putFloat("speech_rate", cfg.speechRate).putFloat("speech_pitch", cfg.speechPitch).commit())
            stopInteraction(); socket.disconnect()
            _config.value = cfg
            speech.resetCapabilities()
            _notice.value = ""
            wantsConnection = true
            connect()
            true
        } catch (_: Exception) { _notice.value = "Settings could not be saved securely. Try again."; false }
    }
    private fun addMessage(message: Message) { _messages.value = (_messages.value + message).takeLast(100) }
    override fun onCleared() { foreground = false; stopInteraction(); momoChatJob?.cancel(); momoChat.release(); momoPairingJob?.cancel(); momoPairing.release(); momoVoice.release(); momoConversation.release(); speech.release(); audio.release(); socket.release(); setup.release(); super.onCleared() }
}
