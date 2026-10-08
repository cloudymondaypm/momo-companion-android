package com.xiaozhi.simple.ui.screen

import android.Manifest
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.xiaozhi.simple.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.xiaozhi.simple.model.ConnectionState
import com.xiaozhi.simple.model.DeviceState
import com.xiaozhi.simple.model.MessageType
import com.xiaozhi.simple.model.XiaozhiConfig
import com.xiaozhi.simple.model.AvatarMood
import com.xiaozhi.simple.ui.avatar.MomoAvatar
import com.xiaozhi.simple.viewmodel.MainViewModel

/** Compact UI designed for a small Android kids-watch display. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(Unit) {
        if (!micPermission.status.isGranted) micPermission.launchPermissionRequest()
    }

    val messages by viewModel.messages.collectAsState()
    val deviceState by viewModel.deviceState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val avatarMood by viewModel.avatarMood.collectAsState()
    val awaitingReply by viewModel.awaitingReply.collectAsState()
    val connectionMessage by viewModel.connectionMessage.collectAsState()
    val bindingCode by viewModel.bindingCode.collectAsState()
    val config by viewModel.config.collectAsState()
    val pttKeyCode by viewModel.pttKeyCode.collectAsState()
    val keyLearning by viewModel.keyLearning.collectAsState()
    val lastHardwareKey by viewModel.lastHardwareKey.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val mood = when {
        deviceState == DeviceState.LISTENING -> AvatarMood.LISTENING
        connectionState is ConnectionState.Connecting || awaitingReply -> AvatarMood.THINKING
        connectionState !is ConnectionState.Connected -> AvatarMood.SLEEPY
        else -> avatarMood
    }

    val latestText = messages.lastOrNull()?.content.orEmpty()
    val latestIsUser = messages.lastOrNull()?.type == MessageType.USER

    val stateLabel = when (deviceState) {
        DeviceState.LISTENING -> "LISTENING"
        DeviceState.SPEAKING -> "SPEAKING"
        DeviceState.IDLE -> when (connectionState) {
            is ConnectionState.Connected -> "READY"
            is ConnectionState.Connecting -> "CONNECTING"
            is ConnectionState.Error -> "ERROR"
            is ConnectionState.Disconnected -> "OFFLINE"
        }
    }

    val stateColor = when {
        deviceState == DeviceState.LISTENING -> Color(0xFF2E7D32)
        deviceState == DeviceState.SPEAKING -> Color(0xFF1565C0)
        connectionState is ConnectionState.Error -> MaterialTheme.colorScheme.error
        connectionState is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        else -> Color.Gray
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF9F0),Color(0xFFFFEEF4))))
            .padding(8.dp)
    ) {
        val compact = maxHeight < 320.dp || maxWidth < 320.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = stateColor
                    ) {}
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stateLabel,
                        color = Color(0xFF56656D),
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { viewModel.onPressEnd(); showSettings = true },
                    modifier = Modifier.size(if (compact) 32.dp else 40.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF75668B))
                }
            }

            if (bindingCode.isNotBlank() || connectionState is ConnectionState.Error) {
                Text(if (bindingCode.isNotBlank()) "Bind code: $bindingCode" else "Open Settings for error",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(viewModel, micPermission.status.isGranted) {
                        detectTapGestures(
                            onPress = {
                                if (micPermission.status.isGranted) {
                                    viewModel.onPressStart()
                                    try {
                                        awaitRelease()
                                    } finally {
                                        viewModel.onPressEnd()
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                MomoAvatar(mood, speaking = deviceState == DeviceState.SPEAKING,
                    animated = foreground && !showSettings && !config.reduceMotion,
                    modifier = Modifier.fillMaxSize())
            }
            Text("Momo", color = Color(0xFF71608C), fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 16.sp)
            Text(if (deviceState == DeviceState.SPEAKING && mood == AvatarMood.HAPPY) "Let's chat!" else mood.caption,
                color = Color(0xFF56656D), fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))

            Text(
                text = when {
                    keyLearning -> "Press a physical button"
                    pttKeyCode == KeyEvent.KEYCODE_UNKNOWN -> "Hold screen to talk • map button in ⚙"
                    deviceState == DeviceState.LISTENING -> "Release to send"
                    else -> "Hold button to talk"
                },
                fontSize = if (compact) 10.sp else 12.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF75668B),
                maxLines = 2
            )

            if (latestText.isNotBlank()) {
                Spacer(Modifier.height(if (compact) 5.dp else 9.dp))
                Text(
                    text = if (latestIsUser) "You: $latestText" else latestText,
                    color = Color(0xFF45545E),
                    modifier = Modifier.padding(horizontal = 5.dp),
                    fontSize = if (compact) 10.sp else 12.sp,
                    lineHeight = if (compact) 12.sp else 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (compact) 1 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

        }
    }

    if (showSettings) {
        WatchSettingsDialog(
            config = config,
            deviceId = viewModel.getDeviceId(),
            clientId = viewModel.getClientId(),
            connectionMessage = connectionMessage,
            bindingCode = bindingCode,
            pttKeyCode = pttKeyCode,
            keyLearning = keyLearning,
            lastHardwareKey = lastHardwareKey,
            onHardwareKey = { viewModel.handleHardwareKey(it) },
            onLearnButton = { viewModel.startButtonLearning() },
            onClearButton = { viewModel.clearPttButton() },
            onReconnect = { viewModel.connect() },
            onDisconnect = { viewModel.disconnect() },
            onDismiss = {
                viewModel.cancelButtonLearning()
                showSettings = false
            },
            onSave = {
                viewModel.saveConfigAndReconnect(it)
                showSettings = false
            }
        )
    }
}

@Composable
private fun WatchSettingsDialog(
    config: XiaozhiConfig,
    deviceId: String,
    clientId: String,
    connectionMessage: String,
    bindingCode: String,
    pttKeyCode: Int,
    keyLearning: Boolean,
    lastHardwareKey: String,
    onHardwareKey: (KeyEvent) -> Boolean,
    onLearnButton: () -> Unit,
    onClearButton: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (XiaozhiConfig) -> Unit
) {
    var serverUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var token by remember(config.token) { mutableStateOf(config.token) }
    var autoConnect by remember(config.autoConnect) { mutableStateOf(config.autoConnect) }
    var automaticToken by remember(config.automaticToken) { mutableStateOf(config.automaticToken) }
    var otaUrl by remember(config.otaUrl) { mutableStateOf(config.otaUrl) }
    var reduceMotion by remember(config.reduceMotion) { mutableStateOf(config.reduceMotion) }
    val keyFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .onPreviewKeyEvent { onHardwareKey(it.nativeKeyEvent) }
                .focusRequester(keyFocus)
                .focusable(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            LaunchedEffect(Unit) { keyFocus.requestFocus() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.app_name), modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Close")
                    }
                }

                if (connectionMessage.isNotBlank()) {
                    Text(connectionMessage, style = MaterialTheme.typography.bodySmall)
                }
                if (bindingCode.isNotBlank()) {
                    Text("Bind $bindingCode in your console, then tap Connect.",
                        color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Xiaozhi WebSocket") },
                    supportingText = { Text("Example: wss://ai.example.com/xiaozhi/v1/") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bearer token (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = automaticToken, onCheckedChange = { automaticToken = it })
                    Text("Get token from my server")
                }
                if (automaticToken) {
                    OutlinedTextField(value = otaUrl, onValueChange = { otaUrl = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Device setup / OTA URL") },
                        singleLine = true)
                    Text("Leave bearer token blank for automatic setup.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoConnect, onCheckedChange = { autoConnect = it })
                    Text("Auto-connect")
                }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reduceMotion, onCheckedChange = { reduceMotion = it })
                    Text("Reduce avatar motion")
                }
                Text("Physical PTT button", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (pttKeyCode == KeyEvent.KEYCODE_UNKNOWN)
                        "Not mapped"
                    else
                        "${KeyEvent.keyCodeToString(pttKeyCode)} ($pttKeyCode)",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (keyLearning) {
                    Text(
                        "Press the side/SOS/volume button now…",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (lastHardwareKey.isNotBlank()) {
                    Text("Last event: $lastHardwareKey", style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLearnButton, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(6.dp)) {
                        Text(if (keyLearning) "Waiting…" else "Learn")
                    }
                    OutlinedButton(onClick = onClearButton, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(6.dp)) {
                        Text("Clear")
                    }
                }

                Text(
                    "Device ID: $deviceId",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Client ID: $clientId", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Connect")
                    }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect")
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            config.copy(
                                serverUrl = serverUrl,
                                token = token,
                                autoConnect = autoConnect,
                                automaticToken = automaticToken,
                                otaUrl = otaUrl,
                                reduceMotion = reduceMotion
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & reconnect")
                }
            }
        }
    }
}
