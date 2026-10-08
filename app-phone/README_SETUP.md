# Momo Companion for Samsung Galaxy Z Fold5

Install `Momo-Companion-Galaxy-Z-Fold5.apk` on your phone. Allow installation from your
file-transfer or file-manager app when Android asks. Grant microphone permission
inside Xiaozhi, then hold the talk button to speak and release it to send.

## Momo, your playful bunny companion

This update adds an original animated mint plush bunny with nine moods: happy,
excited, curious, thinking, caring, surprised, warm, sleepy and encouraging.
Momo gently bobs, blinks, wiggles its ears, moves its mouth during playback and
waves when tapped. Tapping the avatar never activates the microphone.
Expressions follow your server's `llm` emotion messages. Without these, a light
English keyword fallback runs locally on the existing conversation text; there
is no extra analysis service or additional network request. This is a playful
visual reaction, not emotional or psychological assessment. Difficult topic
words receive a caring expression, and unknown server emotions fall back safely.

Settings includes Gentle avatar animations, which can be switched off. Compact
windows scroll when needed; the cover and inner display retain their responsive
layouts. Audio and all PTT privacy controls remain as before.

To make the spoken personality playful and English, open Settings, choose
Copy playful English voice instructions, then paste into your self-hosted
assistant's role/system prompt and save there. The server controls spoken
language, content, and voice; the APK alone does not change that configuration.

Install this APK over the previous version. It uses the same package/signing
key with a higher version code, preserving your settings and registered ID.
Momo-Avatar-Preview.png illustrates the design; it is not a phone screenshot.

## Your server is already configured

- WebSocket: wss://xiaozhi.spacecloud.space/xiaozhi/v1/
- OTA setup: https://xiaozhi.spacecloud.space/xiaozhi/ota/

The live server rejected a connection without a token. Its OTA endpoint supplies
a token and requests activation. After installation, open Settings → Get server
setup. Register the displayed code in your self-hosted Xiaozhi dashboard, then
tap Connect. You can also enter a server token and Device ID manually. Changed
addresses must be saved before requesting setup. No server secrets are embedded
in the APK.

The OTA response currently advertises `ws://xiaozhi.server.com:8000/xiaozhi/v1/`,
which is a placeholder. The app deliberately retains your configured secure
WebSocket address when retrieving its token. Your server's OTA configuration
should eventually advertise the correct public secure address for other clients.

## Interaction and privacy

- Cover screen: a large hold-to-talk control above conversation history.
- Inner display: side-by-side talking and conversation panels at 600 dp window
  width; adapts to rotation and smaller multi-window sizes.
- Settings can map Volume Up or Volume Down to PTT. Mapping is off initially,
  only consumes the selected button on the main screen while the app has focus,
  and does not use Accessibility services, background services, or the power key.
- Microphone opens only during a PTT hold; release, cancellation, focus loss,
  backgrounding, and activity recreation stop it. Reconnection never resumes
  capture automatically. Touch and hardware holds do not stop each other.
- Explicit connection, listening, and speaking states; stop-reply control;
  bounded in-memory conversation history with Clear.
- Automatic reconnect with delays up to 30 seconds while the app is active.
  Authentication failures wait for corrected settings instead of retrying.
- Only microphone and internet permissions. No storage, phone, location,
  contacts, Bluetooth, analytics, wake-word listener, or recording files.
- Random per-install app Device ID, editable in Settings; no hardware fingerprint.
- Token encrypted using Android Keystore. Cloud backup and device transfer
  backup are disabled. The server controls server-side retention and AI services.
- Uses configured WSS and HTTPS endpoints with standard certificate verification;
  no automatic redirect or mandatory xiaozhi.me activation or OTA dependency.
- Get server setup sends only an app identifier, Device ID, Client ID and app
  version to your configured OTA endpoint. It never downloads firmware.

## Build and validation

Version 1.2.0-momo; package `space.spacecloud.xiaozhi.fold5`. Minimum Android 10
(API 29), target/compile Android 16 (API 36). Includes ARM64 for Fold5 and x86_64.
Gradle 8.11.1, Android Gradle plugin 8.9.2, Kotlin 1.9.22, JDK 17. Audio uses
16 kHz mono Opus in 60 ms frames. Libopus 1.5.2 is built from official source;
playback uses Android AudioTrack. The native library has 16 KB ELF alignment,
and APK ZIP alignment and signing are verified separately.

Validation report accompanies this APK. Protocol tests and Android lint were
run locally. Network checks sent no microphone audio. No Fold5 or Android
emulator was attached, so installation, actual microphone/speaker routing,
hardware button delivery, cover/inner layout appearance and fold transitions
still need a phone check. Do not interpret the build as a full device test.

## Source and rebuilding

`Momo-Companion-Galaxy-Z-Fold5-source.zip` includes the customized Gradle project and
required Opus source. Original Kiumo architecture reused under its MIT license;
libopus license is included in its source directory. JDK and SDK are not bundled.
Set JAVA_HOME to JDK 17 and create local.properties pointing to an SDK containing
platform 36, build tools 36.0.0, NDK 25.1.8937393 and CMake 3.22.1. For a new
debug install, run gradlew.bat :app:assembleDebug. For updates to this release,
use the same original signing key, which is retained locally in work/signing.
The source archive excludes private signing keys, passwords, tokens and local
machine configuration. Release signing expects the key at ../signing/fold5.jks
relative to the project root and FOLD5_STORE_PASSWORD in the process environment.
