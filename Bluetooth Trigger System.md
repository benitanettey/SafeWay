---
tags:
  - bluetooth
  - emergency
  - proposal
  - trigger
  - hackathon
---
# Shield / SafeWay Bluetooth Emergency Trigger System

> **Source:** `Shield_Safeway_Bluetooth_Trigger_System.docx`
> **Status:** ✅ **IMPLEMENTED & AUDITED** — MVP live in codebase, all audit findings resolved. AirPods trigger reliability fixes applied 2026-05-19. Silent audio stabilization, false trigger filtering, SOS audible feedback, and media-aware gesture suppression added 2026-05-20.

## Project Goal

Build a system that allows users experiencing danger to discreetly trigger emergency actions using **Bluetooth headsets or beacons** while the app runs in the background.

**Triggered actions:**
- Send SOS alerts
- Share live location
- Start hidden audio recording
- Wake the phone screen silently
- Notify trusted contacts
- Upload evidence securely

## Core Architecture

```
Earbud Button → Android Media Event → Background Service → App Action
```

### Key Insight

Raw tap gestures from AirPods/earbuds are **NOT** directly exposed to Android apps. Earbuds process gestures internally (double tap = play/pause, triple tap = next, long press = assistant). Android apps receive **Media Button Events** rather than raw gesture data.

### Recommended MVP Strategy

Instead of low-level Bluetooth packet reading:
- `MediaSessionCompat`
- `ACTION_MEDIA_BUTTON` BroadcastReceiver
- Foreground Service
- Bluetooth connection monitoring
- Gesture timing logic

## Gesture Detection Logic

| Trigger | Action | Timing | Device Support |
|---|---:|---:|---|
| Double press | Start recording | Within 800ms | Universal — works on AirPods and all headsets |
| Slow double press | Send SOS | Click, wait ~3s, click again (1-10s window, widened from 1.5-5s after user testing on Infinix) | Universal — works on AirPods and all headsets |
| Triple press | SOS (AVRCP only — MEDIA_PREVIOUS) | Dedicated AVRCP route, no timing window | AirPods, headsets with AVRCP skip/previous |

### Pseudo Logic
1. Store timestamp of each press
2. Increment press counter
3. Reset counter after timeout
4. Trigger action when threshold reached

### Preventing Music Conflicts
- **Do NOT use single press** — conflicts with Spotify, YouTube, etc.
- Alternative: intercept assistant-trigger events from earbuds

## Proposed Screens

| Screen | Purpose |
|---|---|
| Onboarding | Explain functionality, request permissions |
| Bluetooth Pairing | Detect connected earbuds, store selected device |
| Gesture Mapping | Configure triple press → SOS, long hold → recording |
| Trusted Contacts | Partner, friend, lawyer, emergency contacts |
| Protection Service | Start/stop protection mode, status, battery warning |
| Emergency History | SOS history, recordings, trigger logs |

## Implemented Architecture

```
com.example.safeway/
├── domain/
│   ├── GestureDetector.kt    # Timing-based gesture recognition
│   ├── ProtectionPrefs.kt    # SharedPreferences for BT settings
│   └── TriggerEvent.kt       # Trigger history data model
├── receiver/
│   ├── MediaButtonReceiver.kt # ACTION_MEDIA_BUTTON interceptor
│   └── BootReceiver.kt       # Auto-start on device boot
├── service/
│   └── ProtectionForegroundService.kt  # Core service with MediaSession
└── protection/
    └── ProtectionStatusActivity.kt     # Setup, status, and history UI
```

### Key Components

**GestureDetector** — Monitors press timestamps to detect:
- **Double press** within 800ms → configurable action (default recording)
- **Slow double press** — click, wait ~3s, click again (1-10s window) → configurable action (default SOS). Arms with a short vibration so the user knows the first tap registered
- **Triple press** via AVRCP (MEDIA_PREVIOUS) → configurable action (default SOS)

**ProtectionForegroundService** — Runs as a foreground service:
- Holds a `MediaSessionCompat` to claim media button focus
- Dynamically registers `MediaButtonReceiver` for `ACTION_MEDIA_BUTTON`
- Monitors connected BT audio devices via `AudioManager.AudioDeviceCallback`
- Shows persistent notification with device status
- Routes detected gestures to `EmergencyAlertDispatcher` or `LogIncidentActivity`

**ProtectionStatusActivity** — Configuration and status:
- Activate/deactivate protection toggle
- Shows connected BT device name
- Configurable gesture → action mapping (tap to change)
- Trigger history log (last 50 events)
- Instructions card for first-time users

## Bluetooth Detection

APIs to use:
- `BluetoothAdapter` + `BluetoothProfile`
- `BluetoothProfile.ServiceListener`
- `AudioManager` + `AudioDeviceCallback`

Goals: detect connected earbuds, detect disconnection, monitor audio routing.

## Background Service Requirements

Android aggressively kills background apps, so:
- **Foreground Service** (persistent notification required)
- Request battery optimization exemption via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Auto-start on boot via `RECEIVE_BOOT_COMPLETED`

## Emergency Execution Flow

When trigger detected:
1. Wake screen silently
2. Vibrate device
3. Show minimal notification ("Shield Active" — avoid sensitive text on lockscreen)
4. Start recording (encrypted, rolling storage)
5. Send SOS
6. Share location
7. Auto-upload evidence when internet available

## Permissions Required

| Permission | Purpose |
|---|---|
| `BLUETOOTH` | Device detection |
| `BLUETOOTH_CONNECT` | Android 12+ BT connection |
| `BLUETOOTH_SCAN` | Android 12+ BT scanning |
| `FOREGROUND_SERVICE` | Background operation |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | API 34+ required for `mediaPlayback` foreground service type (AVRCP routing for AirPods) |
| `FOREGROUND_SERVICE_DATA_SYNC` | API 34+ required for `dataSync` foreground service type |
| `RECORD_AUDIO` | Hidden recording |
| `ACCESS_FINE_LOCATION` | GPS sharing |
| `POST_NOTIFICATIONS` | Android 13+ notifications |
| `WAKE_LOCK` | Screen wake |
| `RECEIVE_BOOT_COMPLETED` | Auto-start |

