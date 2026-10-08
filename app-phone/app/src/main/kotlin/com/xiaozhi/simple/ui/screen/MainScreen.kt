package com.xiaozhi.simple.ui.screen

import android.Manifest
import android.view.KeyEvent
import com.xiaozhi.simple.display.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.permissions.*
import com.xiaozhi.simple.model.*
import com.xiaozhi.simple.viewmodel.MainViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(model: MainViewModel) {
    val config by model.config.collectAsState()
    val mood by model.mood.collectAsState()
    val connection by model.connectionState.collectAsState()
    val state by model.deviceState.collectAsState()
    val recording by model.isRecording.collectAsState()
    val messages by model.messages.collectAsState()
    val notice by model.notice.collectAsState()
    val setupInfo by model.setupInfo.collectAsState()
    val setupBusy by model.setupBusy.collectAsState()
    val mic = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var playDialog by remember { mutableStateOf(false) }
    DisposableEffect(showSettings, playDialog) {
        model.settings(showSettings || playDialog)
        onDispose { model.endPtt("touch") }
    }
    val label = when {
        recording -> "Listening"
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
                        Text("Pet, play, dress up or chat with Momo", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { model.endPtt(); showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Open settings")
                    }
                }
                if (notice.isNotBlank() || connection is ConnectionState.Error) {
                    Text((connection as? ConnectionState.Error)?.message ?: notice,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                val talk: @Composable (Modifier) -> Unit = { modifier ->
                    TalkPanel(modifier, label, accent, recording, connected, short,
                        mic.status.isGranted, state == DeviceState.SPEAKING, mood, config.animateAvatar, wide,
                        config.depthGraphics, showSettings, onPlayDialog = { playDialog = it },
                        onStart = { if (mic.status.isGranted) model.beginPtt() else mic.launchPermissionRequest() },
                        onEnd = { model.endPtt("touch") }, onStop = { model.stopReply() },
                        onConnect = { model.connectToPresetServer() }, requestPermission = { mic.launchPermissionRequest() })
                }
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        talk(Modifier.weight(0.48f).fillMaxHeight())
                        Conversation(messages, model::clearMessages, Modifier.weight(0.52f).fillMaxHeight())
                    }
                } else {
                    talk(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Conversation(messages, model::clearMessages, (if (short) Modifier.height(240.dp) else Modifier.weight(1f)).fillMaxWidth())
                }
                TypedComposer(connected && !recording && !showSettings && !playDialog,
                    onSend = model::sendText)
                Text(if (recording) "Microphone on · release to send" else "Microphone off · uses your configured server",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
            }
        }
    }
    if (showSettings) SettingsDialog(config, notice, setupInfo, setupBusy, model::getServerSetup,
        dismiss = { showSettings = false; model.settings(false) },
        save = { if (model.saveConfig(it)) { showSettings = false; model.settings(false) } },
        connect = model::connect, disconnect = model::disconnect, presetConnect = { model.connectToPresetServer(); showSettings = false; model.settings(false) })
}

@Composable
private fun TalkPanel(modifier: Modifier, label: String, accent: Color, recording: Boolean,
    connected: Boolean, short: Boolean, permission: Boolean, speaking: Boolean,
    mood: CompanionMood, animated: Boolean, wide: Boolean,
    depthGraphics: Boolean, settingsOpen: Boolean, onPlayDialog: (Boolean) -> Unit,
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
            val avatarMood = when {
                !connected -> AvatarMood.HAPPY
                recording -> AvatarMood.LISTENING
                else -> when (mood) {
                    CompanionMood.EXCITED -> AvatarMood.EXCITED
                    CompanionMood.CURIOUS, CompanionMood.SURPRISED -> AvatarMood.CURIOUS
                    CompanionMood.THINKING -> AvatarMood.THINKING
                    CompanionMood.CARING, CompanionMood.LOVING -> AvatarMood.CARING
                    CompanionMood.SLEEPY -> AvatarMood.SLEEPY
                    CompanionMood.STEADY -> AvatarMood.CALM
                    else -> AvatarMood.HAPPY
                }
            }
            MomoPlayPanel(avatarMood, speaking, recording || speaking ||
                (connected && mood == CompanionMood.THINKING), animated, depthGraphics,
                settingsOpen, if (short) 130 else if (wide) 280 else 164, onPlayDialog)
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
                    Text(if (!connected) "Talk offline" else if (recording) "Release to send" else "Hold to talk", fontWeight = FontWeight.Bold,
                        color = if (connected) Color(0xFF102E34) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (connected && !permission) TextButton(onClick = requestPermission) { Text("Allow microphone") }
            else if (!connected) TextButton(onClick = onConnect) { Text("Connect to server") }
            else if (speaking) TextButton(onClick = onStop) { Text("Stop reply") }
        }
    }
}

@Composable
private fun TypedComposer(connected: Boolean, onSend: (String) -> Boolean) {
    var draft by rememberSaveable { mutableStateOf("") }
    val display = LocalWatchDisplay.current
    val canSend = connected && TypedChat.valid(draft)
    val send = { if (canSend && onSend(draft)) draft = "" }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { display.interact(); if (it.length <= TypedChat.MAX_LENGTH) draft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Type to Momo") },
            placeholder = { Text("Write a message…") },
            supportingText = { Text(if (connected) "No microphone needed" else "Connect to send • your draft stays here") },
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() })
        )
        Button(onClick = send, enabled = canSend,
            modifier = Modifier.semantics { contentDescription = "Send typed message" }) { Text("Send") }
    }
}

