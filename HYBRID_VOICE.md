# Hybrid voice

The phone now supports voice in **Momo chat**, using its paired device credential.
Its **Hybrid voice** tab also supports the Xiaozhi companion server. These remain
separate identities and servers; pairing never overwrites the Xiaozhi token.

## Phone / Fold5

In Settings choose English, Filipino / Tagalog, or Taglish; prefer device STT/TTS
independently; select an installed offline voice by name, and adjust local voice
speed/pitch. Taglish uses the Filipino recognition model and a Taglish reply
preference on Momo's new text socket. Install offline language/voice data in the
phone's speech engine settings when available.

On-device recognition uses `createOnDeviceSpeechRecognizer` on Android 12+ only.
It never substitutes a network-backed Android recognizer while claiming local
processing. Devices, languages or engines without this API use server STT.
TTS selects only installed voices that do not require a network connection.

Hold to talk, release to send. If local recognition fails while held, the app
switches to server capture and asks you to repeat. If failure happens after
release, the next hold uses server STT. Android's recognizer owns the mic; this
implementation cannot replay the failed local recording. Leaving the app,
opening settings or losing focus cancels recognition and local speech.

Momo sends text through `/api/device/conversation` with `X-Device-Token`. The
assigned tenant, agent, memory subject and tool permissions remain server owned.
An older server without this route can use the existing HTTP text endpoint.
A submitted turn is never automatically resent after an ambiguous socket loss,
which could otherwise repeat tool actions. Server STT uses bounded temporary
M4A/AAC recordings; server TTS returns MP3 for playback. Cache files are deleted
on completion/cancellation, and no audio is placed in shared storage.

Server fallback requires enabling STT/TTS for the paired agent in the Momo
dashboard. Server voice, pronunciation and external model connections remain
agent settings; local rate/pitch/voice selection apply to device TTS only.
Honcho memory behavior remains conditional on the agent's existing settings.

## Android 8.1 Kiumo watch

The watch keeps server STT and Opus playback by default. Its Settings can opt into
offline device TTS and select English, Tagalog or Taglish. If no matching offline
engine/voice is installed, or the server does not negotiate hybrid support,
server audio continues. Android 8.1 has no guaranteed on-device STT API.
The watch still connects to the existing Xiaozhi companion server, not the paired
Momo API. Physical PTT mapping and ARMv7 support are retained.

## Xiaozhi companion-server compatibility

Hybrid requires an OTA-issued device/client-bound bearer token and a server
advertising `features.hybrid_voice = 1`. Opus format, 16 kHz mono capture and
60 ms frames remain unchanged. A failed local TTS request asks the server to
synthesize the cached reply once, without rerunning the agent/tools. Older
servers and clients keep using the existing audio protocol.

## Validation and deployment

Phone and watch: `testDebugUnitTest assembleDebug`. New socket regression tests
cover paired headers, Taglish, negotiation, HTTP fallback boundaries and no
automatic resubmission. Actual microphone, audio focus, offline models, Fold5
folding and Kiumo engines require physical-device testing.

Deploy the Momo AI Server and companion-server changes before enabling new
features. Momo's compose gateway maps the public WebSocket/speech routes to the
API. TLS/tunnel proxies in front of it must support WebSocket upgrades. No server
deployment, model-download or device installation is performed by these changes.

Android API references:
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine
