---
tags:
  - emergency
  - sos
  - sms
  - location
---
# Emergency System

Two components work together to provide SOS functionality:
1. [[EmergencyAlertActivity]] — Full-screen SOS interface
2. [[EmergencyAlertDispatcher]] — Reusable SMS dispatch engine

## SMS Alert Content

The alert message is formatted as:

```
SHIELD ALERT: Thomas needs help. Location: <lat>, <lng>. Map: <maps_link>. Time: <timestamp>. Automated safety alert.
```

> ⚠️ The alert currently uses a hardcoded name **"Thomas"** in the message text.

## EmergencyAlertActivity

**File:** `com.example.safeway.EmergencyAlertActivity`

A full-screen SOS activity accessible from the Home dashboard.

### Flow

1. **Permission check:** Sends SMS permission request, then location permission request
2. **Location fetch:** Uses FusedLocationProviderClient with fallback to `lastLocation`
3. **SMS dispatch:** Sends to all contacts where `smsAlerts = true`
4. **Result:** Shows success count as Toast

### Key features:
- Real-time SMS preview as user composes
- Location chip display (coordinates)
- Contact chips showing who will be alerted
- Fallback permission handling chain

## EmergencyAlertDispatcher

**File:** `com.example.safeway.EmergencyAlertDispatcher`

A singleton `object` that encapsulates SOS dispatch logic, reused by:
- [[EmergencyAlertActivity]] (manual SOS)
- [[Shield Overlay Service]] (one-tap SOS from overlay)

### Method

```kotlin
EmergencyAlertDispatcher.sendNow(context) { success, message -> }
```

### Permissions Required

| Permission | Purpose |
|---|---|
| `SEND_SMS` | Send alert SMS |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS coordinates |

### Location Strategy

1. Try `getCurrentLocation()` with `PRIORITY_HIGH_ACCURACY`
2. Fallback to `lastLocation`
3. If unavailable, send message with "Location unavailable"

## Related

- [[Support Circle]] — Managing contacts for SMS alerts
- [[Shield Overlay Service]] — One-tap SOS from overlay
- [[Incident Logging]] — Logging incidents
