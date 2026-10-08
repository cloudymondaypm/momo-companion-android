package com.xiaozhi.simple.ui.avatar

import android.graphics.Color

/** Parts are in the 200 x 200 coordinate system used by MomoRenderer. */
enum class AvatarPart(val label: String) {
    HEAD("head"), EARS("ears"), NOSE("nose"), BELLY("belly"), FEET("feet"), HANDS("hands")
}

enum class AvatarReaction(val caption: String) {
    NONE(""), PAT("Aww, more head pats!"), WIGGLE("Hey! My ears are ticklish!"),
    BOOP("Boop! Honk honk!"), TICKLE("Heehee! That tickles!"),
    DANCE("Look at my happy feet!"), WAVE("High five, friend!"),
    CELEBRATE("Woohoo! We did it!"), CUDDLE("Bunny hugs and happy hearts!"),
    PEEK("Peekaboo! I see you!"), SILLY("Bleh! Silly bunny face!")
}

enum class AvatarOutfit(val label: String, val color: Int) {
    CLASSIC("Classic", Color.TRANSPARENT),
    STRAWBERRY("Strawberry", Color.rgb(255, 139, 170)),
    SKY("Sky blue", Color.rgb(123, 194, 242)),
    SUNNY("Sunshine", Color.rgb(255, 208, 104)),
    LAVENDER("Lavender", Color.rgb(186, 159, 239)),
    PAJAMAS("Moon pajamas", Color.rgb(142, 175, 237)),
    HERO("Super Momo", Color.rgb(106, 211, 170))
}

enum class AvatarAccessory(val label: String) {
    NONE("None"), BOW("Bow"), HAT("Party hat"), GLASSES("Silly glasses"),
    SCARF("Scarf"), CROWN("Crown"), HEADPHONES("Headphones")
}

data class AvatarStyle(
    val outfit: AvatarOutfit = AvatarOutfit.CLASSIC,
    val accessory: AvatarAccessory = AvatarAccessory.NONE
)

/** Touch math lives separately from Compose to make it easy to test on watch-sized screens. */
object AvatarTouch {
    /** Hit-test the actual bunny silhouette rather than its rectangular drawing stage. */
    fun locate(x: Float, y: Float, width: Float, height: Float): AvatarPart? {
        if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() ||
            width <= 0f || height <= 0f || x < 0f || y < 0f || x > width || y > height) return null
        val scale = minOf(width, height) / 200f
        val px = (x - (width - 200f * scale) / 2f) / scale
        val py = (y - (height - 200f * scale) / 2f) / scale
        fun oval(cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
            val dx = (px - cx) / rx
            val dy = (py - cy) / ry
            return dx * dx + dy * dy <= 1f
        }
        if (oval(67.5f, 56f, 17f, 46f) || oval(132.5f, 56f, 17f, 46f))
            return AvatarPart.EARS
        if (oval(100f, 122f, 14f, 14f)) return AvatarPart.NOSE
        if (oval(76f, 175f, 22f, 18f) || oval(124f, 175f, 22f, 18f))
            return AvatarPart.FEET
        if (oval(51f, 150f, 20f, 23f) || oval(149f, 150f, 20f, 23f))
            return AvatarPart.HANDS
        if (oval(100f, 156f, 37f, 26f)) return AvatarPart.BELLY
        return if (oval(100f, 117f, 65f, 61f)) AvatarPart.HEAD else null
    }

    /** Petting feedback does not count as a mini-game tap. */
    fun strokeCaption(part: AvatarPart): String = when (part) {
        AvatarPart.HEAD -> "Purrr... gentle head pats!"
        AvatarPart.EARS -> "Heehee! Soft ear scratches!"
        AvatarPart.NOSE -> "A nose nuzzle! Boop!"
        AvatarPart.BELLY -> "More belly rubs, please!"
        AvatarPart.FEET -> "Ooh, a tiny foot massage!"
        AvatarPart.HANDS -> "You're holding my paw!"
    }

    fun reaction(part: AvatarPart): AvatarReaction = when (part) {
        AvatarPart.HEAD -> AvatarReaction.PAT
        AvatarPart.EARS -> AvatarReaction.WIGGLE
        AvatarPart.NOSE -> AvatarReaction.BOOP
        AvatarPart.BELLY -> AvatarReaction.TICKLE
        AvatarPart.FEET -> AvatarReaction.DANCE
        AvatarPart.HANDS -> AvatarReaction.WAVE
    }
}

/** Three offline mini-games. No user data or audio is sent to the server. */
enum class AvatarGame(val title: String) {
    NONE("Free play"), MOMO_SAYS("Momo Says"), TICKLE_RACE("Tickle Race"),
    DANCE_PARTY("Dance Party")
}

/** Pure result: only accepted steps advance, and completion can be awarded once. */
data class GameMove(val accepted: Boolean, val progress: Int, val completed: Boolean)

object AvatarGames {
    val targets = listOf(AvatarPart.HEAD, AvatarPart.NOSE, AvatarPart.EARS,
        AvatarPart.BELLY, AvatarPart.FEET)
    val danceSteps = listOf(AvatarPart.FEET, AvatarPart.HANDS, AvatarPart.FEET,
        AvatarPart.HEAD, AvatarPart.HANDS, AvatarPart.FEET)
    const val TICKLE_GOAL = 8

    fun move(game: AvatarGame, progress: Int, part: AvatarPart,
             momoSequence: List<AvatarPart> = targets): GameMove {
        val goal = when (game) {
            AvatarGame.NONE -> 0
            AvatarGame.MOMO_SAYS -> momoSequence.size
            AvatarGame.TICKLE_RACE -> TICKLE_GOAL
            AvatarGame.DANCE_PARTY -> danceSteps.size
        }
        if (progress < 0 || progress >= goal) return GameMove(false, progress, false)
        val correct = when (game) {
            AvatarGame.NONE -> false
            AvatarGame.MOMO_SAYS -> part == momoSequence[progress]
            AvatarGame.TICKLE_RACE -> part == AvatarPart.BELLY
            AvatarGame.DANCE_PARTY -> part == danceSteps[progress]
        }
        val next = if (correct) progress + 1 else progress
        return GameMove(correct, next, correct && next == goal)
    }
}

/** Quiet surprises only while the watch is visible and voice/games are inactive. */
object AvatarIdle {
    const val SURPRISE_DELAY_MS = 16000L
    val surprises = listOf(AvatarReaction.PEEK, AvatarReaction.SILLY,
        AvatarReaction.WAVE, AvatarReaction.DANCE)

    fun canSurprise(
        foreground: Boolean,
        dialogOpen: Boolean,
        reducedMotion: Boolean,
        voiceBusy: Boolean,
        gameActive: Boolean
    ): Boolean = foreground && !dialogOpen && !reducedMotion && !voiceBusy && !gameActive

    fun nextSurprise(tick: Int): AvatarReaction =
        surprises[Math.floorMod(tick, surprises.size)]
}
