# APK builds

GitHub Actions workflows build independent debug APKs for watch and phone. APKs are CI artifacts, not committed source. Public releases must use repository secrets and a private release-signing key; neither uploaded archive contains signing keys.

- Watch module: `app-watch`, Android 8.1/API 26, ARMv7 + ARM64
- Phone module: `app-phone`, Android 10/API 29, ARM64 + x86_64
