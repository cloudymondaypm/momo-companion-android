package com.xiaozhi.simple.ui.screen

import android.Manifest
import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.xiaozhi.simple.ui.avatar.*
import kotlinx.coroutines.delay
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
    var showWardrobe by remember { mutableStateOf(false) }
    var showGames by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val wardrobePrefs = remember(context) {
        context.getSharedPreferences("momo_watch_wardrobe", Context.MODE_PRIVATE)
    }
    var outfit by remember {
        mutableStateOf(runCatching {
            AvatarOutfit.valueOf(wardrobePrefs.getString("outfit", "CLASSIC") ?: "CLASSIC")
        }.getOrDefault(AvatarOutfit.CLASSIC))
    }
    var accessory by remember {
        mutableStateOf(runCatching {
            AvatarAccessory.valueOf(wardrobePrefs.getString("accessory", "NONE") ?: "NONE")
        }.getOrDefault(AvatarAccessory.NONE))
    }
    var reaction by remember { mutableStateOf(AvatarReaction.NONE) }
    var reactionTick by remember { mutableIntStateOf(0) }
    var reactionMessage by remember { mutableStateOf("") }
    var game by remember { mutableStateOf(AvatarGame.NONE) }
    var gameProgress by remember { mutableIntStateOf(0) }
    var momoSequence by remember { mutableStateOf(AvatarGames.targets) }

    // Each reaction is fleeting. Does not interfere with the voice mood or server connection.
    LaunchedEffect(reactionTick) {
        if (reactionTick > 0) {
            delay(1900)
            reaction = AvatarReaction.NONE
            reactionMessage = ""
        }
    }
    val touchMomo: (AvatarPart) -> Unit = { part ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reaction = AvatarTouch.reaction(part)
        reactionMessage = reaction.caption
        when (game) {
            AvatarGame.MOMO_SAYS -> {
                if (part == momoSequence.getOrNull(gameProgress)) {
                    gameProgress++
                    reaction = AvatarReaction.CELEBRATE
                    reactionMessage = "Great job!"
                    if (gameProgress >= momoSequence.size) {
                        game = AvatarGame.NONE
                        reactionMessage = "You won Momo Says! ⭐"
                    }
                } else reactionMessage = "Oops! Try my ${momoSequence[gameProgress].label}!"
            }
            AvatarGame.TICKLE_RACE -> {
                if (part == AvatarPart.BELLY) {
                    gameProgress++
                    if (gameProgress >= AvatarGames.TICKLE_GOAL) {
                        game = AvatarGame.NONE
                        reaction = AvatarReaction.CELEBRATE
                        reactionMessage = "Tickle champion! 🎉"
                    } else reactionMessage = "Heehee! ${AvatarGames.TICKLE_GOAL - gameProgress} more!"
                } else reactionMessage = "Find my belly! 😆"
            }
            AvatarGame.DANCE_PARTY -> {
                if (part == AvatarGames.danceSteps.getOrNull(gameProgress)) {
                    gameProgress++
                    reaction = AvatarReaction.DANCE
                    if (gameProgress >= AvatarGames.danceSteps.size) {
                        game = AvatarGame.NONE
                        reaction = AvatarReaction.CELEBRATE
                        reactionMessage = "Dance star! You did it! 🎵"
                    } else {
                        reactionMessage = "Nice move! Next: ${AvatarGames.danceSteps[gameProgress].label}!"
                    }
                } else reactionMessage = "Next move: ${AvatarGames.danceSteps[gameProgress].label}!"
            }
            AvatarGame.NONE -> Unit
        }
        reactionTick++
    }
    val cuddleMomo: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reaction = AvatarReaction.CUDDLE
        reactionMessage = AvatarReaction.CUDDLE.caption
        reactionTick++
    }
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
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                MomoAvatar(mood, speaking = deviceState == DeviceState.SPEAKING,
                    animated = foreground && !showSettings && !showWardrobe &&
                        !showGames && !config.reduceMotion,
                    reaction = reaction, reactionTick = reactionTick,
                    style = AvatarStyle(outfit, accessory),
                    onTouch = touchMomo, onCuddle = cuddleMomo,
                    modifier = Modifier.fillMaxSize())
            }
            Text("Momo", color = Color(0xFF71608C), fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 16.sp)
            Text(
                text = when {
                    reactionMessage.isNotBlank() -> reactionMessage
                    game == AvatarGame.MOMO_SAYS ->
                        "Momo says: touch my ${momoSequence[gameProgress].label}!"
                    game == AvatarGame.TICKLE_RACE ->
                        "Tickle my belly! ${gameProgress}/${AvatarGames.TICKLE_GOAL}"
                    game == AvatarGame.DANCE_PARTY ->
                        "Dance! Tap my ${AvatarGames.danceSteps[gameProgress].label}!"
                    deviceState == DeviceState.SPEAKING && mood == AvatarMood.HAPPY -> "Let's chat!"
                    else -> mood.caption
                },
                color = Color(0xFF56656D), fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))

            // Dedicated press-and-hold microphone. Tapping Momo never records audio.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.onPressEnd(); showWardrobe = true },
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Dress", fontSize = if (compact) 10.sp else 12.sp) }
                OutlinedButton(
                    onClick = { viewModel.onPressEnd(); showGames = true },
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Play", fontSize = if (compact) 10.sp else 12.sp) }
                Box(
                    modifier = Modifier
                        .width(if (compact) 90.dp else 108.dp)
                        .height(44.dp)
                        .background(
                            if (deviceState == DeviceState.LISTENING) Color(0xFF318D73)
                            else Color(0xFF7756A6), RoundedCornerShape(24.dp)
                        )
                        .semantics { contentDescription = "Hold to talk to Momo; release to send" }
                        .pointerInput(viewModel, micPermission.status.isGranted) {
                            detectTapGestures(onPress = {
                                if (!micPermission.status.isGranted) {
                                    micPermission.launchPermissionRequest()
                                } else {
                                    viewModel.onPressStart()
                                    try { awaitRelease() } finally { viewModel.onPressEnd() }
                                }
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (deviceState == DeviceState.LISTENING) "Release" else "🎙 Talk",
                        color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 11.sp else 13.sp
                    )
                }
            }
            Spacer(Modifier.height(3.dp))

            Text(
                text = when {
                    keyLearning -> "Press a physical button"
                    pttKeyCode == KeyEvent.KEYCODE_UNKNOWN -> "Hold 🎙 to talk • side button in ⚙"
                    deviceState == DeviceState.LISTENING -> "Release to send"
                    else -> "Hold 🎙 or side button to talk"
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

    if (showWardrobe) {
        MomoWardrobeDialog(
            outfit = outfit, accessory = accessory,
            onOutfit = {
                outfit = it
                wardrobePrefs.edit().putString("outfit", it.name).apply()
            },
            onAccessory = {
                accessory = it
                wardrobePrefs.edit().putString("accessory", it.name).apply()
            },
            onDismiss = { showWardrobe = false }
        )
    }
    if (showGames) {
        MomoGamesDialog(
            activeGame = game,
            onStart = {
                game = it
                gameProgress = 0
                if (it == AvatarGame.MOMO_SAYS) momoSequence = AvatarGames.targets.shuffled()
                reaction = AvatarReaction.CELEBRATE
                reactionMessage = when (it) {
                    AvatarGame.MOMO_SAYS -> "Momo says: touch my ${momoSequence.first().label}!"
                    AvatarGame.TICKLE_RACE -> "Tickle my belly 8 times!"
                    AvatarGame.DANCE_PARTY -> "Dance! Tap my ${AvatarGames.danceSteps.first().label}!"
                    AvatarGame.NONE -> ""
                }
                reactionTick++
                showGames = false
            },
            onStop = {
                game = AvatarGame.NONE
                gameProgress = 0
                reactionMessage = ""
                showGames = false
            },
            onDismiss = { showGames = false }
        )
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

/** Full-screen scrolling pickers stay usable on the Kiumo's small Android 8.1 display. */
@Composable
private fun MomoWardrobeDialog(
    outfit: AvatarOutfit,
    accessory: AvatarAccessory,
    onOutfit: (AvatarOutfit) -> Unit,
    onAccessory: (AvatarAccessory) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Momo's wardrobe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Choose an outfit", fontSize = 12.sp)
                AvatarOutfit.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { item ->
                            val selected = item == outfit
                            OutlinedButton(
                                onClick = { onOutfit(item) },
                                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                contentPadding = PaddingValues(3.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) Color(0xFFE5D8FA)
                                        else Color.Transparent
                                )
                            ) {
                                Text(item.label, fontSize = 10.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Text("Pick an accessory", fontSize = 12.sp)
                AvatarAccessory.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { item ->
                            OutlinedButton(
                                onClick = { onAccessory(item) },
                                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                contentPadding = PaddingValues(3.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (item == accessory) Color(0xFFE5D8FA)
                                        else Color.Transparent
                                )
                            ) { Text(item.label, fontSize = 10.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun MomoGamesDialog(
    activeGame: AvatarGame,
    onStart: (AvatarGame) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Play with Momo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Momo Says: touch the body part Momo names (5 rounds).",
                    fontSize = 11.sp)
                Button(onClick = { onStart(AvatarGame.MOMO_SAYS) },
                    modifier = Modifier.fillMaxWidth()) { Text("Play Momo Says") }
                Text("Tickle Race: tap Momo's belly 8 times!",
                    fontSize = 11.sp)
                Button(onClick = { onStart(AvatarGame.TICKLE_RACE) },
                    modifier = Modifier.fillMaxWidth()) { Text("Play Tickle Race") }
                Button(onClick = { onStart(AvatarGame.DANCE_PARTY) },
                    modifier = Modifier.fillMaxWidth()) { Text("Play Dance Party") }
                if (activeGame != AvatarGame.NONE) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop game")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }
}
