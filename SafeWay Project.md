---
tags:
  - project-root
  - safe-way
aliases:
  - SafeWay
  - Shield App
---
# SafeWay — Project Overview

**SafeWay** (codename: *Shield*) is an Android application built with **Kotlin** that provides safety tools, private incident journaling, and resource access for users experiencing domestic violence or abuse.

> **Application ID:** `com.example.safeway`
> **Min SDK:** 24 | **Target SDK:** 36 | **Compile SDK:** 36

## Core Mission

Provide a discreet, encrypted toolkit that helps users:
- 🆘 Send **emergency SMS alerts** to trusted contacts with GPS location
- 📓 **Log incidents** privately with full AES-256 encryption (via Android Keystore)
- 🎤 Capture **voice notes, photos, and video** evidence
- 👥 Build a **support circle** of trusted contacts
- 📞 Access **hotlines and resources** (police, hospital, counseling, legal aid)
- 🛡️ Deploy a **floating shield overlay** for one-tap emergency access from any app
- 🎧 **Trigger emergency actions discreetly** using Bluetooth headsets or beacons (via media button interception)

## Architecture

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | XML layouts, Material Design (Material Chips, Cards) |
| Database | Room (SQLite) with migrations |
| Encryption | Android Keystore (AES/GCM/NoPadding 256-bit) |
| Navigation | Programmatic (Intents + BottomNavHelper) |
| Background | Foreground Service (Shield Overlay) |
| Location | Google Play Services (FusedLocationProvider) |
| Coroutines | kotlinx-coroutines |

## Tech Stack

- **AndroidX** (AppCompat, Core KTX, Activity, ConstraintLayout, RecyclerView)
- **Material Components** (Material 3 via `com.google.android.material`)
- **Room** (Runtime, KTX, Compiler via KAPT)
- **Google Play Services** (Location 21.3.0)
- **kotlinx-coroutines** (Android 1.7.3)

## Package Structure

```
com.example.safeway/
├── data/                      # Room database layer
│   ├── AppDatabase.kt
│   ├── Contact.kt / ContactDao.kt
│   ├── Hotline.kt / HotlineDao.kt
│   └── Incident.kt / IncidentDao.kt
├── domain/                    # BT trigger gesture system
│   ├── GestureDetector.kt
│   ├── ProtectionPrefs.kt
│   └── TriggerEvent.kt
├── overlay/                   # Floating shield bubble
│   ├── OverlayPrefs.kt
│   └── ShieldOverlayService.kt
├── protection/                # BT protection UI
│   └── ProtectionStatusActivity.kt
├── receiver/                  # System broadcast receivers
│   ├── BootReceiver.kt
│   └── MediaButtonReceiver.kt
├── service/                   # Background services
│   └── ProtectionForegroundService.kt
├── BottomNavHelper.kt
├── BreakingStigmaActivity.kt
├── EmergencyAlertActivity.kt
├── EmergencyAlertDispatcher.kt
├── EncryptionManager.kt
├── EvidenceGuideActivity.kt
├── FindHelpNearYouActivity.kt
├── HomeActivity.kt
├── LogIncidentActivity.kt
├── RecordsActivity.kt / RecordsAdapter.kt
├── ResourceRecommendationEngine.kt
├── ResourcesActivity.kt
├── SplashActivity.kt
├── SupportCircleActivity.kt
└── UnderstandAbuseActivity.kt
```

## Recent History

