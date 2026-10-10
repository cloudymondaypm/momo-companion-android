# Momo AI Server Android binding

This Fold 5 Android app now supports pairing with **Momo AI Server** at
`https://ai.momolegend.fun` via either a QR code or six-digit verification.

The Momo device credential is encrypted on the phone with Android Keystore.
It is **not** the Xiaozhi WebSocket bearer token and does not replace the
existing Xiaozhi connection. The binding grants access to the Momo HTTP
device-chat API; **native Momo voice streaming is not yet implemented**.

## Option A: QR code (dashboard first)

1. Sign in at `https://ai.momolegend.fun`.
2. Open **Companion devices → Add device → Android phone → QR code**.
3. Select the agent, enter a device name, and choose **Generate QR code**.
4. On the phone, open **Momo Companion → Settings → Momo AI Server pairing**.
5. Tap **Scan Momo dashboard QR code** and scan the displayed QR.
6. The app redeems a high-entropy, five-minute, single-use ticket and
   securely stores its device token. The dashboard detects completion.

QR scanning uses Google Code Scanner and requires a working Google Play
services install. The app never sends account login credentials.

## Option B: Six-digit verification (phone first)

1. Open **Momo Companion → Settings → Momo AI Server pairing**.
2. Tap **Show six-digit verification code**.
3. In the signed-in dashboard, open **Companion devices → Add device →
   Android phone → 6-digit verification**.
4. Enter the phone's six-digit code and select your AI agent.
5. Tap **Bind Android device**. The phone securely polls using a
   separate, unguessable credential and stores the assigned token.

The code expires after five minutes. A six-digit code by itself cannot
read or authenticate to any account: a signed-in account administrator
must approve it and choose the agent.

## Security and limitations

- Tokens are stored in a **separate** Android Keystore entry so Xiaozhi
  voice settings and credentials are not overwritten.
- The QR scanner accepts only `momo://pair` payloads for
  `https://ai.momolegend.fun`.
- QR tickets and six-digit codes are short-lived, single-use.
- The server hashes pairing secrets and creates a revocable device token.
- Pairing status is available in Android Settings, but the app's voice
  screen continues to use its existing Xiaozhi server.
- Android binding currently applies only to **app-phone** (Fold 5);
  smartwatch, ESP32 and desktop pairing changes are separate milestones.

## Build

The Gradle module `app-phone` adds Google Code Scanner as a dependency.
Use the existing Android build/CI workflow and Google Play services on
the device for scanning. These changes do not produce a prebuilt APK.
# Universal dashboard and verification code visibility

The embedded pairing server remains `https://ai.momolegend.fun`. The dashboard
now offers QR or six-digit verification for companion apps and verified Xiaozhi
ESP32 activation. This phone continues using its existing request-code/poll/ack
and QR claim routes, including its independent polling secret and Keystore token.
The six digits use an explicit 44sp bold single-line display with 3sp spacing.
The separate Xiaozhi OTA/WebSocket voice settings remain available and are not
replaced by the Momo pairing URL. Momo ESP32 activation does not supply voice.