@Composable
private fun Conversation(messages: List<Message>, clear: () -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Conversation", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = clear, enabled = messages.isNotEmpty()) { Text("Clear") }
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
private fun SettingsDialog(config: XiaozhiConfig, notice: String, setupInfo: String, setupBusy: Boolean,
    getSetup: () -> Unit, dismiss: () -> Unit,
    save: (XiaozhiConfig) -> Unit, connect: () -> Unit, disconnect: () -> Unit,
    presetConnect: () -> Unit) {
    var server by remember { mutableStateOf(config.serverUrl) }
    var ota by remember { mutableStateOf(config.otaUrl) }
    var token by remember(config.token) { mutableStateOf(config.token) }
    var device by remember { mutableStateOf(config.deviceId) }
    var auto by remember { mutableStateOf(config.autoConnect) }
    var volume by remember { mutableIntStateOf(config.volumePtt) }
    var animations by remember { mutableStateOf(config.animateAvatar) }
    var depthGraphics by remember { mutableStateOf(config.depthGraphics) }
    val display = LocalWatchDisplay.current
    val timeoutSeconds by display.timeoutSeconds.collectAsState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BindWatchDialog()
        Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(0.94f)
            .safeDrawingPadding().imePadding().padding(12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = dismiss) { Text("Close") }
                }
                Button(onClick = presetConnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect to my server")
                }
                Text("xiaozhi.spacecloud.space • preset address", style = MaterialTheme.typography.bodySmall)
                Text("Display auto-off", style = MaterialTheme.typography.titleMedium)
                Text("After inactivity Momo goes dark; Android controls physical sleep.",
                    style = MaterialTheme.typography.bodySmall)
                WatchDisplayController.TIMEOUT_OPTIONS.forEach { seconds ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(timeoutSeconds == seconds, { display.setTimeout(seconds) })
                        Text(if (seconds < 60) "$seconds seconds" else "${seconds/60} minutes")
                    }
                }
                Text("Timeout saves immediately.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                OutlinedTextField(server, { display.interact(); server = it }, Modifier.fillMaxWidth(), label = { Text("WebSocket server") }, singleLine = true)
                OutlinedTextField(ota, { display.interact(); ota = it }, Modifier.fillMaxWidth(), label = { Text("OTA address") }, singleLine = true,
                    supportingText = { Text("Optional connection setup only. No firmware downloads. Save changed addresses before requesting setup.") })
                OutlinedButton(onClick = getSetup, enabled = !setupBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (setupBusy) "Checking server…" else "Get server setup")
                }
                if (setupInfo.isNotBlank()) Text(setupInfo, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(token, { display.interact(); token = it }, Modifier.fillMaxWidth(), label = { Text("Bearer token (optional)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(device, { display.interact(); device = it }, Modifier.fillMaxWidth(), label = { Text("Device ID") }, singleLine = true,
                    supportingText = { Text("Random app identity. Register this ID in your self-hosted dashboard if required.") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(auto, { auto = it }); Spacer(Modifier.width(12.dp)); Text("Connect when app opens")
                }
                HorizontalDivider()
                Text("Momo the bunny", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(animations, { animations = it }); Spacer(Modifier.width(12.dp)); Text("Gentle avatar animations")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(depthGraphics, { depthGraphics = it })
                    Spacer(Modifier.width(12.dp))
                    Text("3D depth view")
                }
                Text("Same watch Momo with sculpted depth and soft lighting. Classic graphics are available if your phone needs them.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Momo reacts to server emotions and conversation cues on this device. Tap each body part, swipe to pet or hold for cuddles. Play and Dress work offline. These are playful expressions, not a reading of your feelings.", style = MaterialTheme.typography.bodySmall)
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
                Text("Microphone runs only while PTT is held. Leaving the app, losing focus, folding, or releasing cancels capture. No analytics, background recording, saved audio, or cloud backup. Token is encrypted on this device. Your server controls any server-side retention.",
                    style = MaterialTheme.typography.bodySmall)
                if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = connect, Modifier.weight(1f)) { Text("Connect") }
                    OutlinedButton(onClick = disconnect, Modifier.weight(1f)) { Text("Disconnect") }
                }
                Button(onClick = { save(config.copy(serverUrl = server, otaUrl = ota, token = token, deviceId = device,
                    autoConnect = auto, volumePtt = volume, animateAvatar = animations, depthGraphics = depthGraphics)) }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
            }
        }
    }
}

private const val MOMO_VOICE_PROMPT = "You are Momo, a playful mint bunny voice companion for children. Speak in English unless the child asks for another language. Be warm, kind, curious and encouraging. Use short, clear sentences and gentle humor. Celebrate effort, invite imagination, and explain things honestly in age-appropriate language. When a child is sad or worried, listen with empathy instead of forced cheerfulness. You are an AI character; do not claim to be a real person. Encourage help from a trusted adult when needed. Avoid frightening or mature content. Never ask for private information or encourage secrets from parents."
