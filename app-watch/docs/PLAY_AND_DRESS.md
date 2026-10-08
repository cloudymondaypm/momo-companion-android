# Momo Watch: Pet, Play, and Dress Up

The watch avatar is now **interactive**. Touching the character never starts a microphone recording.

## Controls

- **Touch Momo**: ears wiggle, nose boops, head receives pats, belly giggles, hands wave, feet dance.
- **Dress**: choose Classic, Strawberry, Sky Blue, Sunshine, or Lavender clothing, plus a bow, party hat, silly glasses, or scarf. Clothing and accessory selection are stored locally and restored after restart.
- **Play**: select one of two small offline games:
  - **Momo Says** — touch five requested body parts in order.
  - **Tickle Race** — find and tickle Momo's belly eight times.
- **Talk**: press and hold the dedicated purple Talk button to record, and release to send. Touching the avatar or wardrobe never activates the microphone. The previously supported, user-mapped hardware PTT key remains available.
- **Settings**: Reduce avatar motion turns off moving animation while keeping touch feedback, games, and outfit choices available.

Momo continues to reflect Xiaozhi speaking/listening/emotion states. Games run entirely on device and do not send touches or scores to the server. There are no ads or in-app purchases.

## Device and performance

Designed for the Kiumo ZH23-YL-RF running Android 8.1/API 26. The avatar remains native vector art and runs at about 20fps while the app is foregrounded. Animation is suspended for app dialogs, background use, and reduced-motion mode. The games do not need an internet connection, but voice chat still needs the configured server.

## Verification

`./gradlew -p app-watch assembleDebug testDebugUnitTest`

Manual watch check: test whether the smaller screen permits tapping nose vs head, releasing PTT reliably (including finger sliding off), and whether wardrobe pickers can be scrolled. The buttons are intentionally separate to avoid accidental audio capture. The APK must still be built and tested on hardware.
