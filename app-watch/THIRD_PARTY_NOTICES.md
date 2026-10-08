# Third-party notices

## Xiaozhi Android client

Upstream: https://github.com/jerrygugu/xiaozhi-android

Base commit: e2a026401247f8313262d8fc1e7400dd53fb8e4d

License: MIT. The original copyright and license are preserved in the root `LICENSE` and in `app/src/main/assets/licenses`.

The watch UI, PTT controls, self-hosted setup, native Opus bridge, and Momo avatar are adaptations in this source distribution. The project is not presented as an official Xiaozhi or Kiumo product.

## Opus

Upstream: https://github.com/xiph/opus

Version: v1.5.2; commit ddbe48383984d56acd9e1ab6a090c54ca6b735a6

The bundled source and license notices are under `app/src/main/cpp/opus`, including `COPYING`. An Opus notice is also packaged in the APK's license assets. The dependency's own licenses and source headers are retained.

## Other dependencies

AndroidX / Jetpack Compose, Kotlin, kotlinx.coroutines, OkHttp, Gson, Accompanist, Oboe, and test tools are referenced in the Gradle files and downloaded through their configured repositories. They retain their respective upstream licenses. No dependency signing keys, account credentials, or private server tokens are included.
