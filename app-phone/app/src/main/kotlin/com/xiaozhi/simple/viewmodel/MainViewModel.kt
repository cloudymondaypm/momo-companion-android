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
import java.security.SecureRandom
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("fold5_config", Context.MODE_PRIVATE)
    private val defaultDeviceId = prefs.getString("device_id", null) ?: newDeviceId().also {
        prefs.edit().putString("device_id", it).apply()
    }
    private var credentialMigrationFailed = false
    private val credentials = VoiceCredentialStore(prefs).also {
        credentialMigrationFailed = runCatching {
            it.migrate(prefs.getString("server_url", null) ?: XiaozhiConfig().serverUrl, defaultDeviceId)
        }.isFailure
    }
    private val momoPairing = MomoPairingService()
    private val momoChat = MomoChatService()
    private val momoConversation = MomoConversationService()
    private val momoAccess = MomoDeviceAccess({ credentials.readMomo() }, momoChat, momoConversation)
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
    val momoReady: StateFlow<Boolean> = momoAccess.paired
    private val _momoVoiceError = MutableStateFlow("")
    val momoVoiceError: StateFlow<String> = _momoVoiceError
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
    private val _config = MutableStateFlow(XiaozhiConfig(
        serverUrl = prefs.getString("server_url", null) ?: XiaozhiConfig().serverUrl,
        otaUrl = prefs.getString("ota_url", null) ?: XiaozhiConfig().otaUrl,
        token = credentials.readXiaozhi(prefs.getString("server_url", null) ?: XiaozhiConfig().serverUrl, defaultDeviceId), deviceId = defaultDeviceId,
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
    private fun selectionIdentity() = _momoLinkedDevice.value.ifBlank { _config.value.deviceId }
    private val _voiceBackend = MutableStateFlow(VoiceBackend.fromSaved(prefs.getString(VoiceBackend.selectionKey(selectionIdentity()), null)))
    val voiceBackend: StateFlow<VoiceBackend> = _voiceBackend
    private var connectionCheckJob: Job? = null
    private var interactionGeneration = 0L
    private fun isXiaozhiVoice() = !_momoMode.value && _voiceBackend.value == VoiceBackend.XIAOZHI
    private fun canUseMomoVoice() = _momoMode.value || _voiceBackend.value == VoiceBackend.MOMO
    private fun recordMomoError(error: Exception, voice: Boolean = true, speechOnly: Boolean = false) {
        val message = error.message ?: "Momo request failed. Try again."
        if (voice) {
            _momoVoiceError.value = message
            if (!_momoMode.value) _notice.value = message
            momoAccess.reportVoiceFailure(error, speechOnly)
        } else _momoChatError.value = message
        // A route failure never erases or invalidates the local QR pairing.
        // Each endpoint must still authenticate the stored credential itself.
        momoAccess.credential()
    }

    fun selectVoiceBackend(backend: VoiceBackend): Boolean {
        if (_voiceBackend.value == backend) return true
        return try {
            check(prefs.edit().putString(VoiceBackend.selectionKey(selectionIdentity()), backend.name).commit())
            setupJob?.cancel(); stopInteraction(); socket.disconnect(); momoConversation.disconnect()
            _voiceBackend.value = backend
            _messages.value = emptyList(); _notice.value = ""; _momoVoiceError.value = ""
            wantsConnection = true
            connect()
            true
        } catch (_: Exception) { _notice.value = "Voice server selection could not be saved. Try again."; false }
    }
    fun savedXiaozhiToken(url: String, deviceId: String) = credentials.readXiaozhi(url, deviceId)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _deviceState = MutableStateFlow(DeviceState.IDLE)
    val deviceState: StateFlow<DeviceState> = _deviceState
    val connectionState = combine(_voiceBackend, socket.connectionState, momoConversation.connectionState) { backend, xiaozhi, momo ->
        if (backend == VoiceBackend.MOMO) momo else xiaozhi
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)
    val isRecording = combine(audio.isRecording, localRecording) { server, local -> server || local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _notice = MutableStateFlow(if (credentialMigrationFailed) "Credentials could not be migrated securely. Restart and try again; the previous credentials were retained." else "")
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
            if (isXiaozhiVoice() && foreground && !ignoreTts) {
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
            if (isXiaozhiVoice() && foreground && owner == null) CompanionMood.fromServer(raw)?.let {
                serverMood = true; _mood.value = it
            }
        } }
        socket.onSttMessage = { text -> viewModelScope.launch {
            if (isXiaozhiVoice() && foreground) { textMood(text); addMessage(Message(type = MessageType.USER, content = text)) }
        } }
        socket.onTextMessage = { text -> viewModelScope.launch { if (isXiaozhiVoice() && !ignoreTts && !suppressReplayText) { textMood(text, reply = true); addMessage(Message(type = MessageType.AI, content = text)) } } }
        socket.onTtsStateChanged = { state -> viewModelScope.launch {
            if (!isXiaozhiVoice() || !foreground || owner != null) return@launch
            when (state) {
                "start" -> { ignoreTts = false; _deviceState.value = DeviceState.SPEAKING; audio.startPlayback() }
                "stop" -> if (!ignoreTts && localReplyTurn.isBlank()) audio.finishPlayback()
            }
        } }
        socket.onAudioData = { data -> viewModelScope.launch {
            if (isXiaozhiVoice() && foreground && owner == null && !ignoreTts) {
                _deviceState.value = DeviceState.SPEAKING
                audio.playAudio(data)
            }
        } }
        socket.onError = { text -> viewModelScope.launch { if (isXiaozhiVoice()) { _notice.value = text; stopInteraction() } } }
        audio.onAudioData = { packet -> if (isXiaozhiVoice() && !socket.sendAudio(packet)) viewModelScope.launch {
            _notice.value = "Audio could not be sent. Hold PTT again after reconnecting."
            stopInteraction()
        } }
        audio.onError = { text -> viewModelScope.launch { _notice.value = text; stopInteraction() } }
        audio.onPlaybackFinished = { viewModelScope.launch { if (owner == null) _deviceState.value = DeviceState.IDLE } }
        viewModelScope.launch { socket.connectionState.collect { state ->
            if (isXiaozhiVoice()) {
                if (state !is ConnectionState.Connected) stopInteraction() else _notice.value = ""
            }
        } }
        viewModelScope.launch { isRecording.collect { recording ->
            if (!recording && owner != null && !localCapture && !momoVoiceCapture) endPtt()
        } }
    }

    fun foreground(active: Boolean) {
        foreground = active
        if (active) { if (wantsConnection) connect() }
        else { focused = false; setupJob?.cancel(); stopInteraction(); socket.disconnect(); momoConversation.disconnect() }
    }
    fun focus(hasFocus: Boolean) {
        focused = hasFocus
        if (!hasFocus) stopInteraction()
        else if (foreground && !settingsOpen && wantsConnection) connect()
    }
    fun settings(open: Boolean) {
        settingsOpen = open
        if (open) stopInteraction()
        else if (foreground && focused && wantsConnection) connect()
    }
    fun connect() {
        wantsConnection = true
        if (!foreground || _momoMode.value) return
        if (_voiceBackend.value == VoiceBackend.MOMO) {
            if (connectionCheckJob?.isActive == true || _momoChatBusy.value) return
            val token = momoAccess.credential()
            if (token.isBlank()) {
                momoConversation.markError("Pair this phone with Momo AI Server in Settings first.")
                return
            }
            connectionCheckJob = viewModelScope.launch {
                try { momoAccess.checkVoice(); _notice.value = ""; _momoVoiceError.value = "" }
                catch (_: TimeoutCancellationException) { momoConversation.markError("Momo connection check timed out. Try again.") }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { recordMomoError(e) }
            }
            return
        }
        val cfg = config.value
        if (!VoiceBackend.validXiaozhiEndpoint(cfg.serverUrl, "wss")) {
            _notice.value = "Xiaozhi needs its own wss:// endpoint. Select Momo AI Server for ai.momolegend.fun."; return
        }
        if (cfg.token.isNotBlank() && cfg.token == credentials.readMomo()) {
            _notice.value = "A Momo paired-device credential cannot be used with Xiaozhi. Get Xiaozhi server setup in Settings."; return
        }
        socket.connect(cfg.serverUrl, cfg.deviceId, clientId, cfg.token)
    }
    fun disconnect() { wantsConnection = false; stopInteraction(); socket.disconnect(); momoConversation.disconnect() }
    fun clearMessages() { _messages.value = emptyList() }
    fun clearMomoMessages() { _momoMessages.value = emptyList() }
    fun useMomo(enabled: Boolean) {
        if (_momoMode.value == enabled) return
        stopInteraction(); socket.disconnect(); momoConversation.disconnect()
        momoChatJob?.cancel()
        _momoMode.value = enabled
        prefs.edit().putBoolean("momo_chat_mode", enabled).apply()
        if (!enabled && wantsConnection) connect()
    }
    fun sendMomoMessage(raw: String, spoken: Boolean = false) {
        if ((!_momoMode.value && !(spoken && _voiceBackend.value == VoiceBackend.MOMO)) || _momoChatBusy.value) return
        val message = raw.trim()
        if (message.isBlank() || message.codePointCount(0, message.length) > 8000) {
            _momoChatError.value = "Enter a message of 1–8000 characters."; return
        }
        val token = momoAccess.credential()
        if (token.isBlank()) {
            if (spoken) _momoVoiceError.value = "No saved Momo credential is available. Pair this phone in Settings."
            else _momoChatError.value = "No saved Momo credential is available. Pair this phone in Settings."
            return
        }
        val epoch = interactionGeneration
        momoChatJob = viewModelScope.launch {
            _momoChatBusy.value = true
            if (spoken) _momoVoiceError.value = "" else _momoChatError.value = ""
            _momoMessages.value = (_momoMessages.value + Message(type = MessageType.USER, content = message)).takeLast(100)
            serverMood = false; _mood.value = CompanionMood.THINKING
            try {
                val reply = if (spoken) momoAccess.voice(message, config.value.speechLanguage) else momoAccess.chat(message)
                _momoMessages.value = (_momoMessages.value + Message(type = MessageType.AI, content = reply)).takeLast(100)
                textMood(reply, reply = true)
                if (spoken && epoch == interactionGeneration && foreground && focused && !settingsOpen && canUseMomoVoice()) speakMomoReply(token, reply)
            } catch (_: TimeoutCancellationException) {
                val message = "Momo took too long to reply. The message was not retried automatically."
                if (spoken) _momoVoiceError.value = message else _momoChatError.value = message
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == interactionGeneration) recordMomoError(e, voice = spoken)
            } finally {
                if (epoch == interactionGeneration) {
                    _momoChatBusy.value = false
                    if (_mood.value == CompanionMood.THINKING) _mood.value = CompanionMood.HAPPY
                }
            }
        }
    }
    private fun speakMomoReply(token: String, reply: String) {
        momoVoiceJob?.cancel()
        speech.stopSpeaking()
        _deviceState.value = DeviceState.SPEAKING
        val epoch = interactionGeneration
        val fallback = {
            if (epoch == interactionGeneration && foreground && focused && !settingsOpen && canUseMomoVoice()) momoVoiceJob = viewModelScope.launch {
                try { momoVoice.speak(token, reply) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { recordMomoError(e, speechOnly = true) }
                finally { _deviceState.value = DeviceState.IDLE }
            }
        }
        val cfg = config.value
        if (!cfg.localTts || !speech.speak(reply, speechLocale(), cfg.voiceName, cfg.speechRate, cfg.speechPitch,
            done = { _deviceState.value = DeviceState.IDLE }, failed = fallback)) fallback()
    }
    private fun beginMomoPtt(source: String) {
        val token = momoAccess.credential()
        if (!foreground || !focused || settingsOpen || owner != null || finalizingRecognition ||
            _momoChatBusy.value || token.isBlank() || connectionCheckJob?.isActive == true ||
            (!_momoMode.value && momoConversation.connectionState.value !is ConnectionState.Connected)) return
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _momoVoiceError.value = "Allow microphone access, then hold to talk."; return
        }
        momoVoiceJob?.cancel(); speech.stopSpeaking(); speech.cancelRecognition()
        _momoVoiceError.value = ""; _notice.value = ""
        moodReset?.cancel(); serverMood = false; _mood.value = CompanionMood.CURIOUS
        owner = source; momoVoiceCapture = true; localRecording.value = true
        _deviceState.value = DeviceState.LISTENING
        if (config.value.localStt && speech.canRecognize(speechLocale())) {
            localCapture = true
            if (speech.recognize(speechLocale(), result = { text ->
                owner = null; localCapture = false; momoVoiceCapture = false
                localRecording.value = false; finalizingRecognition = false
                _deviceState.value = DeviceState.IDLE
                if (foreground && focused && !settingsOpen && canUseMomoVoice()) sendMomoMessage(text, spoken = true)
            }, failure = {
                localCapture = false; finalizingRecognition = false
                if (owner != null && foreground && focused && !settingsOpen && momoVoice.startRecording()) {
                    _momoVoiceError.value = "Local recognition failed. Server microphone is active; please repeat."
                } else {
                    owner = null; momoVoiceCapture = false; localRecording.value = false
                    _deviceState.value = DeviceState.IDLE
                    _momoVoiceError.value = "Local recognition unavailable. Hold again to use server recognition."
                }
            })) return
            localCapture = false
        }
        if (!momoVoice.startRecording()) {
            owner = null; momoVoiceCapture = false; localRecording.value = false
            _deviceState.value = DeviceState.IDLE; _momoVoiceError.value = "Microphone could not start. Try again."
        }
    }
    fun getServerSetup() {
        if (_setupBusy.value || _voiceBackend.value != VoiceBackend.XIAOZHI) return
        val cfg = config.value
        if (!VoiceBackend.validXiaozhiEndpoint(cfg.otaUrl, "https")) {
            _setupInfo.value = "Use the separate Xiaozhi HTTPS OTA address. Momo pairing does not use OTA."; return
        }
        endPtt()
        setupJob = viewModelScope.launch {
            _setupBusy.value = true
            _setupInfo.value = "Checking your configured OTA server…"
            try {
                val result = setup.fetch(cfg.otaUrl, cfg.deviceId, clientId)
                if (config.value != cfg || !foreground || _voiceBackend.value != VoiceBackend.XIAOZHI) return@launch
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
     * Momo tokens authorize its text conversation and speech-only device APIs;
     * never overwrite or submit the independent Xiaozhi WebSocket bearer token.
     */
    private fun keepBoundDevice(bound: MomoPairingService.BoundDevice) {
        require(bound.token.isNotBlank() && bound.deviceId.isNotBlank())
        stopInteraction(); socket.disconnect(); momoConversation.disconnect()
        credentials.bindMomo(bound)
        _momoLinkedDevice.value = bound.deviceId
        _voiceBackend.value = VoiceBackend.fromSaved(prefs.getString(VoiceBackend.selectionKey(bound.deviceId), null))
        momoAccess.credential()
        _momoChatError.value = ""; _momoVoiceError.value = ""
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
        if (canUseMomoVoice()) { beginMomoPtt(source); return }
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
                    val text = momoVoice.transcribe(credentials.readMomo(), file, config.value.speechLanguage)
                    if (foreground && focused && !settingsOpen) sendMomoMessage(text, spoken = true)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { recordMomoError(e, speechOnly = true) }
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
    private fun stopInteraction() {
        interactionGeneration++
        connectionCheckJob?.cancel(); momoChatJob?.cancel(); momoVoiceJob?.cancel()
        momoConversation.disconnect(); _momoChatBusy.value = false
        if (isXiaozhiVoice()) { socket.stopListening(); socket.sendAbort() }
        owner = null
        momoVoice.cancelRecording(); momoVoiceCapture = false
        speech.cancelRecognition(); localCapture = false; localRecording.value = false
        finalizingRecognition = false; speech.stopSpeaking()
        localReplyTurn = ""; suppressReplayText = false
        audio.stopRecording(); audio.stopPlayback()
        moodReset?.cancel(); serverMood = false; _mood.value = CompanionMood.HAPPY
        ignoreTts = true; _deviceState.value = DeviceState.IDLE
    }
    fun handleHardwareKey(event: KeyEvent): Boolean {
        val key = config.value.volumePtt
        if (key !in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN) ||
            event.keyCode != key || !foreground || !focused || settingsOpen) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) beginPtt("volume")
        if (event.action == KeyEvent.ACTION_UP || event.isCanceled) endPtt("volume")
        return true
    }
    fun saveConfig(value: XiaozhiConfig): Boolean {
        val selected = if (_voiceBackend.value == VoiceBackend.MOMO) value.copy(serverUrl = config.value.serverUrl,
            otaUrl = config.value.otaUrl, token = config.value.token, deviceId = config.value.deviceId) else value
        val cfg = selected.copy(serverUrl = selected.serverUrl.trim(), otaUrl = selected.otaUrl.trim(),
            token = selected.token.trim().removePrefix("Bearer "), deviceId = selected.deviceId.trim())
        if (_voiceBackend.value == VoiceBackend.XIAOZHI &&
            (!VoiceBackend.validXiaozhiEndpoint(cfg.serverUrl, "wss") || !VoiceBackend.validXiaozhiEndpoint(cfg.otaUrl, "https"))) {
            _notice.value = "Xiaozhi needs its own wss:// and https:// addresses. Select Momo AI Server for ai.momolegend.fun."; return false
        }
        if (
            cfg.speechLanguage !in listOf("en-US", "fil-PH", "taglish") ||
            !cfg.speechRate.isFinite() || cfg.speechRate !in 0.5f..2f ||
            !cfg.speechPitch.isFinite() || cfg.speechPitch !in 0.5f..2f || cfg.voiceName.length > 200 ||
            cfg.deviceId.isBlank() || cfg.deviceId.length > 128 ||
            (cfg.deviceId + cfg.token).any { it.code !in 32..126 }) {
            _notice.value = "Use wss:// and https:// addresses, and a plain ASCII device ID and token."; return false
        }
        return try {
            if (_voiceBackend.value == VoiceBackend.XIAOZHI) {
                if (cfg.token.isNotBlank() && cfg.token == credentials.readMomo()) {
                    _notice.value = "A Momo paired-device credential cannot be used with Xiaozhi."; return false
                }
                if ((cfg.serverUrl != config.value.serverUrl || cfg.deviceId != config.value.deviceId) &&
                    cfg.token.isNotBlank() && cfg.token == config.value.token &&
                    cfg.token != credentials.readXiaozhi(cfg.serverUrl, cfg.deviceId)) {
                    _notice.value = "The Xiaozhi server or device changed. Clear the previous token and get setup for this device."; return false
                }
                credentials.writeXiaozhi(cfg.serverUrl, cfg.deviceId, cfg.token)
            }
            if (_momoLinkedDevice.value.isBlank()) check(prefs.edit()
                .putString(VoiceBackend.selectionKey(cfg.deviceId), _voiceBackend.value.name).commit())
            check(prefs.edit().putString("server_url", cfg.serverUrl).putString("ota_url", cfg.otaUrl)
                .putString("device_id", cfg.deviceId).putBoolean("auto_connect", cfg.autoConnect)
                .putInt("volume_ptt", cfg.volumePtt).putBoolean("animate_avatar", cfg.animateAvatar)
                .putBoolean("local_stt", cfg.localStt).putBoolean("local_tts", cfg.localTts)
                .putString("speech_language", cfg.speechLanguage).putString("voice_name", cfg.voiceName)
                .putFloat("speech_rate", cfg.speechRate).putFloat("speech_pitch", cfg.speechPitch).commit())
            stopInteraction(); socket.disconnect(); momoConversation.disconnect()
            // Hidden Xiaozhi fields are preserved verbatim when saving Momo speech settings.
            _config.value = if (_voiceBackend.value == VoiceBackend.MOMO) cfg.copy(
                serverUrl = config.value.serverUrl, otaUrl = config.value.otaUrl,
                token = config.value.token, deviceId = config.value.deviceId) else cfg
            if (_momoLinkedDevice.value.isBlank()) _voiceBackend.value = VoiceBackend.fromSaved(
                prefs.getString(VoiceBackend.selectionKey(_config.value.deviceId), null))
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
