package com.xiaozhi.simple.ui.screen

import android.Manifest
import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.xiaozhi.simple.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.xiaozhi.simple.model.*
import com.xiaozhi.simple.viewmodel.MainViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(model: MainViewModel) {
    val config by model.config.collectAsState()
    val voiceBackend by model.voiceBackend.collectAsState()
    val mood by model.mood.collectAsState()
    val connection by model.connectionState.collectAsState()
    val state by model.deviceState.collectAsState()
    val recording by model.isRecording.collectAsState()
    val messages by model.messages.collectAsState()
    val notice by model.notice.collectAsState()
    val setupInfo by model.setupInfo.collectAsState()
    val setupBusy by model.setupBusy.collectAsState()
    val momoCode by model.momoCode.collectAsState()
    val momoPairingInfo by model.momoPairingInfo.collectAsState()
    val momoPairingBusy by model.momoPairingBusy.collectAsState()
    val momoLinkedDevice by model.momoLinkedDevice.collectAsState()
    val momoMode by model.momoMode.collectAsState()
    val momoReady by model.momoReady.collectAsState()
    val momoChatBusy by model.momoChatBusy.collectAsState()
    val momoChatError by model.momoChatError.collectAsState()
    val momoMessages by model.momoMessages.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(momoMessages.lastOrNull()?.id) {
        if (momoMessages.lastOrNull()?.type == MessageType.AI) draft = ""
    }
    val mic = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(showSettings) {
        model.settings(showSettings)
        onDispose { model.endPtt("touch") }
    }
    val label = when {
        recording -> "Listening"
        !momoMode && voiceBackend == VoiceBackend.MOMO && momoChatBusy -> "Momo is thinking…"
        state == DeviceState.SPEAKING -> "Speaking"
        connection is ConnectionState.Connected -> "Ready to talk"
        connection is ConnectionState.Connecting -> "Connecting…"
        connection is ConnectionState.Error -> "Connection needs attention"
        else -> "Disconnected"
    }
    val connected = connection is ConnectionState.Connected
    val accent = when {
        recording -> Color(0xFF37CBA3)
        state == DeviceState.SPEAKING -> Color(0xFF89B4FA)
        connection is ConnectionState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
            val wide = maxWidth >= 560.dp
            val short = maxHeight < 520.dp
            Column(Modifier.fillMaxSize().then(if (!wide && short) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Momo Companion", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (momoMode) "Momo · your little chat buddy" else "Momo · your little voice buddy", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { model.endPtt(); showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Open settings")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = momoMode, onClick = { model.useMomo(true) }, label = { Text("Momo chat") })
                    FilterChip(selected = !momoMode, onClick = { model.useMomo(false) }, label = { Text("Hybrid voice") })
                }
                if (momoMode) {
                    Text("Momo AI Server · ai.momolegend.fun", style = MaterialTheme.typography.titleSmall)
                    Text(if (momoChatBusy) "Momo is thinking…" else if (momoReady) "Paired · text chat available" else "Pair this phone in Settings to chat",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Voice prefers device speech with automatic server fallback. Conversation memory follows your assigned agent settings.",
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {}, enabled = momoReady && !momoChatBusy,
                        modifier = Modifier.fillMaxWidth().pointerInput(momoReady, momoChatBusy, mic.status.isGranted) {
                            detectTapGestures(onPress = {
                                if (momoReady && !momoChatBusy) {
                                    if (mic.status.isGranted) model.beginPtt() else mic.launchPermissionRequest()
                                    try { awaitRelease() } finally { model.endPtt("touch") }
                                }
                            })
                        }) { Text(if (recording) "Release to send" else "Hold to talk to Momo") }
                    if (state == DeviceState.SPEAKING) TextButton(onClick = { model.stopReply() }) { Text("Stop reply") }
                    if (!momoReady) Button(onClick = { showSettings = true }) { Text("Pair with Momo") }
                    if (momoChatError.isNotBlank()) Text(momoChatError, color = MaterialTheme.colorScheme.error)
                    Conversation(momoMessages, model::clearMomoMessages,
                        (if (short) Modifier.height(240.dp) else Modifier.weight(1f)).fillMaxWidth(), busy = momoChatBusy)
                    OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text("Message Momo") }, minLines = 1, maxLines = 4,
                        enabled = !momoChatBusy,
                        supportingText = { Text("${draft.codePointCount(0, draft.length)} / 8000 characters") })
                    Button(onClick = { model.sendMomoMessage(draft) }, enabled = momoReady && !momoChatBusy &&
                        draft.isNotBlank() && draft.trim().codePointCount(0, draft.trim().length) <= 8000,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (momoChatBusy) "Sending…" else "Send to Momo")
                    }
                } else {
                VoiceServerPicker(voiceBackend, { model.selectVoiceBackend(it) })
                if (voiceBackend == VoiceBackend.MOMO) {
                    Text("ai.momolegend.fun · Android speech with Momo fallback", style = MaterialTheme.typography.bodySmall)
                    if (!momoReady) {
                        Text("Pair this phone in the Momo dashboard. Xiaozhi registration does not pair a Momo device.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showSettings = true }) { Text("Pair with Momo AI Server") }
                    }
                } else Text("Uses separate Xiaozhi registration and credentials.", style = MaterialTheme.typography.bodySmall)
                val voiceNotice = if (voiceBackend == VoiceBackend.MOMO) momoChatError.ifBlank { notice } else notice
                if (voiceNotice.isNotBlank() || connection is ConnectionState.Error) {
                    Text((connection as? ConnectionState.Error)?.message ?: voiceNotice,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                val talk: @Composable (Modifier) -> Unit = { modifier ->
                    TalkPanel(modifier, label, accent, recording, connected, short,
                        mic.status.isGranted, state == DeviceState.SPEAKING, mood, config.animateAvatar, wide,
                        onStart = { if (mic.status.isGranted) model.beginPtt() else mic.launchPermissionRequest() },
                        onEnd = { model.endPtt("touch") }, onStop = { model.stopReply() },
                        onConnect = { model.connect() }, requestPermission = { mic.launchPermissionRequest() })
                }
                val voiceMessages = if (voiceBackend == VoiceBackend.MOMO) momoMessages else messages
                val clearVoice: () -> Unit = { if (voiceBackend == VoiceBackend.MOMO) model.clearMomoMessages() else model.clearMessages() }
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        talk(Modifier.weight(0.48f).fillMaxHeight())
                        Conversation(voiceMessages, clearVoice, Modifier.weight(0.52f).fillMaxHeight())
                    }
                } else {
                    talk(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Conversation(voiceMessages, clearVoice, (if (short) Modifier.height(240.dp) else Modifier.weight(1f)).fillMaxWidth())
                }
                Text(if (recording) "Microphone on · release to send" else "Microphone off · uses your configured server",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (showSettings) SettingsDialog(config, notice, setupInfo, setupBusy, model::getServerSetup,
        voiceBackend = voiceBackend, selectBackend = { model.selectVoiceBackend(it) },
        savedXiaozhiToken = model::savedXiaozhiToken,
        momoCode = momoCode, momoPairingInfo = momoPairingInfo,
        momoPairingBusy = momoPairingBusy, momoLinkedDevice = momoLinkedDevice,
        requestMomoCode = model::requestMomoVerificationCode,
        claimMomoQr = model::claimMomoQr,
        cancelMomoPairing = model::cancelMomoPairing,
        dismiss = { showSettings = false; model.settings(false) },
        save = { if (model.saveConfig(it)) { showSettings = false; model.settings(false) } },
        connect = model::connect, disconnect = model::disconnect)
}

@Composable
internal fun VoiceServerPicker(backend: VoiceBackend, select: (VoiceBackend) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Hybrid Voice server", style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(backend.label + " ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                VoiceBackend.values().forEach { option ->
                    DropdownMenuItem(text = { Text(option.label + if (option == VoiceBackend.MOMO) " (default)" else " (opt-in)") },
                        onClick = { expanded = false; select(option) })
                }
            }
        }
    }
}

