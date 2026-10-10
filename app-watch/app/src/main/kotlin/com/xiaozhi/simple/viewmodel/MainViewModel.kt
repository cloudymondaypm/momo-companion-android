package com.xiaozhi.simple.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Application
import android.content.Context
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhi.simple.model.ConnectionState
import com.xiaozhi.simple.model.AvatarMood
import com.xiaozhi.simple.model.AvatarMoodResolver
import com.xiaozhi.simple.model.DeviceState
import com.xiaozhi.simple.model.Message
import com.xiaozhi.simple.model.MessageType
import com.xiaozhi.simple.model.XiaozhiConfig
import com.xiaozhi.simple.service.AudioService
import com.xiaozhi.simple.service.DeviceFingerprint
import com.xiaozhi.simple.service.OTAService
import com.xiaozhi.simple.service.WebSocketService
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Self-hosted-first ViewModel for the Kiumo ZH23 watch build.
 *
 * Differences from the upstream phone client:
 * - No mandatory xiaozhi.me OTA/activation request.
 * - Uses the configured self-hosted WebSocket URL directly.
 * - Supports a learned physical Android key for hold-to-talk/release-to-send.
 * - Keeps the microphone closed except while PTT is held.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val webSocketService = WebSocketService()
    private val otaService = OTAService()
    private var connectJob: Job? = null
    private val audioService = AudioService(application)
    private val speech = com.xiaozhi.simple.service.DeviceSpeechService(application)
    private var localReplyTurn = ""
    private var replayText = false
    private var foreground = true
    private fun speechLocale() = if (config.value.speechLanguage == "taglish") "fil-PH" else config.value.speechLanguage
    private val deviceFingerprint = DeviceFingerprint.getInstance(application)

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _deviceState = MutableStateFlow(DeviceState.IDLE)
    val deviceState: StateFlow<DeviceState> = _deviceState
    private val _avatarMood = MutableStateFlow(AvatarMood.HAPPY)
    val avatarMood: StateFlow<AvatarMood> = _avatarMood
    private val _awaitingReply = MutableStateFlow(false)
    val awaitingReply: StateFlow<Boolean> = _awaitingReply
    private var replyTimeout: Job? = null
    private var moodTimeout: Job? = null
    private var userMood = AvatarMood.HAPPY
    private var serverMood: AvatarMood? = null

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<XiaozhiConfig> = _config

    val connectionState: StateFlow<ConnectionState> = webSocketService.connectionState
    val isRecording: StateFlow<Boolean> = audioService.isRecording
    private val _connectionMessage = MutableStateFlow("")
    val connectionMessage: StateFlow<String> = _connectionMessage
    private val _bindingCode = MutableStateFlow("")
    val bindingCode: StateFlow<String> = _bindingCode

    private val _pttKeyCode = MutableStateFlow(
        prefs.getInt(KEY_PTT_KEYCODE, KeyEvent.KEYCODE_UNKNOWN)
    )
    val pttKeyCode: StateFlow<Int> = _pttKeyCode

    private val _keyLearning = MutableStateFlow(false)
    val keyLearning: StateFlow<Boolean> = _keyLearning

    private val _lastHardwareKey = MutableStateFlow(
        prefs.getString(KEY_LAST_HW_KEY, "") ?: ""
    )
    val lastHardwareKey: StateFlow<String> = _lastHardwareKey

    private var learnedKeyAwaitingRelease: Int? = null

    init {
        setupWebSocketCallbacks()
        setupAudioCallbacks()
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state !is ConnectionState.Connected) onPressEnd()
                when (state) {
                    is ConnectionState.Error -> _connectionMessage.value = state.message
                    is ConnectionState.Connected -> {
                        _connectionMessage.value = "Connected to your server"
                        _bindingCode.value = ""
                    }
                    else -> Unit
                }
            }
        }
        deviceFingerprint.ensureDeviceIdentity()

        if (_config.value.autoConnect) {
            viewModelScope.launch {
                delay(250)
                connect()
            }
        }
    }

    private fun loadConfig(): XiaozhiConfig {
        return XiaozhiConfig(
            serverUrl = prefs.getString(
                "server_url",
                "wss://xiaozhi.spacecloud.space/xiaozhi/v1/"
            ) ?: "wss://xiaozhi.spacecloud.space/xiaozhi/v1/",
            token = prefs.getString("token", "") ?: "",
            deviceId = prefs.getString("device_id", android.os.Build.MODEL)
                ?: android.os.Build.MODEL,
            autoConnect = prefs.getBoolean("auto_connect", true),
            otaUrl = prefs.getString("ota_url", OTAService.OTA_URL) ?: OTAService.OTA_URL,
            automaticToken = prefs.getBoolean("automatic_token", true),
            reduceMotion = prefs.getBoolean("reduce_motion", false),
            localTts = prefs.getBoolean("local_tts", false),
            speechLanguage = prefs.getString("speech_language", "en-US") ?: "en-US"
        )
    }

    fun saveConfig(config: XiaozhiConfig) {
        prefs.edit().apply {
            putString("server_url", config.serverUrl.trim())
            putString("token", config.token.trim())
            putString("device_id", config.deviceId)
            putBoolean("auto_connect", config.autoConnect)
            putString("ota_url", config.otaUrl.trim())
            putBoolean("automatic_token", config.automaticToken)
            putBoolean("reduce_motion", config.reduceMotion)
            putBoolean("local_tts", config.localTts)
            putString("speech_language", config.speechLanguage)
            apply()
        }
        _config.value = config.copy(
            serverUrl = config.serverUrl.trim(),
            token = config.token.trim().removePrefix("Bearer "),
            otaUrl = config.otaUrl.trim()
        )
    }

    fun saveConfigAndReconnect(config: XiaozhiConfig) {
        saveConfig(config)
        disconnect()
        viewModelScope.launch {
            delay(350)
            connect()
        }
    }

    private fun setupWebSocketCallbacks() {
        webSocketService.onDeviceReply = { text, turn -> viewModelScope.launch {
            if (!foreground) return@launch
            localReplyTurn = turn
            _awaitingReply.value = false
            addMessage(Message(type = MessageType.AI, content = text))
            _deviceState.value = DeviceState.SPEAKING
            val fallback = {
                if (localReplyTurn == turn) {
                    replayText = true
                    localReplyTurn = ""
                    if (!webSocketService.requestServerTts(turn)) _deviceState.value = DeviceState.IDLE
                }
            }
            if (!speech.speak(text, speechLocale(), "", 1f, 1f,
                done = { if (localReplyTurn == turn) { localReplyTurn = ""; _deviceState.value = DeviceState.IDLE } },
                failed = fallback)) fallback()
        } }

        webSocketService.onEmotion = { emotion ->
            viewModelScope.launch {
                AvatarMoodResolver.fromServer(emotion)?.let { mood ->
                    serverMood = mood
                    if (userMood != AvatarMood.CARING || mood == AvatarMood.CARING || mood == AvatarMood.CALM)
                        setMood(mood)
                }
            }
        }
        webSocketService.onActivationRequired = { _, code ->
            viewModelScope.launch {
                _bindingCode.value = code
                webSocketService.reportError("Bind code $code in your Xiaozhi console, then reconnect")
            }
        }
        webSocketService.onTextMessage = { text ->
            viewModelScope.launch {
                _awaitingReply.value = false
                replyTimeout?.cancel()
                val mood = serverMood ?: AvatarMoodResolver.fromText(text)
                setMood(if (userMood == AvatarMood.CARING && mood != AvatarMood.CALM) AvatarMood.CARING else mood)
                if (!replayText) addMessage(Message(type = MessageType.AI, content = text))
            }
        }

        webSocketService.onSttMessage = { text ->
            viewModelScope.launch {
                userMood = AvatarMoodResolver.fromText(text)
                serverMood = null
                setMood(userMood)
                addMessage(Message(type = MessageType.USER, content = text))
            }
        }

        webSocketService.onAudioData = { audioData ->
            _awaitingReply.value = false
            if (_deviceState.value != DeviceState.LISTENING) {
                _deviceState.value = DeviceState.SPEAKING
                audioService.playAudio(audioData)
            }
        }

        webSocketService.onTtsStateChanged = { state ->
            when (state) {
                "start" -> {
                    // The server sends start before generating audio; keep the thinking face until audio arrives.
                }
                "end" -> {
                    _awaitingReply.value = false
                    if (_deviceState.value == DeviceState.SPEAKING && localReplyTurn.isBlank()) {
                        _deviceState.value = DeviceState.IDLE
                    }
                }
            }
        }
    }

    private fun setupAudioCallbacks() {
        audioService.onAudioData = { audioData ->
            webSocketService.sendAudio(audioData)
        }
    }

    fun connect() {
        val cfg = _config.value
        if (cfg.serverUrl.isBlank()) return
        disconnect()
        connectJob = viewModelScope.launch {
            webSocketService.beginSetup()
            _bindingCode.value = ""
            val deviceId = deviceFingerprint.ensureDeviceIdentity().first
            val clientId = deviceFingerprint.getClientId()
            try {
                var token = cfg.token.removePrefix("Bearer ")
                if (cfg.automaticToken && token.isBlank()) {
                    _connectionMessage.value = "Getting device token from your server"
                    val setup = otaService.fetchConfig(deviceId, clientId, cfg.otaUrl)
                    if (setup.bindingCode != null) {
                        _bindingCode.value = setup.bindingCode
                        webSocketService.reportError("Bind code ${setup.bindingCode} in your Xiaozhi console, then reconnect")
                        return@launch
                    }
                    token = setup.token
                }
                _connectionMessage.value = "Connecting to your WebSocket"
                webSocketService.requestHybrid = cfg.localTts
                webSocketService.connect(cfg.serverUrl, deviceId, clientId, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                webSocketService.reportError(e.message ?: "Connection setup failed")
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        webSocketService.disconnect()
        audioService.stopRecording()
        audioService.stopPlayback()
        speech.stopSpeaking(); localReplyTurn = ""; replayText = false
        _deviceState.value = DeviceState.IDLE
        _awaitingReply.value = false
        replyTimeout?.cancel()
    }

    fun foreground(active: Boolean) {
        foreground = active
        if (!active) { onPressEnd(); speech.stopSpeaking(); localReplyTurn = "" }
    }
    fun onPressStart() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (connectionState.value !is ConnectionState.Connected) {
            return
        }

        if (_deviceState.value == DeviceState.LISTENING) return
        serverMood = null
        _awaitingReply.value = false
        replyTimeout?.cancel()

        if (_deviceState.value == DeviceState.SPEAKING) {
            webSocketService.sendAbort()
            audioService.stopPlayback()
            _deviceState.value = DeviceState.IDLE
        }

        speech.stopSpeaking(); localReplyTurn = ""; replayText = false
        _deviceState.value = DeviceState.LISTENING
        webSocketService.startListening("manual", config.value.localTts && speech.canSpeak(speechLocale(), ""))
        if (!audioService.startRecording()) onPressEnd()
    }

    fun onPressEnd() {
        if (_deviceState.value != DeviceState.LISTENING) return
        val wasRecording = audioService.isRecording.value
        audioService.stopRecording()
        webSocketService.stopListening()
        _deviceState.value = DeviceState.IDLE
        if (wasRecording && connectionState.value is ConnectionState.Connected) {
            _awaitingReply.value = true
            replyTimeout?.cancel()
            replyTimeout = viewModelScope.launch { delay(20000); _awaitingReply.value = false }
        }
    }

    private fun setMood(mood: AvatarMood) {
        _avatarMood.value = mood
        moodTimeout?.cancel()
        moodTimeout = viewModelScope.launch {
            delay(30000)
            while (_deviceState.value != DeviceState.IDLE || _awaitingReply.value) delay(1000)
            _avatarMood.value = AvatarMood.HAPPY
            userMood = AvatarMood.HAPPY
            serverMood = null
        }
    }

    fun startButtonLearning() {
        onPressEnd()
        learnedKeyAwaitingRelease = null
        _keyLearning.value = true
        _lastHardwareKey.value = "Press the physical button now"
    }

    fun cancelButtonLearning() {
        _keyLearning.value = false
    }

    fun clearPttButton() {
        learnedKeyAwaitingRelease = null
        _keyLearning.value = false
        _pttKeyCode.value = KeyEvent.KEYCODE_UNKNOWN
        prefs.edit().remove(KEY_PTT_KEYCODE).apply()
    }

    /**
     * Returns true when the Activity should consume the hardware event.
     */
    fun handleHardwareKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val keyName = KeyEvent.keyCodeToString(keyCode)

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val description = "$keyName ($keyCode)"
            _lastHardwareKey.value = description
            prefs.edit().putString(KEY_LAST_HW_KEY, description).apply()
        }

        if (_keyLearning.value) {
            // Don't let the app trap the normal Android navigation keys.
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_MENU
            ) {
                return false
            }

            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                _pttKeyCode.value = keyCode
                prefs.edit().putInt(KEY_PTT_KEYCODE, keyCode).apply()
                _keyLearning.value = false
                learnedKeyAwaitingRelease = keyCode
                return true
            }
            return false
        }

        // Swallow the release belonging to the key-learning press.
        if (event.action == KeyEvent.ACTION_UP && learnedKeyAwaitingRelease == keyCode) {
            learnedKeyAwaitingRelease = null
            return true
        }

        val mapped = _pttKeyCode.value
        if (mapped == KeyEvent.KEYCODE_UNKNOWN || keyCode != mapped) {
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) onPressStart()
            }
            KeyEvent.ACTION_UP -> onPressEnd()
        }
        return true
    }

    fun getDeviceId(): String = deviceFingerprint.getSerialNumber()
    fun getClientId(): String = deviceFingerprint.getClientId()

    private fun addMessage(message: Message) {
        _messages.value = (_messages.value + message).takeLast(20)
    }

    override fun onCleared() {
        super.onCleared()
        connectJob?.cancel()
        webSocketService.disconnect()
        speech.release()
        audioService.release()
    }

    companion object {
        private const val PREFS_NAME = "xiaozhi_config"
        private const val KEY_PTT_KEYCODE = "ptt_key_code"
        private const val KEY_LAST_HW_KEY = "last_hardware_key"
    }
}
