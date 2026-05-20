---
tags:
  - overlay
  - service
  - floating-bubble
  - emergency
---
# Shield Overlay Service

**File:** `com.example.safeway.overlay.ShieldOverlayService`

A **floating bubble overlay** that provides one-tap access to emergency and logging functions from any screen.

## Architecture

The overlay is an Android **Foreground Service** with:
- A persistent notification (channel: `shield_overlay_channel`, ID: `7001`)
- SYSTEM_ALERT_WINDOW permission required
- TYPE_APPLICATION_OVERLAY (API 26+) / TYPE_PHONE (legacy)

## UI Components

### Main Bubble (58dp)
- Shield icon (white on dark circle with accent border)
- Draggable — user can reposition anywhere on screen
- Tap to expand/collapse the menu
- Position persisted via [[OverlayPrefs]]

### Emergency Bubble (46dp)
- Alert icon
- Tap → dispatches [[EmergencyAlertDispatcher]] immediately
- Visible only when menu is expanded

### Log Bubble (46dp)
- Log icon
- Tap → opens [[LogIncidentActivity]]
- Visible only when menu is expanded

### Dismiss Overlay
- Full-screen transparent overlay behind expanded menu
- Tap anywhere to collapse

## Interaction Design

### Dragging
- Touch threshold: 5px before drag mode activates
- Real-time position update via `WindowManager.updateViewLayout()`
- Position saved to SharedPreferences on touch up

### Expand/Collapse Animation
- Both child bubbles animate from main bubble position
- **Expand:** 180ms — scale from 0.5→1.0, alpha 0.5→1.0
- **Collapse:** 140ms — reverse
- Child positions: offset 74dp diagonally (emergency top-left, log top-right)

### Menu Position
- When main bubble is dragged, child bubbles follow and reposition
- Positions recalculated on drag

## OverlayPrefs

**File:** `com.example.safeway.overlay.OverlayPrefs`

SharedPreferences wrapper storing:

| Key | Type | Default |
|---|---|---|
| `overlay_enabled` | Boolean | false |
| `bubble_x` | Int | 0 |
| `bubble_y` | Int | 320 |

## State Machine

```
DISABLED → ENABLED (foreground service starts)
  └─ On toggle OFF → stop service, cleanup views
  └─ On permission lost → disable, show toast

EXPANDED → COLLAPSED
  └─ Tap main bubble
  └─ Tap dismiss overlay
  └─ Receive ACTION_OUTSIDE touch event
```

## Lifecycle

- `onCreate()`: Creates bubbles, adds main bubble, starts foreground
- `onStartCommand()`: Checks overlay permission → START_STICKY
- `onDestroy()`: Removes all views safely
- Service runs in foreground with `PRIORITY_LOW` notification

## Safety Note

The overlay requires `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` which redirects to system settings. [[HomeActivity]] handles the permission flow with automatic re-check in `onResume()`.

## Related

- [[Emergency System]] — What the SOS button dispatches
- [[Incident Logging]] — What the log button opens
- [[HomeActivity]] — Where overlay is toggled on/off
