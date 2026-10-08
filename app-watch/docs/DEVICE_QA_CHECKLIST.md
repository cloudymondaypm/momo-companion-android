# Kiumo ZH23-YL-RF watch acceptance checklist (v0.4.2)

This is a **manual test plan**, not a claim that the physical watch has been tested.
Target device: Kiumo ZH23-YL-RF, Android 8.1 (API 27). Minimum app API is 26.

## Build and install

1. From [GitHub Actions](https://github.com/cloudymondaypm/momo-companion-android/actions/workflows/watch-apk.yml), open a successful **Build watch APK** run for the feature branch or merged release and download the `momo-companion-watch-debug` artifact. Extract its APK.
2. Record the build's commit SHA and app version in the results below.
3. Install using a method enabled on your watch (for example local file transfer or Android debugging). If installation is blocked, permit APK installation from that source in the watch's Android settings.
4. **Signing caution:** GitHub-hosted runners may generate a new debug signing certificate. Android refuses to update an installed app whose package ID is the same but signing certificate differs. Do not uninstall an older installation until you have preserved the server address, pairing identity/token, wardrobe choices and any other needed data. Reinstalling can reset private app data and require server re-pairing. Production distribution should use one securely retained release signing key (never committed to this repository).

## Interaction and usability checks

| Check | Expected | Pass / Fail / Notes |
| --- | --- | --- |
| Startup on Android 8.1 | Watch opens without crashes; avatar is visible | |
| Quiet idle for 1 minute | Character blinks and gently moves; no microphone opens | |
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