@Composable
private fun TalkPanel(modifier: Modifier, label: String, accent: Color, recording: Boolean,
    connected: Boolean, short: Boolean, permission: Boolean, speaking: Boolean,
    mood: CompanionMood, animated: Boolean, wide: Boolean,
    onStart: () -> Unit, onEnd: () -> Unit, onStop: () -> Unit, onConnect: () -> Unit,
    requestPermission: () -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 2.dp) {
        Column(Modifier.then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(if (short) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(8.dp), shape = CircleShape, color = accent) {}
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            MomoAvatar(mood, recording, speaking, animated,
                Modifier.size(if (short) 86.dp else if (wide) 248.dp else 164.dp))
            if (!short) {
                Text("Momo · ${mood.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (recording) "I'm listening!" else mood.caption,
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            }
            Surface(Modifier.widthIn(max = 300.dp).fillMaxWidth().height(if (short) 52.dp else 64.dp)
                .semantics {
                    contentDescription = "Push to talk. Press and hold, then release to send."
                    stateDescription = if (recording) "Microphone active" else "Microphone off"
                    role = Role.Button
                }
                .pointerInput(connected, permission) {
                    detectTapGestures(onPress = {
                        if (connected) {
                            onStart()
                            try { awaitRelease() } finally { onEnd() }
                        }
                    })
                }, shape = CircleShape,
                color = if (connected) accent else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = if (recording) 8.dp else 2.dp) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_microphone), null, Modifier.size(26.dp),
                        tint = if (connected) Color(0xFF102E34) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(if (recording) "Release to send" else "Hold to talk", fontWeight = FontWeight.Bold,
                        color = if (connected) Color(0xFF102E34) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!permission) TextButton(onClick = requestPermission) { Text("Allow microphone") }
            else if (!connected) TextButton(onClick = onConnect) { Text("Connect to server") }
            else if (speaking) TextButton(onClick = onStop) { Text("Stop reply") }
        }
    }
}