## Backend Options

| Option | Use Case |
|---|---|
| Firebase | Fastest for hackathon MVP |
| AWS (API Gateway + Lambda + DynamoDB + SNS) | Production |

Emergency payload:
```json
{
  "userId": "...",
  "timestamp": "...",
  "location": "...",
  "recordingActive": true
}
```

## Recommended Tech Stack

- **Language:** Kotlin
- **Async:** Coroutines
- **Media:** MediaSessionCompat
- **Services:** Foreground Services, WorkManager
- **Storage:** Room
- **Architecture:** MVVM + Repository Pattern + Clean Architecture

## Risks

| Risk | Mitigation |
|---|---|
| Earbud compatibility varies | Test with specific models |
| Android fragmentation | Version-specific handling |
| Background restrictions (Android 13+) | Foreground service + battery exemption |
| Audio conflicts with music apps | Avoid single-press triggers |

## Audit Findings (2026-05-19)

A rigorous 12-phase structured audit identified and resolved 5 bugs:

| # | Severity | Bug | Fix |
|---|---|---|---|
| 1 | **CRITICAL** | Service ANR on Android 8+ — `START_STICKY` restarts service with null intent, `startForeground()` never called, system kills process after ~5s | Added `isForeground` flag with unconditional fallback `startForeground()` in `onStartCommand` |
| 2 | **HIGH** | Notification exposed sensitive text ("BT Protection active" + device name) on lockscreen | Changed to generic "Protected mode active" text + `VISIBILITY_PRIVATE` |
| 3 | **MEDIUM** | `updateOngoingNotification()` gated on `TIRAMISU+`, leaving API 24-32 with stale BT device info | Removed SDK check, wrapped in `try/catch` instead |
| 4 | **MEDIUM** | `GestureDetector` used `System.currentTimeMillis()` for timing — vulnerable to NTP/timezone changes | Migrated to `SystemClock.elapsedRealtime()` |
| 5 | **MEDIUM** | `MediaButtonReceiver` declared with `android.permission.SYSTEM_ALERT_WINDOW` (blocking broadcast delivery) + used deprecated `startService()` on O+ | Removed incorrect permission, uses `startForegroundService()` on API 26+ |

All fixes verified via `compileDebugKotlin` — zero new warnings introduced.

## Root Cause Analysis: AirPods Not Triggering (2026-05-19)

After auditing the entire Bluetooth trigger pipeline and accessibility service, **6 root causes** were identified across 2 issue categories:

### Category A: AirPods Button Presses Not Triggering Actions

| # | Severity | Root Cause | Fix | File |
|:---:|:---:|---|---|---|
| 1 | **CRITICAL** | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission missing — Android 14+ silently strips `mediaPlayback` foreground service type, so `MediaSession` never gets AVRCP routing priority | Added `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />` | `AndroidManifest.xml` |
| 2 | **HIGH** | No `OnAudioFocusChangeListener` — when Spotify takes audio focus, focus is never re-requested, so all AVRCP events route to Spotify instead of SafeWay | Implemented `focusChangeListener` with 500ms delayed re-request on focus loss | `ProtectionForegroundService.kt` |
| 3 | **MEDIUM** | `AUDIOFOCUS_GAIN` requests exclusive focus — pauses Spotify entirely when SafeWay is active, which users don't want | Changed to `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` so Spotify continues playing at reduced volume | `ProtectionForegroundService.kt` |
| 4 | **MEDIUM** | `MODE_STATIC` silent audio track may not keep AVRCP routing active across all devices/Android versions | *Deferred* — try `MODE_STREAM` with periodic silence writes if primary fixes don't resolve | `ProtectionForegroundService.kt` |
| 5 | **LOW** | `GestureDetector.onKeyDown()` fires `DOUBLE_PRESS` immediately at 2 presses, preventing triple-press from ever being detected | Added 400ms debounce delay: at 2 presses, wait 400ms before committing to `DOUBLE_PRESS`; if a third press arrives, cancel debounce and fire `TRIPLE_PRESS` | `GestureDetector.kt` |

### Category B: Accessibility Service Unavailable

| # | Severity | Root Cause | Fix | Location |
|:---:|:---:|---|---|---|
| 1 | **HIGH** | Android 13+ "Restricted Settings" blocks sideloaded apps from enabling Accessibility Services | **Manual:** Settings → Apps → SafeWay → ⋮ menu → "Allow restricted settings" (must repeat after every debug reinstall) | Phone settings |
| 2 | **HIGH** | `accessibility_service_config.xml` missing `flagRequestFilterKeyEvents` and `canRequestFilterKeyEvents` — `onKeyEvent()` is never called by the system regardless of programmatic flag setting | Added `|flagRequestFilterKeyEvents` to `accessibilityFlags` + `android:canRequestFilterKeyEvents="true"` to XML | `accessibility_service_config.xml` |

### Testing After Fixes

Test with logcat filter:
```
adb logcat -s ShieldBT.Broadcast ShieldBT.Service ShieldBT.Gesture ShieldBT.Prefs ShieldBT.UI ShieldBT.Accessibility
```

**Test sequence:**
1. Connect AirPods
2. Start Spotify playing a song
3. Enable SafeWay protection
4. Double-tap your AirPod
5. Check logcat for:
   - `MediaSession.onMediaButtonEvent received` OR `onKeyEvent: action=...`
   - `AVRCP MEDIA_NEXT → DOUBLE_PRESS`
   - `GESTURE TRIGGERED: DOUBLE_PRESS → ...`

