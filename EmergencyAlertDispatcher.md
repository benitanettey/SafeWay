---
tags:
  - emergency
  - sms
  - dispatcher
  - singleton
---
# EmergencyAlertDispatcher

**File:** `com.example.safeway.EmergencyAlertDispatcher`

A singleton `object` that handles the SOS SMS dispatch logic.

## API

```kotlin
sendNow(context, onResult: (Boolean, String) -> Unit)
```

- `Boolean`: success (true if at least one SMS was sent)
- `String`: status message ("Sent to N contact(s)" / error description)

## Flow

1. Check SMS permission → fail early if missing
2. Fetch GPS location (best-effort, non-blocking)
3. Build alert message with location + timestamp
4. Query contacts with SMS alerts from database
5. Send SMS to each contact (fire-and-forget, per-contact try/catch)
6. Report result

## Location Strategy

Attempts:
1. `FusedLocationProviderClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY)`
2. Fallback to `getLastLocation()`
3. If both fail → "Location unavailable" in message

## Alert Message Format

```
SHIELD ALERT: Thomas needs help. Location: <lat, lng>. Map: <maps_link>. Time: <timestamp>. Automated safety alert.
```

## Usage

Called from:
- [[Shield Overlay Service]] — emergency bubble tap
- [[EmergencyAlertActivity]] — SOS button

## Related

- [[Emergency System]] — Full emergency system overview
- [[Support Circle]] — Contact management for alerts
