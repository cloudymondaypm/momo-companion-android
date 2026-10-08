# Kiumo ZH23-YL-RF watch acceptance checklist (v0.4.5)

This is a **manual test plan**, not a claim that the physical watch has been tested.
Target device: Kiumo ZH23-YL-RF, Android 8.1 (API 27). Minimum app API is 26.

## Build and install

1. From [GitHub Actions](https://github.com/cloudymondaypm/momo-companion-android/actions/workflows/watch-apk.yml), open a successful **Build watch APK** run for the feature branch or merged release and download the `momo-companion-watch-debug` artifact. Extract its APK.
2. Record the build's commit SHA and app version in the results below.
3. Install using a method enabled on your watch (for example local file transfer or Android debugging). If installation is blocked, permit APK installation from that source in the watch's Android settings.
4. **Signing caution:** GitHub-hosted runners may generate a new debug signing certificate. Android refuses to update an installed app whose package ID is the same but signing certificate differs. Do not uninstall an older installation until you have preserved the server address, pairing identity/token, wardrobe choices and any other needed data. Reinstalling can reset private app data and require server re-pairing. For repeatable in-place watch updates, set up the optional persistent signer described in `app-watch/README.md`. Keep the key backed up and never commit or upload it publicly.

## Interaction and usability checks

| Check | Expected | Pass / Fail / Notes |
| --- | --- | --- |
| Startup on Android 8.1 | Watch opens without crashes; avatar is visible | |
| Quiet idle for 1 minute | Character blinks; every ~16s a playful wink, silly tongue face, wave or dance may appear; microphone remains closed | |
| Quiet idle during a game | No spontaneous surprise interrupts game instructions | |
| Quiet idle with Reduce Motion enabled | No idle surprises; touch response and games remain functional | |
| Tap head and both ears | Head pat and ear wiggle; haptic/reaction | |
| Tap nose | Distinct nose boop; does not accidentally count as head | |
| Tap hands, belly, feet | Wave, tickle, dance respectively | |
| Tap empty stage/corners | No avatar gesture or game progress | |
| Swipe each body part | Petting feedback for touched part, not a game tap | |
| Long-press Momo | Cuddle pose and floating hearts, no recording | |
| Rapid repeated taps | Animation and captions restart; no crash | |
| Reduced motion enabled | Avatar remains still but petting and games still work | |
| Dress: 7 outfits | Every choice visible in live preview | |
| Dress: 7 accessories | Each accessory fits avatar; choices persist after restart | |
| Momo Says | Randomized five-step sequence; wrong taps do not advance | |
| Tickle Race | Only belly taps count; exactly 8 accepted taps wins | |
| Dance Party | Six instructed steps; wrong taps do not advance | |
| Hug Time | Long-press Momo three times for one star; simple taps and swipes do not count | |
| Idle animation | Movement remains fluid enough at ~11fps; reactions and speaking ~25fps | |
| Game HUD | Prompt and progress bar remain visible during reactions | |
| Stop while playing | One tap on Stop ends game; no star awarded | |
| Game completion | Exactly one star per win; survives app restart | |
| Hold purple Talk | Audio capture only while held (after permission + connection) | |
| Release Talk or slide finger away | Recording stops and audio sends/cancels correctly | |
| Tap/pet during conversation | Voice recording must not start | |
| App background / open dialogs | Avatar animation pauses; no runaway audio or stutter | |
| Offline play | Dress, petting and local games continue without Wi-Fi | |
| Reconnect | Voice can reconnect to the configured self-hosted server | |
| Watch battery / heat | Observe any unexpected drain or warming over 15 minutes | |

## Display timeout and offline acceptance (required before leaving draft)

- Fresh install: default display idle timeout is 2 minutes.
- Settings: select 30 seconds, 1, 2, 5 or 10 minutes; selection saves immediately,
  survives restart and does not reconnect or change Android global timeout/brightness.
- Leave the main screen, Settings, wardrobe and games picker untouched: at the selected
  deadline the app becomes black, window brightness goes to minimum and every app
  window releases KEEP_SCREEN_ON. Idle surprises/animation must not extend this deadline.
- API 27 cannot force hardware screen-off via ordinary public app APIs. Minimum brightness
  may still leave a faint backlight; physical panel sleep follows the watch OS timeout.
  Record observed backlight and physical sleep delay on this exact device.
- Tap/swipe or delivered hardware keys before expiry restart the timer. The first
  wake gesture is consumed (no game move or microphone recording). If Android has
  slept, wake with the watch power button. Android retains ownership of power/lock keys.
- Background the app or turn the screen off: no idle timer or screen-on flag remains;
  return to Momo with normal brightness and a fresh timer. Test wake after a dialog.
- Let the timeout expire while holding Talk: recording stops. Timeout does not
  disconnect the transport or erase local rewards/style.
- Cold launch with Wi-Fi off, failed connection, and missing binding/token:
  all six tap reactions, all six body-part swipes and cuddles work; Momo stays happy.
- In every offline state: all 7 outfits and 7 accessories save locally; complete
  each of the four games and verify one saved star. Restart offline and check persistence.
- Offline Talk is gray and explains connecting in Settings. Tapping it does not
  prompt for microphone permission. A mapped side button does not record offline.
- Connect successfully: touch and side-button hold-to-talk work after granting
  microphone permission. Disconnect during recording/playback: audio stops and local
  play continues. Merely connecting/activating is insufficient to enable Talk.
- Save local settings without server reconnect; use Connect explicitly after editing
  server configuration. Device QA and battery/heat observations remain outstanding.

## Report

- **Device Android build:** 
- **Test date:** 
- **APK SHA / source commit:** 
- **Version displayed:** 
- **Install result / signature issues:** 
- **Touch alignment and screen dimensions:** 
- **Voice/PTT results:** 
- **Any crashes / lag / battery observations:** 

Report failures with the exact step and screen size, and if safe include a short video of the watch screen. Do not publish server tokens, pairing codes, or private audio recordings.