If `MediaSession.onMediaButtonEvent` is not logged but Spotify skips track, then audio focus re-acquisition (Fix #2) is not working correctly.

## User Experience Improvements (2026-05-19)

Following user testing, the following gaps were identified and fixed:

| # | Issue | Fix |
|---|---|---|
| 1 | Runtime permissions (BLUETOOTH_CONNECT, BLUETOOTH_SCAN, POST_NOTIFICATIONS) never requested | Added runtime permission requests via `ActivityResultContracts.RequestMultiplePermissions` in `ProtectionStatusActivity` with dedicated permissions card in UI |
| 2 | BT device status only refreshes on activity resume or manual button tap | Added 2-second Handler-based polling loop in `ProtectionStatusActivity` (active in onResume, stopped in onPause) |
| 3 | No user feedback when gesture triggers (silent execution) | Added haptic feedback pattern (3-buzz vibration) via `Vibrator` in `ProtectionForegroundService.handleGesture()` |
| 4 | Long hold threshold too short (1.5s) | Changed to **3 seconds** in `GestureDetector.LONG_PRESS_THRESHOLD_MS` |
| 5 | Instructions unclear about earbud compatibility | Updated instructions to note that AirPods handle gestures internally and may not forward events to the app |
| 6 | No permission status visibility | Added permissions card showing each required permission with Granted/Denied status + "Grant All" button |

### Round 3: Background Recording & Debug Fixes (2026-05-19)

| # | Issue | Fix |
|---|---|---|
| 12 | AirPods double-tap sent vendor-specific key code 354 instead of 87 — accessibility service filtered it out | Added `KEYCODE_TRANSSION_BT_BUTTON = 354` to accepted codes in `ProtectionAccessibilityService.onKeyEvent()` |
| 13 | `START_RECORDING` opened `LogIncidentActivity` immediately — useless when phone is locked/in another room | Reworked to toggle **background recording** in `ProtectionForegroundService` — double-tap starts/stops recording silently; draft saved to `ProtectionPrefs` |
| 14 | No way to see background recording draft after it's done | `LogIncidentActivity.onCreate()` now checks `ProtectionPrefs.getPendingVoiceNotePath()` and loads the draft into the UI |
| 15 | No feedback when screen is locked (user can't see toasts) | Added `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` in `startBackgroundRecording()` to wake screen briefly |
| 16 | Notification shows same text regardless of recording state | Notification updates to "🔴 Recording…" with high priority when recording, reverts to normal when stopped |

### Round 4: Feedback & Draft Persistence Audit (2026-05-19)

| # | Issue | Fix |
|---|---|---|
| 17 | No auditory feedback when gesture triggers — user can't hear recording start/stop | Added `playFeedbackTone()` generating sine wave via AudioTrack — ascending 800→1200Hz for double-tap, lower ascending 600→900Hz for triple-tap, urgent rising 500→1500Hz for slow-double-press |
| 18 | Screen wake only for START_RECORDING gesture, not SOS or share location | Moved `wakeScreen()` from `startBackgroundRecording()` to `handleGesture()` — fires for ALL gesture types via the same entry point |
| 19 | Form fields (description, location, type, severity, photo, video) all lost on exit — only voice note was persisted via ProtectionPrefs | Added `saveDraft()`/`loadDraft()`/`clearDraft()` using separate SharedPreferences (`incident_draft`) in `LogIncidentActivity`. Saves entire form state on `onPause()`, restores on `onCreate()`, clears on successful `saveIncident()` |
| 20 | Back button exits without warning even with unsaved form data | Added `AlertDialog` "Discard draft?" confirmation when `hasUnsavedData()` returns true — offers "Discard" (clears draft + finishes) or "Keep editing" |
| 21 | Service may not restart after process death on Transsion XOS (START_STICKY often suppressed by OEM battery manager) | Added `ensureRunning()` companion method to `ProtectionForegroundService` — checks `ProtectionPrefs.isEnabled()` and calls `start()` if needed. Called from `SplashActivity.onCreate()` and `HomeActivity.onCreate()` so service restarts whenever user opens the app |
| 22 | Background recording failure gives zero user feedback (Toast + notification update inside try block, never reached on error) | `playFeedbackTone()` and `wakeScreen()` fire in `handleGesture()` BEFORE `executeAction()`, so user always gets audio + vibration + screen wake even if recording fails to start |

### Round 5: Phantom Gestures, Auto-Refresh, UI & Permissions (2026-05-19)

| # | Issue | Fix |
|---|---|---|
| 23 | Emojis in Toast messages and notification text look unprofessional | Removed all emojis from Toasts and notification content text |
| 24 | BT disconnect triggers phantom double-press/long-hold gestures — audio stack emits spurious key events | Added `phantomGestureCooldownUntil` (3s cooldown after BT disconnect) + `gestureDetector.reset()` on disconnect. `processKeyEvent()` and `handleGesture()` both check cooldown before processing events |
| 25 | LogIncidentActivity doesn't update when background recording stops via double-tap — user must exit and re-enter to see the waveform | Added 1s auto-refresh polling in LogIncidentActivity that detects `ProtectionForegroundService.isBackgroundRecording` transition and auto-loads pending voice notes with zero UI navigation |
| 26 | Trigger history grows unbounded with no fixed height — pushes other UI off the page | Wrapped history container in `NestedScrollView` with 200dp fixed height and vertical scrollbar; shows/hides properly when history is empty |
| 27 | "Clear" and "Refresh" button text cut off at bottom — 30dp height insufficient with system button padding | Increased buttons to 36dp with `minHeight="0dp"` and `gravity="center"` to prevent text clipping |
| 28 | No proactive permission notification on home screen — user must navigate to BT Protection page to see missing permissions | Added permissions card to HomeActivity showing all missing permissions (RECORD_AUDIO, CAMERA, ACCESS_FINE_LOCATION, SEND_SMS, POST_NOTIFICATIONS, BLUETOOTH_CONNECT, BLUETOOTH_SCAN, overlay, accessibility) with red/missing status and "Grant All" button that chains runtime permission requests → overlay settings → accessibility settings |
| 29 | SafeWay holds AUDIOFOCUS_GAIN permanently, pausing any media playback (YouTube, Spotify, etc.) while protection is active | Replaced permanent `AUDIOFOCUS_GAIN` with `requestTemporaryAudioFocus()` — only grabs focus for 30 seconds after a gesture is detected. `focusChangeListener` re-acquisition limited to 3 attempts with exponential backoff (500ms/1s/2s) to prevent infinite tug-of-war. Audio focus is abandoned via `abandonAudioFocusRequest` after the 30s window expires, letting media resume normally |
| 30 | Phantom code=354 events triggered by system navigation gestures (app switching) on Transsion/Infinix even with no earbuds connected | Added `isConnectedToBtDevice` guard in ProtectionForegroundService.processKeyEvent() — code=354 is ignored entirely unless a BT audio device is connected. Same check added in ProtectionAccessibilityService.onKeyEvent() to prevent forwarding phantom events |

### Updated Background Recording Flow

1. Double-tap AirPods → **ascending beep** + **screen wakes** + **3-buzz vibration** + notification "Recording…" + background recording starts silently
   - Tone: 800→1200Hz sine wave, 250ms, via AudioTrack daemon thread (plays before recording starts, so user always hears it)
   - Screen: `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP`, 2s
2. Double-tap again → **descending tone** + **screen wakes** + **vibration** + Toast "Recording saved — open Log Incident to finish"
   - Tone: 800→400Hz sine wave, 250ms
   - File path saved to `ProtectionPrefs` as pending draft
3. Open SafeWay → Log Incident tab → draft loaded automatically
   - Voice note shows "Voice note saved — Encrypted • 0:30"
   - All previously filled form fields (description, location, type, severity, photos, videos) restored from `incident_draft` SharedPreferences
4. Fill in description/location/type/severity → tap **Save** → incident stored in Room DB, draft cleared
5. Form data persists even if you exit the app without saving:
   - Exit and reopen → voice note + all text + attachments restored
   - Process death and reopen → same recovery via prefs
   - Back button shows "Discard draft?" confirmation dialog

### Round 2 UX Fixes (2026-05-19)

| # | Issue | Fix |
|---|---|---|
| 7 | Gesture detection not working when no media app is playing — AVRCP commands dropped by system | Moved AUDIOFOCUS_GAIN to on-demand (30s window) + silent audio MODE_STREAM keeps AVRCP routing active |
| 8 | No visible feedback when gesture is detected (user can't tell if app heard the tap) | Added `Toast` in `handleGesture()` showing "GESTURE → Action" |
| 9 | No visible feedback when Accessibility Service intercepts a key event | Added `Toast` in `ProtectionAccessibilityService.onKeyEvent()` showing "Accessibility: key=XX" |
| 10 | MODE_STATIC silent audio unreliable across devices/OEMs | Switched to `MODE_STREAM` with periodic silence writes (every 500ms) to keep AVRCP routing active |
| 11 | AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK doesn't override Spotify/YouTube GAIN focus — AVRCP events still routed to media app | Changed to `AUDIOFOCUS_GAIN` so SafeWay holds media button priority. Trade-off: media apps pause when SafeWay has focus |

### Round 6: Long Hold Removed, Slow Double Press Stabilized, Toast Crash Fix (2026-05-19)

| # | Issue | Fix |
|---|---|---|
| 31 | LONG_HOLD gesture fires 3s after any single click because processKeyEvent() filters ACTION_UP — onKeyUp() never runs, so isLongPressCandidate stays true permanently | Removed LONG_HOLD entirely: deleted from GestureType enum, removed all long-hold timer logic from GestureDetector, removed getLongHoldAction/setLongHoldAction from ProtectionPrefs, removed config_long_hold UI row from ProtectionStatusActivity and layout, removed long_hold string references |
| 32 | Toast crashes with "Can't toast on a thread that has not called Looper.prepare()" — EmergencyAlertDispatcher callback runs on Dispatchers.IO | Wrapped Toast calls in executeAction() with `Handler(Looper.getMainLooper()).post { ... }` |
| 33 | Slow-double description text overlaps with action label in gesture mapping card | Added `android:layout_marginEnd="8dp"` to description container in activity_protection_status.xml |
| 34 | Long hold mentioned in bt_instructions and gesture_compat_note despite being removed from UI | Removed all long-hold references from strings.xml |

### Round 7: SOS Location Timeout, Slow-Double Vibration Feedback (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 35 | SOS takes 20-30s on Infinix Smart 9 because `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` has no timeout — GPS chip struggles indoors | Changed `fetchLocation()` to use `lastLocation` as instant fallback + parallel `getCurrentLocation` with 5s timeout. SOS never waits more than 5s, and gets fresh GPS fix if available |
| 36 | No feedback when slow-double timer arms on single tap — user doesn't know the first tap registered, taps again out of confusion, triggering unintended SOS | Added `onSlowDoubleArmed` callback in GestureDetector that fires a 50ms vibration on single tap. New `vibrateShort()` method in ProtectionForegroundService |
| 37 | No confirmation that SOS is in progress during GPS location fetch | Added "📍 Sending SOS…" / "📍 Sharing location…" Toast in `executeAction()` before dispatch starts |
| 38 | Slow-double window still too tight — user's taps were 4.9s and 7.9s apart, barely within the 5s window | Window already widened from 1.5-5s to 1-10s in Round 6 (verified: 7949ms gaps now succeed) |

### Round 8: Silent Audio Underrun Fix, False Trigger Filtering, SOS Audible Feedback (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 39 | **CRITICAL** — Silent audio track underrun kills AVRCP routing. AudioTrack buffer was only `minBufferSize.coerceAtLeast(4096)` (~214ms at 44100Hz), but writes happened every 500ms. The buffer drained between writes, the track was disabled by Android, and the system stopped routing AVRCP events — the second tap of slow-double-press never arrived at the app. Root cause of "slow double press doesn't work" after Round 7. | Increased buffer to 1 second (`sampleRate * 2` = 88200 bytes). Changed write frequency from 500ms to 100ms. Changed write chunk to 1764 bytes (20ms of audio). Added auto-restart: if `playState != PLAYSTATE_PLAYING`, call `track.play()` on every write cycle. This keeps the buffer always full and the track active. |
| 40 | **HIGH** — False triggers during normal phone use. Spurious media button events generated by system navigation gestures, USB plug/unplug, and other UI interactions were being routed to the gesture detector as real tap events — causing phantom DOUBLE_PRESS triggers with no earbuds connected. | Added audio device connection check in `processKeyEvent()` for `KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`: only forward to gesture detector if `isConnectedToBtDevice || isWiredHeadsetConnected()`. Added `isWiredHeadsetConnected()` helper checking for `TYPE_WIRED_HEADSET`, `TYPE_WIRED_HEADPHONES`, `TYPE_USB_HEADSET`, `TYPE_USB_DEVICE`. Existing code=354 guard already checked BT connection. |
| 41 | **HIGH** — No feedback when SOS is dispatched with phone across the room. Toasts are invisible on a locked phone. User can't tell if SOS is actually being sent or has completed. | Added `shield_sos_alerts` notification channel (`IMPORTANCE_HIGH`, sound, vibration, bypassDnd, VISIBILITY_PUBLIC). `showSosAlertNotification()` posts a heads-up alert "Sending SOS..." during dispatch, updated to "Done: message" on completion, auto-dismissed after 10s. `playSosLoopTone()` generates a two-tone siren (880Hz/1100Hz alternating, 500ms per tone, 3 repeats) through USAGE_ALARM so it's audible from across the room. |
| 42 | **MEDIUM** — `requestTemporaryAudioFocus()` in `onSlowDoubleArmed` callback was grabbing `AUDIOFOCUS_GAIN` on every single tap, interfering with AVRCP routing on Transsion/Infinix devices. The second tap of slow-double-press was routed to the wrong app because audio focus changed too early. | Removed `requestTemporaryAudioFocus()` from `onSlowDoubleArmed` callback entirely. Audio focus is now only grabbed in `handleGesture()` — after a gesture is fully confirmed, not during the arming phase. The silent audio track keeps MediaSession active for routing without needing focus. |
| 43 | **LOW** — Cross-gesture debounce (1.5s `lastAnyGestureTime` check) was too aggressive. When both the AccessibilityService code=354 path and the AVRCP path fired for the same tap, the second path was blocked — but the same-type debounce (same gesture within 1s) is sufficient for dual-path scenarios. | Removed `lastAnyGestureTime` field and its 1.5s check entirely. Only same-type debounce remains (same `GestureType` within 1s). |
| 44 | **LOW** — Emojis in Toast and notification text look unprofessional. User explicitly requested no emojis. | Removed all emojis from Toast messages, notification text, and content strings. "Sending SOS..." is plain text. |
| 45 | **MEDIUM** — Safety gestures fire even when the user is actively watching YouTube, listening to Spotify, or playing a video in the gallery. The earbud tap intended to skip a track instead triggers SOS or recording. | Added `isOtherAppPlayingAudio()` check in `handleGesture()`: uses `AudioManager.getActivePlaybackConfigurations()` (API 26+) to count active USAGE_MEDIA/USAGE_GAME audio players. Our silent audio track creates one; if there's more than one, another app is playing audio and the gesture is suppressed with log "another app is playing audio — suppressing". On API 24-25 the check is skipped (no `getActivePlaybackConfigurations`). |

### Round 9: Background Thread for Silent Audio, SLOW_DOUBLE_PRESS Excluded from Media Suppression (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 46 | **CRITICAL** — Silent audio write loop runs on the main thread Handler. When the main thread is busy with UI work (layout, inflation), `postDelayed(this, 50)` fires late — sometimes 300ms+ late. The 1s buffer drains between delayed writes, the AudioTrack is disabled by the system (`restartIfDisabled` every ~310ms), and AVRCP routing becomes unreliable. Even though the gesture detector recognized SLOW_DOUBLE_PRESS, the second click sometimes didn't arrive because the audio stack was in a constant restart cycle. | Moved silent audio to a dedicated `HandlerThread` with `Process.THREAD_PRIORITY_AUDIO`. `writeSilence()` posts on this thread's looper instead of the main looper, so write timing is consistent regardless of UI load. Pre-fill of the 1s buffer happens before `play()` to prevent initial underrun. Added return-value check on `track.write()` — if it returns negative (ERROR_DEAD_OBJECT from a disabled track), call `track.play()` to restart. |
| 47 | **HIGH** — SLOW_DOUBLE_PRESS was suppressed by `isOtherAppPlayingAudio()` when the user had audio playing through their earbuds. The media-aware suppression feature treats ANY gesture as potentially accidental, but SLOW_DOUBLE_PRESS requires the user to click, wait 3-10 seconds, and click again — this pattern cannot be accidental. The user reported "second click not detected" because the gesture was recognized but silently dropped. | Excluded `GestureType.SLOW_DOUBLE_PRESS` from the media suppression check in `handleGesture()`. Only DOUBLE_PRESS and TRIPLE_PRESS (which can arrive as AVRCP next/previous commands during media playback) are suppressed when another app is playing audio. |
| 48 | **HIGH** — Second earbud press of slow-double doesn't arrive at SafeWay. The system routes the second press to a different component (or drops it) because SafeWay doesn't hold audio focus. The first press arrives via the MediaSession callback, but without focus, Android routes subsequent media button events to the current foreground media app. | Added `requestSlowDoubleAudioFocus()` in `onSlowDoubleArmed` — requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` for the duration of the 10s slow-double window. This ducks other apps' volume (doesn't pause them) but gives SafeWay media button priority so the second press routes here. Auto-releases after 12s via `focusTimerHandler`. Previously audio focus in onSlowDoubleArmed was removed (Round 8, #42) because it broke AVRCP on Transsion when the silent audio was underrunning. Now that the silent audio is stable on a dedicated thread (Round 9, #46), the focus handshake works correctly. |

### Round 10: AVRCP PlaybackState Stay-PAUSED + AUDIOFOCUS_GAIN_TRANSIENT (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 49 | **HIGH** — Second press of slow-double sequence never arrives at SafeWay. Diagnostic dump confirmed: BT connected (Harmonics twins mini), MediaSession active, AudioTrack PLAYING, 5 audio devices, 1 active playback config (our silent audio), focus held. No second key event of ANY code arrives during the 10s window. Root cause: Harmonics earbuds implement a play/pause toggle in firmware. They send `KEYCODE_MEDIA_PAUSE` (127) for the first press, toggle internal state to "paused", and then refuse to send another `MEDIA_PAUSE` because they think the phone is already paused. Our AVRCP PlaybackState stays `STATE_PLAYING` continuously, so the earbuds never observe a state change to reset their toggle. | Changed approach from "toggle back to PLAYING after 300ms" (which briefly reset the earbud state machine but left a cooldown period) to "keep PAUSED until gesture detected or window expires." `setAvrcpStateToPaused()` sets PlaybackState to `STATE_PAUSED` after the first press and keeps it there. The earbud's internal state stays "paused" and accepts the next press as a fresh MEDIA_PLAY/MEDIA_PAUSE event. `restoreAvrcpStateToPlaying()` is called in `handleGesture()` on any gesture detection, or in the 12s focus release timer if the window expires. Also restored audio focus in `onSlowDoubleArmed`, but using `AUDIOFOCUS_GAIN_TRANSIENT` (via a separate `transientAudioFocusRequest`) instead of `AUDIOFOCUS_GAIN` — transient focus gives routing priority during the slow-double window but doesn't block other apps (YouTube, Instagram, Spotify) when the user switches to them. The AVRCP PAUSED state is the real fix for the earbud firmware; audio focus is only needed for routing priority. **Result**: Verified working — second press arrived 3462ms later and SLOW_DOUBLE_PRESS was detected, triggering SOS. |
| 50 | **LOW** — No visibility into system state when slow-double window expires. | Added unconditional key event logging at the top of `processKeyEvent()` logging every ACTION_DOWN with code, hex, timestamp, device name, and source before any filtering. Added `dumpSlowDoubleDiagnostics()` called 10s after slow-double arms. |

### Round 11: isOtherAppPlayingAudio False Positive After Gesture Execution (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 51 | **MEDIUM** — After any gesture fires (SOS, recording, etc.), subsequent DOUBLE_PRESS gestures are falsely suppressed by `isOtherAppPlayingAudio()`. The user reported "double clicking stopped working" after testing SOS. Root cause: after `handleGesture()` calls `executeAction()`, the system may report transient active playback configs from our own notification sound or siren tones. On Transsion/Infinix (API 34), `isMusicActive()` continues returning true for minutes after our audio stops — the silent audio pause/100ms-sleep/`isMusicActive()` approach in `isOtherAppPlayingAudio()` false-positives. | Added `focusHeldTemporarily` gate in the suppression check: `!focusHeldTemporarily && isOtherAppPlayingAudio()`. When `handleGesture()` grabs `AUDIOFOCUS_GAIN` via `requestTemporaryAudioFocus()`, no other media app can be actively playing (they would have lost focus). So the `isOtherAppPlayingAudio()` check is entirely skipped during the 30s focus window after any gesture, preventing false positive suppression of follow-up gestures. |

### Round 12: Global Gesture Cooldown + BT Reconnect Phantom Suppression (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 52 | **HIGH** — Phantom gesture execution without button presses. After a gesture fires, `restoreAvrcpStateToPlaying()` + `requestTemporaryAudioFocus()` can cause earbuds to re-send button events (AVRCP feedback loop). The new `isOtherAppPlayingAudio()` correctly returns false (no other app playing), so these phantom events are no longer accidentally blocked by the suppression gate. User reports "i havent touched the buttons but my phone is still executing commands" — Toasts appear showing gesture actions with no user input. | Added `gestureCooldownUntil` — a global 3s cooldown set immediately after any gesture is confirmed, BEFORE restoring AVRCP state or grabbing focus. Any gesture arriving within the cooldown window is suppressed. This breaks the AVRCP feedback loop at the earliest possible point. |
| 53 | **MEDIUM** — BT reconnect on Transsion/Infinix fires phantom code=354 events during headset handshake. These arrive while `isConnectedToBtDevice` is true and pass through all existing guards. The old `isOtherAppPlayingAudio()` was incorrectly returning true on Transsion (false positive from `isMusicActive()`), which accidentally suppressed these events. Now the detection is correct, the phantom events execute. | Added `transsionReconnectCooldownUntil` — set when `updateConnectionState()` detects a `!connected → connected` transition. code=354 events are suppressed for 3s after BT reconnects. Added `gestureCooldownUntil` also covers post-reconnect phantom gestures from any code path. |

### Round 13: Misleading Toast on Recording Toggle + Audio Focus Tug-of-War (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 54 | **LOW** — Double-click to start recording shows "DOUBLE_PRESS → Start Recording" (correct). Double-click to stop recording shows "DOUBLE_PRESS → Start Recording" again (misleading) because the toast in `handleGesture()` always uses `action.displayName` ("Start Recording") regardless of whether the action toggles on or off. The recording actually stops correctly — the toast is the only thing wrong. | Changed the toast display text to check `backgroundRecorder` state at toast time. If `backgroundRecorder != null` (recording is active), shows "Stop Recording"; otherwise "Start Recording". Non-recording actions (SOS_ALERT, SHARE_LOCATION) continue using the static `action.displayName`. |
| 55 | **HIGH** — After any gesture fires, `requestTemporaryAudioFocus()` grabs `AUDIOFOCUS_GAIN` for 30s. If the user then opens another app (YouTube, Spotify) and tries to play audio, the `focusChangeListener` re-acquires focus within 500ms, creating a tug-of-war that blocks the other app from playing. The log shows: `Audio focus lost: -2 (attempt #1)` → 500ms later → `focus re-acquired after attempt #1`. This happens up to 3 times. | Added `focusHeldTemporarily` check in `focusChangeListener`: when focus is lost during the temporary post-gesture window, do NOT re-acquire it. The user is actively using another app, and we shouldn't fight for audio focus. Subsequent earbud events still arrive via the AccessibilityService code=354 path, so toggle-off gestures remain functional. |

### Round 14: Phantom Code=354 Trust Window — Reset Gesture Detector on Phantom Events (2026-05-20)

| # | Issue | Fix |
|---|---|---|
| 56 | **CRITICAL** — Random gesture execution (SOS, recording, etc.) from phantom code=354 events without user input. The Transsion BT stack periodically fires phantom code=354 events (BT keepalive, system state changes, etc.). These accumulate in the gesture detector's `pressTimestamps` list. Two phantom events 1-10s apart trigger SLOW_DOUBLE_PRESS → SOS. Two phantom events within 800ms trigger DOUBLE_PRESS → recording. The user reports "my app or phone is still randomly triggering ex the SOS even without me doing any command or clicking anything its annoying". Prior mitigations (gesture cooldown, reconnect cooldown) only helped after a real gesture, not for standalone phantom events. | Added `lastTrustedEventTime` — updated on every standard media key (HEADSETHOOK, MEDIA_PLAY_PAUSE, MEDIA_PLAY, MEDIA_PAUSE) and AVRCP command (MEDIA_NEXT, MEDIA_PREVIOUS). In the code=354 handler, if no trusted event arrived within the last 3s (`CODE354_TRUST_WINDOW_MS`), the event is treated as phantom: `gestureDetector.reset()` is called (clearing all accumulated press timestamps, slow-double timer, double-press debounce) and the event is dropped. On Transsion, real earbud taps fire BOTH paths simultaneously, so a real tap will have a trusted event within milliseconds of code=354 — this doesn't affect functionality. |

## Logging System

The entire gesture pipeline logs to Logcat with a consistent tag prefix `ShieldBT.*`:

| Component | Tag | What's Logged |
|---|---|---|
| `MediaButtonReceiver` | `ShieldBT.Broadcast` | Incoming broadcast intents, key codes, forwarding decisions |
| `ProtectionForegroundService` | `ShieldBT.Service` | Service lifecycle, MediaSession callbacks, BT connection changes, gesture execution results |
| `GestureDetector` | `ShieldBT.Gesture` | Each key event, press count, gesture detection firings, timestamp cleanup |
| `ProtectionPrefs` | `ShieldBT.Prefs` | Trigger event saves |
| `ProtectionStatusActivity` | `ShieldBT.UI` | Permission requests, protection toggle, state refreshes |

To view logs during testing:
```
adb logcat -s ShieldBT.Broadcast ShieldBT.Service ShieldBT.Gesture ShieldBT.Prefs ShieldBT.UI
```

### Known Limitations

- **AirPods triple press** — AirPods intercept triple press at firmware level (maps to skip/previous track). The `GestureDetector` debounce fix (400ms delay on double-press, [[#Category A: AirPods Button Presses Not Triggering Actions|see above]]) helps ensure triple-press fires when detected, but AirPods may still map triple-tap to `KEYCODE_MEDIA_PREVIOUS` or not forward it at all depending on model/firmware.
- **AirPods double press** — Most reliably forwarded by AirPods. Audio focus re-acquisition ([[#Category A: AirPods Button Presses Not Triggering Actions|Fix #2]]) ensures SafeWay reclaims button priority after Spotify loses focus.
- **Gesture vs. music conflict** — Any media button gesture conflicts with music playback controls. Fixed by using `AUDIOFOCUS_GAIN` so SafeWay holds media button priority for 30s after a gesture. For the slow-double window, `AUDIOFOCUS_GAIN_TRANSIENT` is used instead so other apps aren't affected during the arm phase. Trade-off: media apps may briefly duck/pause during active gesture handling.
- **Process death during recording** — If the process is killed while background recording is active, the audio file is orphaned (no pending draft saved). `ensureRunning()` restarts the service on next app open, but the recording is lost.
- **START_STICKY unreliability** — On Transsion XOS and other aggressive OEM skins, `START_STICKY` does not reliably restart killed services. Mitigated by `ensureRunning()` in SplashActivity and HomeActivity.
- **Orphaned media files** — Discarding a draft (via back button confirmation) does not clean up the recorded audio, photo, or video files on disk. These files accumulate until the app is reinstalled or storage is manually cleaned.

## Long-Term Solution

For production reliability, build a **custom BLE panic button** (e.g., ESP32 with custom firmware):
- Direct BLE packet access
- No music conflicts
- Custom UUIDs
- Better battery efficiency

## Hackathon Demo Strategy

- One Android device
- One tested Bluetooth earbud
- Triple press trigger
- SOS system + hidden recording + live location sharing

**Pitch:** *"A discreet wearable-triggered emergency response system for survivors of violence."*

## Technical Verdict

> **YES, the system is technically possible.** Do NOT attempt raw AirPod tap detection. Use Android media button interception instead.

**Recommended MVP:**
```
Bluetooth Earbud → Media Button Event → Foreground Service → Emergency Action
```

## Proposed Integration with SafeWay

The existing app already has:
- [[Encryption System]] — for encrypting recordings
- [[Emergency AlertDispatcher]] — for SOS dispatch
- [[EmergencyAlertActivity]] — SOS UI
- [[Shield Overlay Service]] — foreground service pattern
- [[Support Circle]] — trusted contacts
- [[Records & Export]] — evidence storage and export
- [[Incident Logging]] — MediaRecorder for audio capture

**New components needed:**
- `BluetoothMonitorService` — detect connected earbuds
- `MediaButtonReceiver` — intercept media button events
- `GestureDetector` — timing-based gesture recognition
- `ProtectionForegroundService` — always-on protection mode
- New UI screens (onboarding, pairing, gesture mapping, protection status)

## Media Conflict Resolution: AVRCP vs. Safety Triggers

### The Core Problem

AirPods/earbuds send **AVRCP key codes** for tap gestures:
- Double-tap → `KEYCODE_MEDIA_NEXT` (code 87)
- Triple-tap → `KEYCODE_MEDIA_PREVIOUS` (code 88)

These codes are identical to media "next track" and "previous track" commands. **Spotify, YouTube, and every media app register for the same events.** There is no way to distinguish "I want to skip the track" from "I want to trigger an emergency action" at the protocol level — the phone receives the same electrical signal either way.

### The AccessibilityService Parallel Path

On Transsion/Infinix devices, the earbud button press also generates a **vendor-specific key code (354)** that is caught by the AccessibilityService (`onKeyEvent()`) independently of the AVRCP/MediaSession path. This means:

- Every earbud tap fires **two parallel event streams**: AVRCP (to media apps) and code=354 (to SafeWay's accessibility service)
- Both fire simultaneously, so the user gets: Spotify skips track + SafeWay triggers action
- This is transparent to the user — they hear the song skip and feel the SafeWay vibration

### Current Solution: Temporary Audio Focus

| Design Decision | Rationale |
|---|---|
| Short `AUDIOFOCUS_GAIN_TRANSIENT` for slow-double arm window | Gives routing priority for the second press without blocking other apps. AVRCP PAUSED state (not focus) is the real fix for earbud firmware. |
| No permanent `AUDIOFOCUS_GAIN` | Prevents SafeWay from pausing media when protection is active but no gesture has occurred |
| 30s focus window after each gesture | Enough to capture follow-up taps (e.g., double-tap to stop recording) without permanently disrupting media |
| Max 3 re-acquisition attempts with exponential backoff (500ms/1s/2s) | Prevents infinite focus tug-of-war with other apps while preserving gesture reliability |
| AccessibilityService code=354 path always active | Provides a parallel trigger path that doesn't depend on audio focus at all |

### Behavioral Summary

| Scenario | What Happens |
|---|---|
| Protection active, no media playing, double-tap | SafeWay catches it via accessibility + AVRCP, starts recording |
| Protection active, Spotify playing, double-tap | Spotify skips track (AVRCP) AND SafeWay starts recording (code=354). Both fire simultaneously |
| Second double-tap within 30s | SafeWay holds temporary focus, stops recording. Spotify doesn't skip (focus held by SafeWay) |
| 30s after last gesture | Audio focus abandoned, media controls return to normal |
| Protection deactivated | All interception stops, normal media behavior |
| BT device disconnected | 3s phantom cooldown + gestureDetector reset prevents spurious triggers from audio stack |

### The Real Solution: BLE Beacon

The AVRCP media conflict is a **protocol-level limitation** — it cannot be fully solved with AirPods. The reliable solution is a dedicated **BLE GATT button** (beacon) that communicates over a custom service UUID that no media app has access to. See [[#Device Type Design Notes]] below.

For MVP: the parallel-path approach (code=354 + AVRCP) works acceptably. The user gets both track skip and SafeWay action on the first tap, and subsequent taps within 30s route exclusively to SafeWay.

## Device Type Design Notes

### AVRCP (Earbuds / Headsets — Current Implementation)

- AirPods, Galaxy Buds, and most consumer TWS earbuds use **AVRCP** (Audio/Video Remote Control Profile)
- Double-tap → `KEYCODE_MEDIA_NEXT` / Triple-tap → `KEYCODE_MEDIA_PREVIOUS`
- These are the same codes as media controls — **media apps (Spotify, YouTube) compete for the same events**
- No way to tell SafeWay-tap from Spotify-next-track at the protocol level
- Current solution: screen-state routing (screen off/locked → SafeWay, screen on/unlocked → passthrough)

### BLE GATT (Beacons / Panic Buttons)

- Uses **BLE GATT** (Bluetooth Low Energy Generic Attribute Profile)
- Device exposes a custom service with a characteristic — button press writes to it
- **No conflict with media apps** — Spotify/YouTube have no access to this data
- App reads the button directly via `BluetoothGattCallback`
- Hardware options: off-the-shelf BLE button (e.g., Flic, Xiaomi button) or custom ESP32-based panic button
- Requires BLE scanning in foreground service + pairing once

### HID (Keyboards / Game Controllers)

- Uses **HID over Bluetooth** (Human Interface Device profile)
- Button presses arrive as key events — accessible via AccessibilityService
- Media apps ignore HID input
- `KeyEvent` codes are standard (e.g., KEYCODE_BUTTON_A, KEYCODE_F1, etc.)
- Requires AccessibilityService to capture in background

### Recommendation

| Profile | Conflict with Media | Background Reliability | Hardware Available |
|---|---|---|---|
| AVRCP (AirPods) | **High** — shares media codes | Medium — needs audio focus | User's existing earbuds |
| BLE GATT | **None** | High — persistent connection | Needs dedicated beacon |
| HID | **None** | Medium — needs Accessibility | Niche — keyboards, etc. |

For production: support AVRCP (earbuds users already have) + BLE GATT (dedicated panic button for reliability). The two paths are independent and can coexist.

## Related

- [[Emergency System]] — Existing SOS infrastructure
- [[Shield Overlay Service]] — Existing foreground service pattern
- [[Incident Logging]] — Existing recording capabilities
- [[Records & Export]] — Existing evidence storage
