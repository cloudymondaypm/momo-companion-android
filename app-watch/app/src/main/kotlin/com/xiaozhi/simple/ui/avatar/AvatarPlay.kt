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
    CELEBRATE("Woohoo! We did it!"), CUDDLE("Bunny hugs and happy hearts!")
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
    fun locate(x: Float, y: Float, width: Float, height: Float): AvatarPart? {
        if (width <= 0f || height <= 0f) return null
        val scale = minOf(width, height) / 200f
        val px = (x - (width - 200f * scale) / 2f) / scale
        val py = (y - (height - 200f * scale) / 2f) / scale
        if (px !in 32f..168f || py !in 11f..188f) return null
        if (py < 87f && (px in 51f..84f || px in 116f..149f)) return AvatarPart.EARS
        if (py in 110f..136f && px in 88f..112f) return AvatarPart.NOSE
        if (py >= 171f && (px in 55f..94f || px in 106f..145f)) return AvatarPart.FEET
        if (py in 137f..175f && (px < 68f || px > 132f)) return AvatarPart.HANDS
        if (py in 137f..180f && px in 68f..132f) return AvatarPart.BELLY
        return AvatarPart.HEAD
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

object AvatarGames {
    val targets = listOf(AvatarPart.HEAD, AvatarPart.NOSE, AvatarPart.EARS,
        AvatarPart.BELLY, AvatarPart.FEET)
    val danceSteps = listOf(AvatarPart.FEET, AvatarPart.HANDS, AvatarPart.FEET,
        AvatarPart.HEAD, AvatarPart.HANDS, AvatarPart.FEET)
    const val TICKLE_GOAL = 8
}
