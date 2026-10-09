package com.xiaozhi.simple.model

import com.xiaozhi.simple.ui.avatar.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class WatchAvailabilityTest {
    private val offlineStates = listOf(ConnectionState.Disconnected,
        ConnectionState.Connecting, ConnectionState.Error("Server unavailable"))

    @Test fun onlyServerConnectedEnablesVoiceAndOfflineMomoStaysPlayful() {
        offlineStates.forEach { state ->
            assertFalse(WatchAvailability.canTalk(state))
            DeviceState.entries.forEach { device ->
                assertEquals(AvatarMood.HAPPY,
                    WatchAvailability.mood(state, device, true, AvatarMood.SLEEPY))
            }
        }
        assertTrue(WatchAvailability.canTalk(ConnectionState.Connected))
        assertEquals(AvatarMood.LISTENING, WatchAvailability.mood(
            ConnectionState.Connected, DeviceState.LISTENING, false, AvatarMood.HAPPY))
    }

    @Test fun everyGameCompletesLocallyAcrossTransportStates() {
        (offlineStates + ConnectionState.Connected).forEach { state ->
            assertEquals(state is ConnectionState.Connected, WatchAvailability.canTalk(state))
            val steps = mapOf(
                AvatarGame.MOMO_SAYS to AvatarGames.targets,
                AvatarGame.TICKLE_RACE to List(8) { AvatarPart.BELLY },
                AvatarGame.DANCE_PARTY to AvatarGames.danceSteps,
                AvatarGame.HUG_TIME to List<AvatarPart?>(3) { null })
            steps.forEach { (game, sequence) ->
                var progress = 0
                var wins = 0
                sequence.forEach { part ->
                    val move = AvatarGames.move(game, progress, part)
                    assertTrue(move.accepted)
                    progress = move.progress
                    if (move.completed) wins++
                }
                assertEquals(1, wins)
                assertFalse(AvatarGames.move(game, progress, sequence.last()).completed)
            }
            AvatarPart.entries.forEach { part ->
                assertNotEquals(AvatarReaction.NONE, AvatarTouch.reaction(part))
                assertTrue(AvatarTouch.strokeCaption(part).isNotBlank())
            }
            AvatarOutfit.entries.forEach { outfit ->
                AvatarAccessory.entries.forEach { accessory ->
                    assertEquals(outfit, AvatarStyle(outfit, accessory).outfit)
                    assertEquals(accessory, AvatarStyle(outfit, accessory).accessory)
                }
            }
        }
    }
}
