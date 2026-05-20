---
tags:
  - activity
  - dashboard
  - home
---
# HomeActivity

**File:** `com.example.safeway.HomeActivity`

The main dashboard — the first screen users see after the splash.

## Quick Action Cards

| Button | Destination |
|---|---|
| 🆘 Emergency | [[EmergencyAlertActivity]] |
| 📓 Log Incident | [[LogIncidentActivity]] |
| 👥 My Circle | [[SupportCircleActivity]] |
| 📁 Records | [[RecordsActivity]] |
| 📚 Resources Center | [[ResourcesActivity]] |

## Shield Overlay Toggle

A `SwitchCompat` in the UI controls the floating [[Shield Overlay Service]]:

**States:**
1. Switch OFF → disabled (stop service, persist preference)
2. Switch ON → check SYSTEM_ALERT_WINDOW permission
   - Granted → start foreground service
   - Denied → redirect to system settings (Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
3. `onResume()` → recover from permission redirect

The `pendingOverlayPermissionRequest` flag handles the permission redirect round-trip.

## Default Hotlines

Four fixed hotline buttons on the dashboard:

| Button | Number | Action |
|---|---|---|
| 🚔 Police | 999 | Dial |
| 🏥 Hospital | 112 | Dial |
| ⚖️ Amnesty | 0800123456 | Dial |
| 🧠 Counselor | 0724999999 | Dial |

## Custom Hotlines

Users can add custom hotlines beyond the defaults:
- **Add:** Opens dialog (name + phone), saves to [[Hotline (Entity)]] via [[HotlineDao]]
- **Display:** Appended below defaults in a `GridLayout`
- **Delete:** Long-press to reveal delete button, confirm dialog

## Lifecycle

```kotlin
onCreate()
  ├── setupQuickActions()    — button click handlers
  ├── setupBottomNavigation() — [[BottomNavHelper]]
  ├── setupHotlines()        — default + custom hotlines
  └── setupShieldToggle()    — overlay on/off

onResume()
  ├── Check overlay permission recovery
  └── Sync overlay state with persistence
```

## Related

- [[Shield Overlay Service]] — Toggle controls this service
- [[Navigation System]] — Bottom nav setup
- [[Emergency System]] — Where SOS goes
