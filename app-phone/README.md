# Momo Companion for Galaxy Z Fold5

A private, push-to-talk Android client for a self-hosted Xiaozhi server, with an
original animated plush bunny companion and responsive cover/inner layouts.

## Features

- Android 10+ (API 29); target Android 16 (API 36), ARM64 and x86_64.
- Fold5 cover-screen conversation and wide-screen side-by-side layout.
- Hold-to-talk microphone control; optional focused-app volume-button PTT.
- Momo blinks, bobs, waves and animates its mouth during replies, with nine moods.
- Server emotion messages take priority; local English conversation cues provide
  a simple fallback without another network service. Animation can be disabled.
- Secure WSS/HTTPS, editable server/token/device ID and automatic reconnect.
- No background microphone, wake-word capture, analytics, saved audio or cloud
  backup. Tokens use Android Keystore; conversation history stays in memory.

Defaults point to `wss://xiaozhi.spacecloud.space/xiaozhi/v1/` and
`https://xiaozhi.spacecloud.space/xiaozhi/ota/`. Change them in Settings for
another deployment. No server credentials, signing keys or passwords are bundled.

## Build in Android Studio

1. Open this extracted folder as the project. Select JDK 17 for Gradle.
2. In SDK Manager, install Android SDK platform 36, Build Tools 36.0.0,
   NDK 25.1.8937393 and CMake 3.22.1. Allow Gradle to download its dependencies.
3. Build a debug APK using the IDE or the commands below.

Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A debug build uses
a different signing key from the supplied release APK; Android will not install
it over that release. Keep the original private release key for future updates.

For command-line builds, set JAVA_HOME to JDK 17 and create `local.properties`
with your Android SDK path, or set ANDROID_HOME. Machine-specific files are
excluded from this repository. Use forward slashes in Windows SDK paths.

## Signed release builds

The Gradle release configuration expects `../signing/fold5.jks` relative to
this project root, alias `fold5`, and the environment variable
`FOLD5_STORE_PASSWORD`. These private files are intentionally absent. To use your
own key, change the signing configuration in `app/build.gradle.kts`, and keep
all keys and passwords outside Git. To update the supplied APK, use its original
local signing key and increase versionCode.

```powershell
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

## Upload to GitHub

Extract the source ZIP first. In GitHub Desktop, add this folder as a repository
(create the repository here if prompted), commit its files, then choose Publish
repository. You can select a private or public repository. Keep `.gitignore`;
do not add your signing key, password, token or `local.properties` to Git.
This archive includes source, tests, Gradle wrapper, native Opus source and
licenses, rather than generated build caches or installed SDK/JDK files.

## Spoken personality and language

Open Settings and copy the playful English voice instructions. Paste these into
your self-hosted assistant's role/system prompt and save there. Speech language,
voice and response content are controlled by your server. The app does not
silently change the server prompt. Mood labels are playful visual expressions,
not a psychological assessment of the child.

## Code map

- `app/src/main/kotlin/com/xiaozhi/simple/ui/screen/MomoAvatar.kt`: original
  native Compose Canvas avatar and animation.
- `model/CompanionMood.kt` under the same Kotlin package: emotion and topic mapping.
- `ui/screen/MainScreen.kt`: responsive UI, settings and touch PTT.
- `viewmodel/MainViewModel.kt`: state, microphone ownership and app lifecycle.
- `service/WebSocketService.kt`: Xiaozhi protocol, emotions and reconnection.
- `service/AudioService.kt`: PTT recording and Opus playback.
- `app/src/main/cpp`: JNI codec and complete required libopus source.
- `app/src/test`: protocol and expression tests.

Build, unit tests, Android lint, APK signature and native 16 KB alignment checks
passed locally. No phone/emulator was attached for runtime UI or audio testing.

## License

Android client: MIT, see LICENSE (original attribution retained).
Bundled libopus 1.5.2 has its own license at
`app/src/main/cpp/opus-1.5.2/COPYING`. Momo is an original code-drawn avatar;
no third-party character image assets are required.
