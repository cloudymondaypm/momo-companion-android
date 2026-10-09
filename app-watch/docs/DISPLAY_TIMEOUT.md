# Watch display idle timeout

Momo v0.4.5 defaults to 120 seconds of inactivity. Watch Settings offers 30 seconds,
1, 2, 5 and 10 minutes. The choice persists separately from Xiaozhi configuration
and takes effect immediately; changing it never reconnects the server.

Touch and delivered hardware input reset a main-thread Handler timer in every app
window, including dialogs. Animation and network callbacks do not reset it.
On expiry a black full-screen dialog covers Momo, brightness is set to the window
minimum, microphone capture ends, and KEEP_SCREEN_ON is removed from all app windows.
The wake gesture is consumed so it cannot play a move or start talking.
App backgrounding cancels the timer and restores the original per-window brightness.

Android 8.1 public APIs do not grant an ordinary app an exact physical panel sleep
deadline. Brightness 0 means the panel's minimum, which may not switch off its
backlight. The OS global screen timeout controls physical sleep after the app releases
its screen-on flags. No WRITE_SETTINGS permission, global Settings writes, hidden
userActivityTimeout API, device-admin lock or wake lock is used.

API references:
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/develop/background-work/background-tasks/awake/screen-on

Offline petting, wardrobe and all four games use on-device state only.
Talk is available only after the Xiaozhi connection is Connected, and additionally
requires microphone permission. Saving Settings does not implicitly connect; use
Connect for server changes. Physical watch QA is required before making PR #2 ready.