- **2026-05-20 (latest):** Round 14 — Fixed persistent phantom gesture triggers (SOS, recording, etc.) caused by code=354 phantom events from Transsion BT stack accumulating in gesture detector and arming slow-double/double-press timers. Added `lastTrustedEventTime` — code=354 is only trusted if a standard media key/AVRCP event arrived within 3s. Phantom code=354 resets gesture detector instead of feeding it. Also fixed Round 13 bugs: misleading toast now shows "Stop Recording" when toggling off; audio focus no longer re-acquired during temporary window so other apps can play.
- **2026-05-20:** Round 11 — Fixed "double clicking stopped working" bug: after any gesture fires, `isOtherAppPlayingAudio()` would false-positive because the system continued reporting active music after our own notification/siren audio. Added `focusHeldTemporarily` gate — during the 30s AUDIOFOCUS_GAIN window after a gesture, the suppression check is skipped entirely since our focus grab already paused any other media app.
- **2026-05-20:** Round 10 — Changed to stay-PAUSED AVRCP approach: after first MEDIA_PAUSE press, set PlaybackState to STATE_PAUSED and keep it there until gesture detected or 12s timeout. Changed slow-double audio focus from AUDIOFOCUS_GAIN to AUDIOFOCUS_GAIN_TRANSIENT so other apps (YouTube, Instagram, Spotify) aren't blocked during the arm window. Verified working — SOS triggered on second press at 3462ms gap. Also restored AUDIOFOCUS_GAIN in onSlowDoubleArmed. Added diagnostic logging.
- **2026-05-20:** Round 9 — Silent audio moved to dedicated HandlerThread (was running on main thread Handler, getting delayed by UI jank, causing restartIfDisabled every ~310ms). SLOW_DOUBLE_PRESS excluded from media suppression (requires deliberate 3-10s gap, can't be accidental). Proper write() return value handling for disabled-track recovery.
- **2026-05-20:** Round 8 — Silent audio underrun fix: buffer sized to 1s (was ~214ms), writes every 100ms (was 500ms), auto-restart on underrun. False trigger filtering: media button events gated on audio device connection. SOS feedback: notification channel with IMPORTANCE_HIGH + two-tone siren loop. Removed premature audio focus grab from onSlowDoubleArmed (was breaking AVRCP routing on Transsion). Added media-aware suppression: safety gestures are ignored when another app (Spotify, YouTube, browser, gallery) is actively playing audio.
- **2026-05-20:** Round 7 — SOS location timeout fix: switched from `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` (no timeout, 20-30s GPS wait on Infinix) to `lastLocation` + parallel fresh GPS with 5s timeout. Added short vibration on single tap to confirm slow-double timer armed. Added "Sending SOS..." toast for immediate feedback. Window widened to 1-10s.
- **2026-05-19:** Round 6 — Removed LONG_HOLD gesture entirely (was firing 3s after every single click due to ACTION_UP filtering preventing timer cancellation). Added SLOW_DOUBLE_PRESS gesture (click, wait ~3s, click again, 1.5-5s window, default SOS). Fixed Toast crash on background thread in EmergencyAlertDispatcher callback. Fixed slow-double description text overlap in gesture mapping card. Updated all vault docs and generated WhatsApp-ready user guide.
- **2026-05-19:** AirPods trigger reliability fixes — added `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission, audio focus re-acquisition listener (transient duck mode), `flagRequestFilterKeyEvents` in accessibility XML, and double-press debounce in GestureDetector. Full root cause analysis documented in [[Bluetooth Trigger System]].
- **2026-05-19:** Added DOUBLE_PRESS gesture, comprehensive Logcat logging (ShieldBT.* tags), haptic feedback on trigger, runtime permission requests, live BT status polling, notification shows last triggered action. Long hold threshold changed to 3s.
- **2026-05-19:** Bluetooth Trigger System MVP implemented and audited — 5 bugs found and fixed (1 critical ANR, 1 high-severity notification leak, 3 medium)
- **2026-03:** Complete redesign with material dark theme, encryption overhaul
- **Prior:** Audio recording UI, private records feature

## Navigation

The app uses a custom [[BottomNavHelper]] with 4 tabs: **Home**, **Log**, **Circle**, **Records**. Resource/education screens use a back-button navigation pattern without bottom nav highlighting.

---

**Explore the vault:**
- [[Data Layer]] — Database schema, entities, DAOs
- [[Encryption System]] — AES-256 via Android Keystore
- [[Emergency System]] — SOS alerts and dispatcher
- [[Shield Overlay Service]] — Floating bubble overlay
- [[Incident Logging]] — Logging with media capture
- [[Records & Export]] — Viewing, editing, exporting
- [[Support Circle]] — Contact management
- [[Resources Hub]] — Educational and help resources
- [[Navigation System]] — Bottom nav and routing
- [[UI Components]] — Layouts, theming, animations
