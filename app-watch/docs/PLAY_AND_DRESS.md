# Momo Watch: Pet, Play, and Dress Up

The watch avatar is now **interactive**. Petting, tapping, swiping or dressing the character never starts a microphone recording.

## Controls

- **Pet Momo**: tap ears, nose, head, belly, hands, or feet for distinct gestures. Hold or swipe across Momo for a big bunny cuddle and floating hearts.
- **Dress**: choose Classic, Strawberry, Sky Blue, Sunshine, Lavender, Moon Pajamas, or Super Momo clothing, plus a bow, party hat, silly glasses, scarf, crown, or headphones. The cape and pajama patterns are drawn as lightweight vector art. Choices are saved locally across restarts.
- **Play**: select one of three offline games:
  - **Momo Says** — follow a freshly shuffled sequence of five body-part prompts each round.
  - **Tickle Race** — find and tickle Momo's belly eight times.
  - **Dance Party** — follow six tap-to-the-beat body-part prompts to make Momo dance.
- **Talk**: press and hold the dedicated purple Talk button to record, and release to send. Touching the avatar or wardrobe never activates the microphone. The previously supported, user-mapped hardware PTT key remains available.
- **Settings**: Reduce avatar motion turns off moving animation while keeping touch feedback, games, and outfit choices available.

Momo continues to reflect Xiaozhi speaking/listening/emotion states. Games run entirely on device and do not send touches or scores to the server. There are no ads or in-app purchases.

## Device and performance

Designed for the Kiumo ZH23-YL-RF running Android 8.1/API 26. The avatar remains native vector art and runs at about 20fps while the app is foregrounded. Animation is suspended for app dialogs, background use, and reduced-motion mode. The games do not need an internet connection, but voice chat still needs the configured server.

## Verification

`./gradlew -p app-watch assembleDebug testDebugUnitTest`

Manual watch check: verify tap vs long-press vs swipe on the small screen, nose vs head hit boxes, no false microphone activation, reliable PTT release (including finger sliding off), all new outfit/accessory rendering, and scrollable pickers. The buttons are intentionally separate to avoid accidental audio capture. The APK must still be built and tested on hardware.
