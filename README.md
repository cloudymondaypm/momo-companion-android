# Momo Companion Android

Android clients for **Momo Companion Server**, adapted from the MIT-licensed Xiaozhi Android client.

## Repository layout

- `app-watch/` — Kiumo ZH23-YL-RF, Android 8.1, compact-screen UI
- `app-phone/` — Samsung Galaxy Z Fold5, responsive phone/foldable UI
- `core-momo/` — planned shared WebSocket, audio, authentication, and reconnection boundary
- `core-ai/` — planned shared language, Tagalog, and conversation-logic boundary
- `builds/` — APK build and release documentation
- `.github/workflows/` — separate watch and phone APK builds

Each application remains an independent Gradle project to preserve the supplied, previously validated source layout. Historical internal Android namespaces and package IDs are retained for installed-app upgrade compatibility; visible branding is Momo Companion.

## Build locally

Use JDK 17 and the Android SDK components listed in each app README:

```bash
./gradlew -p app-watch assembleDebug testDebugUnitTest
./gradlew -p app-phone assembleDebug testDebugUnitTest
```

## Licensing

Retain the root and application license/notice files. Momo Companion is not endorsed by the original Xiaozhi authors. See each app's `LICENSE`, bundled codec licenses, and third-party notices.
