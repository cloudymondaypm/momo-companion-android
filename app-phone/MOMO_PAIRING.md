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
- The main screen defaults to **Momo chat**. After pairing, type a message
  and tap **Send to Momo**. Replies come from the dashboard-selected agent.
- The app sends the separately encrypted Momo credential as `X-Device-Token`
  to `https://ai.momolegend.fun/api/device/chat`. The web relay forwards only
  this credential and message to the internal `/v1/device/chat` endpoint.
- Each message is independent: the server currently does not accept chat
  history. The displayed conversation stays in memory only.
- **Xiaozhi voice** remains an explicit fallback tab, with existing preferences,
  device identity, and encrypted bearer credential unchanged. Momo mode never
  opens a Xiaozhi socket or enables microphone/volume-button PTT.
- Invalid/revoked Momo credentials prompt re-pairing; connection/provider errors
  retain the draft for retry. There is no automatic cross-server retry.
- The verification code is displayed in a bold monospace font up to 60sp,
  fitted to narrow screens, with selectable digits and a Copy code action.
- Android binding currently applies only to **app-phone** (Fold 5);
  smartwatch, ESP32 and desktop pairing changes are separate milestones.

## Build

The **Build phone APK** GitHub Actions workflow runs unit tests and produces
the `momo-companion-phone-debug` APK artifact plus test reports. Version 1.3.0
keeps the existing application ID and preference keys. Future debug builds
cache their signing identity; an older APK built with a different signing key
cannot be updated in place. Do not uninstall to work around that if you need
to retain existing local data; use your original signing key instead.

The Momo server must include the new `web/app/api/device/chat/route.ts` relay.
On the existing server checkout, deploy with `./update` (preserves database
volumes and secrets). A missing relay produces an actionable HTTP 404 error;
the Android app does not send device credentials through the console login API.