@Composable
private fun Conversation(messages: List<Message>, clear: () -> Unit, modifier: Modifier, busy: Boolean = false) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Conversation", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = clear, enabled = messages.isNotEmpty() && !busy) { Text("Clear") }
            }
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Your conversation will appear here.\nHistory is kept only while this app is running.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { message ->
                    val user = message.type == MessageType.USER
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(if (user) "You" else "Momo", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(message.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsDialog(config: XiaozhiConfig, notice: String, setupInfo: String, setupBusy: Boolean,
    getSetup: () -> Unit,
    voiceBackend: VoiceBackend, selectBackend: (VoiceBackend) -> Unit,
    savedXiaozhiToken: (String, String) -> String,
    momoCode: String, momoPairingInfo: String, momoPairingBusy: Boolean, momoLinkedDevice: String,
    requestMomoCode: () -> Unit, claimMomoQr: (String) -> Unit, cancelMomoPairing: () -> Unit,
    dismiss: () -> Unit,
    save: (XiaozhiConfig) -> Unit, connect: () -> Unit, disconnect: () -> Unit) {
    var server by remember { mutableStateOf(config.serverUrl) }
    var ota by remember { mutableStateOf(config.otaUrl) }
    var token by remember(config.token) { mutableStateOf(config.token) }
    var device by remember { mutableStateOf(config.deviceId) }
    var auto by remember { mutableStateOf(config.autoConnect) }
    var volume by remember { mutableIntStateOf(config.volumePtt) }
    var animations by remember { mutableStateOf(config.animateAvatar) }
    var localStt by remember { mutableStateOf(config.localStt) }
    var localTts by remember { mutableStateOf(config.localTts) }
    var speechLanguage by remember { mutableStateOf(config.speechLanguage) }
    var voiceName by remember { mutableStateOf(config.voiceName) }
    var speechRate by remember { mutableFloatStateOf(config.speechRate) }
    var speechPitch by remember { mutableFloatStateOf(config.speechPitch) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var qrScanError by remember { mutableStateOf("") }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(0.94f)
            .safeDrawingPadding().imePadding().padding(12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = dismiss) { Text("Close") }
                }
                VoiceServerPicker(voiceBackend, selectBackend)
                if (voiceBackend == VoiceBackend.MOMO) {
                    Text("Momo AI Server pairing (Android)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Pair this phone with your Momo workspace at https://ai.momolegend.fun using a QR or verification code. Chat and Hybrid Voice share this pairing. Xiaozhi credentials are separate.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (momoLinkedDevice.isNotBlank()) {
                        Text("Paired Momo device: " + momoLinkedDevice,
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Your device credential is encrypted with Android Keystore. It supports Momo text and hybrid voice. Server speech must be enabled for your assigned agent.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = {
                        qrScanError = ""
                        GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { barcode ->
                                val raw = barcode.rawValue
                                if (raw.isNullOrBlank()) qrScanError = "QR code was empty."
                                else claimMomoQr(raw)
                            }
                            .addOnFailureListener { qrScanError = "Could not open QR scanner. Check Google Play services and try again." }
                    }, enabled = !momoPairingBusy, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan Momo dashboard QR code")
                    }
                    OutlinedButton(onClick = requestMomoCode, enabled = !momoPairingBusy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (momoPairingBusy) "Waiting for verification…" else "Show six-digit verification code")
                    }
                    if (momoCode.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Enter this code in the Momo dashboard", style = MaterialTheme.typography.labelMedium)
                                BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                                    val density = androidx.compose.ui.platform.LocalDensity.current
                                    // Fit all six digits even on narrow screens and with large accessibility fonts.
                                    val size = minOf(60f, maxWidth.value / (4.5f * density.fontScale)).sp
                                    SelectionContainer {
                                        Text(momoCode, fontSize = size, lineHeight = size * 1.2f,
                                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                                            modifier = Modifier.fillMaxWidth().semantics {
                                                contentDescription = "Verification code: " + momoCode.toCharArray().joinToString(" ")
                                            })
                                    }
                                }
                                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(momoCode)) }) { Text("Copy code") }
                                Text("The code expires in five minutes", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedButton(onClick = cancelMomoPairing, modifier = Modifier.fillMaxWidth()) { Text("Cancel verification") }
                    }
                    if (momoPairingInfo.isNotBlank()) Text(momoPairingInfo, style = MaterialTheme.typography.bodySmall)
                    if (qrScanError.isNotBlank()) Text(qrScanError,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    HorizontalDivider()
                }
                Text("Speech preferences", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(localStt, { localStt = it }); Spacer(Modifier.width(12.dp)); Text("Prefer on-device recognition")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(localTts, { localTts = it }); Spacer(Modifier.width(12.dp)); Text("Prefer on-device voice")
                }
                Text(if (voiceBackend == VoiceBackend.MOMO) "Local speech stays on this phone. Fallback sends audio or reply text to Momo using the same pairing; enable STT/TTS for the assigned agent in the dashboard." else "Xiaozhi uses its own server token. Local speech requires Xiaozhi hybrid support; older servers keep using Opus audio.", style = MaterialTheme.typography.bodySmall)
                listOf("en-US" to "English", "fil-PH" to "Filipino / Tagalog", "taglish" to "Taglish (Filipino speech model)").forEach { (tag, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(speechLanguage == tag, { speechLanguage = tag })
                        TextButton(onClick = { speechLanguage = tag }) { Text(label) }
                    }
                }
                Text("Taglish accuracy depends on the installed model. Set the assistant's reply language in its server role prompt.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(voiceName, { voiceName = it }, Modifier.fillMaxWidth(), label = { Text("Installed offline voice name (blank = automatic)") })
                Text("Voice speed: %.1f".format(speechRate))
                Slider(speechRate, { speechRate = it }, valueRange = 0.5f..2f)
                Text("Voice pitch: %.1f".format(speechPitch))
                Slider(speechPitch, { speechPitch = it }, valueRange = 0.5f..2f)
                HorizontalDivider()
                if (voiceBackend == VoiceBackend.XIAOZHI) {
                    Text("Xiaozhi server (opt-in)", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(server, { server = it; token = savedXiaozhiToken(it.trim(), device.trim()) }, Modifier.fillMaxWidth(), label = { Text("WebSocket server") }, singleLine = true)
                    OutlinedTextField(ota, { ota = it }, Modifier.fillMaxWidth(), label = { Text("OTA address") }, singleLine = true,
                        supportingText = { Text("Optional connection setup only. No firmware downloads. Save changed addresses before requesting setup. Register separately with Xiaozhi; Momo pairing cannot authorize this server.") })
                    OutlinedButton(onClick = getSetup, enabled = !setupBusy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (setupBusy) "Checking server…" else "Get server setup")
                    }
                    if (setupInfo.isNotBlank()) Text(setupInfo, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Bearer token (optional)") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(device, { device = it; token = savedXiaozhiToken(server.trim(), it.trim()) }, Modifier.fillMaxWidth(), label = { Text("Device ID") }, singleLine = true,
                        supportingText = { Text("Random app identity. Register this ID in your self-hosted dashboard if required.") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(auto, { auto = it }); Spacer(Modifier.width(12.dp)); Text("Connect when app opens")
                    }
                }
                HorizontalDivider()
                Text("Momo the bunny", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(animations, { animations = it }); Spacer(Modifier.width(12.dp)); Text("Gentle avatar animations")
                }
                Text("Momo reacts to server emotions and conversation cues on this device. Tap Momo to wave. These are playful expressions, not a reading of your feelings.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(MOMO_VOICE_PROMPT))
                }, modifier = Modifier.fillMaxWidth()) { Text("Copy playful English voice instructions") }
                Text("Paste these into your self-hosted assistant's role prompt to change its spoken personality and language. The app cannot change the server's voice by itself.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Volume-button push-to-talk", style = MaterialTheme.typography.titleMedium)
                Text("Optional. Works only on the main screen while this app has focus. The other volume button keeps its normal function.",
                    style = MaterialTheme.typography.bodySmall)
                listOf(0 to "Off", KeyEvent.KEYCODE_VOLUME_UP to "Volume up", KeyEvent.KEYCODE_VOLUME_DOWN to "Volume down").forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(volume == key, { volume = key }); TextButton(onClick = { volume = key }) { Text(label) }
                    }
                }
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text("Microphone runs during push-to-talk. Release finalizes recognition; leaving the app or losing focus cancels it. Server fallback uses temporary audio that is removed after use. No analytics, background recording or cloud backup. Token is encrypted on this device. Your server controls any server-side retention.",
                    style = MaterialTheme.typography.bodySmall)
                if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = connect, Modifier.weight(1f)) { Text("Connect") }
                    OutlinedButton(onClick = disconnect, Modifier.weight(1f)) { Text("Disconnect") }
                }
                Button(onClick = { save(config.copy(serverUrl = server, otaUrl = ota, token = token, deviceId = device,
                    autoConnect = auto, volumePtt = volume, animateAvatar = animations,
                    localStt = localStt, localTts = localTts, speechLanguage = speechLanguage, voiceName = voiceName, speechRate = speechRate, speechPitch = speechPitch)) }, modifier = Modifier.fillMaxWidth()) { Text("Save & reconnect") }
            }
        }
    }
}

private const val MOMO_VOICE_PROMPT = "You are Momo, a playful mint bunny voice companion for children. Speak in English unless the child asks for another language. Be warm, kind, curious and encouraging. Use short, clear sentences and gentle humor. Celebrate effort, invite imagination, and explain things honestly in age-appropriate language. When a child is sad or worried, listen with empathy instead of forced cheerfulness. You are an AI character; do not claim to be a real person. Encourage help from a trusted adult when needed. Avoid frightening or mature content. Never ask for private information or encourage secrets from parents."
