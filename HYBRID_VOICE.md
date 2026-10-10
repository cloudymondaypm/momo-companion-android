# Hybrid voice

The phone's **Hybrid voice** screen has a **Hybrid Voice server** dropdown:

- **Momo AI Server** (default): `https://ai.momolegend.fun`. Pair this phone in
  Settings using the Momo dashboard QR or six-digit code. Existing Momo Chat
  pairing is reused; no OTA setup or Xiaozhi bearer is involved.
- **Xiaozhi server** (opt-in): retains the existing secure WebSocket/OTA addresses,
  device ID and separate bearer credentials. Register with Xiaozhi independently.

Only the selected server's settings are visible. Switching cancels microphone
capture, speech, pending connection checks and pending turns before connecting to
that protocol. An already-submitted server operation may still finish remotely;
its result cannot revive the previous screen or trigger speech on the new backend.

Selection is persisted for the current paired Momo device (or the local device
ID before pairing). New pairings default to Momo; returning to an existing paired
device restores its explicit choice. Legacy Xiaozhi settings are preserved but
never silently opt the phone into Xiaozhi. Android Keystore encrypts credentials
in separate protocol/device namespaces; Xiaozhi credentials are additionally
scoped to the WebSocket address. Existing encrypted tokens migrate without
re-pairing. Changing Xiaozhi's address/device restores only that identity's saved
token, or clears the field until its own setup is completed. Momo's origin and
current paired token are explicitly rejected by the Xiaozhi connection path.

**Momo Chat** remains independent of the voice backend dropdown, and always uses
Momo pairing. Changing the voice backend does not redirect Momo Chat to Xiaozhi.

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
A connection check validates Momo's version-1 text hello without sending a
conversation or calling the agent. Ready means the last authentication check
succeeded; each turn opens a new socket and authenticates again. Stored pairing
is separate from endpoint authorization. A voice rejection never changes the
stored-pairing indicator or disables typed chat; retries read the QR token afresh.
Voice and typed-chat errors are displayed separately. A missing route,
wrong protocol, rejected/revoked credential, timeout or lost socket is reported
separately from Xiaozhi setup errors. Typed Momo Chat uses its existing authenticated
HTTP endpoint independently; Hybrid Voice requires the text WebSocket. An HTTP
401/403 while upgrading to voice is reported as a voice-route rejection, not
proof that the QR pairing was revoked. If fresh HTTP chat works but voice is
rejected, check gateway routing and device-token forwarding on the voice route.
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

Phone: `testDebugUnitTest assembleDebug assembleDebugAndroidTest`.
Watch: `testDebugUnitTest assembleDebug`.
UI tests: `connectedDebugAndroidTest` on a connected Android device/emulator.

Regression tests cover protocol-specific headers, hello negotiation, connection
checks without agent turns, revocation, cancellation without stale reconnection,
no automatic resubmission, speech-only fallback routes, bounded replies,
credential migration and per-device isolation. UI tests cover the dropdown at
320dp cover-screen width and mutually exclusive server settings. The test APK
can be built without a connected device; that does not confirm UI tests ran.

Physical validation checklist (not a claim of completed hardware testing):

- Fold5 cover screen and unfolded screen: pair once, select Momo in Hybrid Voice,
  check connection, hold/release PTT, verify transcript/reply and local TTS.
- Repeat with offline models absent or local STT/TTS disabled: verify authenticated
  Momo STT/TTS fallback and meaningful errors if agent speech is disabled.
- Fold/unfold, rotate, background, lose focus, open Settings, switch backends and
  disconnect during recording/recognition/response/TTS: capture and speech stop;
  no stale reply is spoken and no turn is automatically replayed.
- Revoke the paired token: voice requests ask for Momo pairing, never Xiaozhi OTA.
- Select Xiaozhi: original WebSocket/OTA/device ID remain; obtain its own token,
  verify Opus PTT and capability-aware local speech, switch back and restart.
- Kiumo Android 8.1 watch: original server/physical PTT, ARMv7 Opus capture/playback,
  optional offline TTS, missing-engine fallback, avatar and offline play. Watch
  remains on its existing Xiaozhi protocol; it has no Momo pairing selector.

Actual microphone, audio focus, offline models, Fold5 folding and Kiumo engines
require physical-device testing.

Speech fallback HTTP 403 means recognition or synthesis is disabled/denied; it
does not prove device revocation. HTTP 409 means the agent's speech configuration
is incomplete. These failures preserve the authenticated text connection and
permit another hold-to-talk attempt without switching tabs. HTTP 401 still reports
a device authentication rejection. Version 1.4.2 identifies which speech stage
failed. In the Momo dashboard, configure speech recognition/synthesis for the
agent assigned to this device, or enable local phone speech and install its
offline language support. Enabling a local preference cannot provide an engine
or model that the phone does not have.

Deploy the Momo AI Server and companion-server changes before enabling new
features. Momo's compose gateway maps the public WebSocket/speech routes to the
API. TLS/tunnel proxies in front of it must support WebSocket upgrades. No server
deployment, model-download or device installation is performed by these changes.

Android API references:
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine
