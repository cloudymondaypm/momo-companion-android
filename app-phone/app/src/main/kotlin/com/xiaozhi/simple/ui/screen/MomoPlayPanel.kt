package com.xiaozhi.simple.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xiaozhi.simple.display.*
import com.xiaozhi.simple.model.*
import com.xiaozhi.simple.ui.avatar.*
import kotlinx.coroutines.delay

/** Same character, interaction rules and local rewards as the watch. */
@Composable
fun MomoPlayPanel(mood: AvatarMood, speaking: Boolean, voiceBusy: Boolean,
    animate: Boolean, depthGraphics: Boolean, settingsOpen: Boolean,
    size: Int, onDialogChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val wardrobePrefs = remember(context) {
        context.getSharedPreferences("momo_watch_wardrobe", Context.MODE_PRIVATE)
    }
    var showWardrobe by remember { mutableStateOf(false) }
    var showGames by remember { mutableStateOf(false) }
    val display = LocalWatchDisplay.current
    val sleeping by display.sleeping.collectAsState()
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
    // All rewards are on-device, offline and never tied to purchases.
    var stars by remember { mutableIntStateOf(wardrobePrefs.getInt("game_stars", 0).coerceAtLeast(0)) }
    val awardStar: () -> Unit = {
        stars += 1
        wardrobePrefs.edit().putInt("game_stars", stars).apply()
    }

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
        if (game != AvatarGame.NONE) {
            val activeGame = game
            val move = AvatarGames.move(activeGame, gameProgress, part, momoSequence)
            gameProgress = move.progress
            when {
                move.completed -> {
                    game = AvatarGame.NONE
                    awardStar()
                    reaction = AvatarReaction.CELEBRATE
                    reactionMessage = when (activeGame) {
                        AvatarGame.MOMO_SAYS -> "You won Momo Says! ⭐"
                        AvatarGame.TICKLE_RACE -> "Tickle champion! 🎉"
                        AvatarGame.DANCE_PARTY -> "Dance star! You did it! 🎵"
                        AvatarGame.HUG_TIME -> "Super hug champion! 💗"
                        AvatarGame.NONE -> ""
                    }
                }
                !move.accepted -> reactionMessage = when (activeGame) {
                    AvatarGame.MOMO_SAYS -> "Try my ${momoSequence.getOrNull(gameProgress)?.label ?: "head"}!"
                    AvatarGame.TICKLE_RACE -> "Find my belly! 😆"
                    AvatarGame.DANCE_PARTY -> "Next: ${AvatarGames.danceSteps.getOrNull(gameProgress)?.label ?: "feet"}!"
                    AvatarGame.HUG_TIME -> "Hold Momo to cuddle!"
                    AvatarGame.NONE -> ""
                }
                activeGame == AvatarGame.MOMO_SAYS -> {
                    reaction = AvatarReaction.CELEBRATE
                    reactionMessage = "Great job!"
                }
                activeGame == AvatarGame.TICKLE_RACE ->
                    reactionMessage = "Heehee! ${AvatarGames.TICKLE_GOAL - gameProgress} more!"
                activeGame == AvatarGame.DANCE_PARTY -> {
                    reaction = AvatarReaction.DANCE
                    reactionMessage = "Nice move! Keep dancing!"
                }
            }
        }
        reactionTick++
    }
    val petMomo: (AvatarPart) -> Unit = { part ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reaction = AvatarTouch.reaction(part)
        reactionMessage = AvatarTouch.strokeCaption(part)
        reactionTick++
    }
    val cuddleMomo: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reaction = AvatarReaction.CUDDLE
        reactionMessage = AvatarReaction.CUDDLE.caption
        if (game == AvatarGame.HUG_TIME) {
            val move = AvatarGames.move(game, gameProgress, null, momoSequence)
            gameProgress = move.progress
            if (move.completed) {
                game = AvatarGame.NONE
                awardStar()
                reaction = AvatarReaction.CELEBRATE
                reactionMessage = "Super hug champion! 💗"
            } else {
                reactionMessage = "Bunny hug! ${AvatarGames.HUG_GOAL - gameProgress} more!"
            }
        }
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

    val dialogOpen = showWardrobe || showGames || settingsOpen
    DisposableEffect(showWardrobe, showGames) {
        onDialogChanged(showWardrobe || showGames)
        onDispose { onDialogChanged(false) }
    }
    LaunchedEffect(foreground, sleeping, dialogOpen, animate, voiceBusy, game, reactionTick) {
        if (AvatarIdle.canSurprise(foreground && !sleeping, dialogOpen, !animate,
            voiceBusy, game != AvatarGame.NONE)) {
            delay(AvatarIdle.SURPRISE_DELAY_MS)
            reaction = AvatarIdle.nextSurprise(reactionTick)
            reactionMessage = reaction.caption
            reactionTick++
        }
    }
    val instruction = when (game) {
        AvatarGame.MOMO_SAYS -> "Tap ${momoSequence.getOrNull(gameProgress)?.label} · ${gameProgress+1}/${momoSequence.size}"
        AvatarGame.TICKLE_RACE -> "Belly taps · $gameProgress/${AvatarGames.TICKLE_GOAL}"
        AvatarGame.DANCE_PARTY -> "Dance ${AvatarGames.danceSteps.getOrNull(gameProgress)?.label} · ${gameProgress+1}/${AvatarGames.danceSteps.size}"
        AvatarGame.HUG_TIME -> "Hold to hug · $gameProgress/${AvatarGames.HUG_GOAL}"
        AvatarGame.NONE -> if (reactionMessage.isNotBlank()) reactionMessage else mood.caption
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MomoAvatar(mood, speaking, foreground && !sleeping && !dialogOpen && animate,
            Modifier.size(size.dp), reaction, reactionTick, AvatarStyle(outfit, accessory),
            touchMomo, petMomo, cuddleMomo,
            depthGraphics = depthGraphics && foreground && !sleeping && !dialogOpen)
        if (game != AvatarGame.NONE) {
            val goal = when(game) {
                AvatarGame.MOMO_SAYS -> momoSequence.size
                AvatarGame.TICKLE_RACE -> AvatarGames.TICKLE_GOAL
                AvatarGame.DANCE_PARTY -> AvatarGames.danceSteps.size
                AvatarGame.HUG_TIME -> AvatarGames.HUG_GOAL
                else -> 1
            }
            LinearProgressIndicator(progress = { gameProgress.toFloat()/goal.coerceAtLeast(1) },
                modifier = Modifier.width(size.dp).padding(bottom = 4.dp))
        }
        Text("Momo · ⭐ $stars", fontWeight = FontWeight.Bold)
        Text(instruction, maxLines = 2, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showWardrobe = true }) { Text("Dress") }
            OutlinedButton(onClick = {
                if (game == AvatarGame.NONE) showGames = true
                else { game = AvatarGame.NONE; gameProgress = 0; reactionMessage = "Game stopped"; reactionTick++ }
            }) { Text(if (game == AvatarGame.NONE) "Play" else "Stop") }
        }
    }
    if (showWardrobe) MomoWardrobeDialog(outfit, accessory,
        onOutfit = { outfit = it; wardrobePrefs.edit().putString("outfit",it.name).apply() },
        onAccessory = { accessory = it; wardrobePrefs.edit().putString("accessory",it.name).apply() },
        onDismiss = { showWardrobe = false })
    if (showGames) MomoGamesDialog(game,
        onStart = {
            game = it; gameProgress = 0
            if (it == AvatarGame.MOMO_SAYS) momoSequence = AvatarGames.targets.shuffled()
            reaction = AvatarReaction.CELEBRATE
            reactionMessage = "Let's play!"
            reactionTick++; showGames = false
        },
        onStop = { game = AvatarGame.NONE; gameProgress = 0; showGames = false },
        onDismiss = { showGames = false })
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
        BindWatchDialog()
        Surface(modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Saved on this watch • works offline", fontSize = 11.sp)
                Text("Momo's wardrobe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height(118.dp), contentAlignment = Alignment.Center) {
                    MomoAvatar(
                        mood = AvatarMood.HAPPY, speaking = false, animated = false,
                        style = AvatarStyle(outfit, accessory),
                        modifier = Modifier.size(114.dp)
                    )
                }
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
        BindWatchDialog()
        Surface(modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("All four games work offline.", fontSize = 11.sp)
                Text("Play with Momo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Momo Says: touch the body part Momo names (5 rounds).",
                    fontSize = 11.sp)
                Button(onClick = { onStart(AvatarGame.MOMO_SAYS) },
                    modifier = Modifier.fillMaxWidth()) { Text("Play Momo Says") }
                Text("Tickle Race: tap Momo's belly 8 times!",
                    fontSize = 11.sp)
                Button(onClick = { onStart(AvatarGame.TICKLE_RACE) },
                    modifier = Modifier.fillMaxWidth()) { Text("Play Tickle Race") }
                Text("Dance Party: follow six different body-part dance steps.", fontSize = 11.sp)
                Button(onClick = { onStart(AvatarGame.DANCE_PARTY) },
                    modifier = Modifier.fillMaxWidth()) { Text("Play Dance Party") }
                Text("Hug Time: hold Momo to cuddle three times!", fontSize = 11.sp)
                Button(onClick = { onStart(AvatarGame.HUG_TIME) },
                    modifier = Modifier.fillMaxWidth()) { Text("Play Hug Time") }
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

