# Phone Momo v1.3.1 acceptance

This checklist is pending physical-phone QA. Main and watch builds are unchanged.

- Install the phone APK (Android 10/API 29 or newer; arm64 or x86_64).
- Portrait, landscape, Fold cover screen and unfolded layout: avatar, Play/Dress,
  conversation and Type to Momo composer stay accessible; test with keyboard visible.
- Same watch mint bunny, all six taps, all six swipe areas and long-press cuddles.
- Offline cold start and failed server connection: petting, all 7 outfits, all 7
  accessories and all 4 games work. Style and exactly one star per win survive restart.
- Settings: Connect to my server restores the supplied Spacecloud endpoints;
  no server address typing required. Existing credentials and identity remain local.
- Text: before server hello Send is disabled. Draft remains editable offline.
  After connection, send punctuation, emoji, multiline and non-English text.
  No microphone permission or audio capture is needed; text appears once in history.
  Server replies appear in Conversation.
- Chat/Speak selector saves immediately and survives restart without reconnecting.
  Chat: type and send; replies appear in Conversation with no voice playback or
  microphone permission prompt. Hardware PTT leaves volume controls alone.
  Speak: hold to talk or type, with voice replies and visible chat history.
  Switch to Chat during a spoken reply: audio stops immediately, remaining text
  still arrives. Switch back to Speak: that reply stays silent; the next reply speaks.
- If a server rejects listen/detect text, record the exact server version; the
  Xiaozhi ESP32 server supports this path, but fork-specific compatibility needs QA.
- Voice touch and volume PTT continue to require server hello and microphone permission.
  Opening any play/settings dialog stops PTT and blocks hardware capture.
- Default 2-minute app idle timeout. Test 30s, 1, 2, 5, 10 minutes in main/settings/
  wardrobe/game picker. Animation/network callbacks never restart the timer.
  Background and wake restore brightness; first wake gesture does not record or play.
  Actual hardware sleep is Android-controlled, as in the watch policy.
- Enable 3D depth view in Settings, Save. Confirm same art/costumes/moods with raised
  volume and soft highlights. It is a front-facing 3D relief, not a rotatable full model.
  Check all gesture targets, changes during speech, reduced motion, backgrounding,
  dialogs and idle sleep. Classic graphics remain available; shader failure falls back.
- Observe graphics speed/heat/battery for 15 minutes with 3D enabled and disabled.
- Record source SHA, Android/device build, APK version, install/signature results.

Preview APKs use debug signing. A different signing certificate prevents updating
an older installation. Preserve private settings before any uninstall.

Sources for typed chat and graphics:
https://github.com/xinnan-tech/xiaozhi-esp32-server/blob/main/main/xiaozhi-server/core/handle/textHandler/listenMessageHandler.py
https://developer.android.com/develop/ui/views/graphics/opengl/environment
