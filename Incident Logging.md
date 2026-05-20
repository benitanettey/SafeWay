---
tags:
  - incident
  - logging
  - media
  - recording
---
# Incident Logging

**File:** `com.example.safeway.LogIncidentActivity`

The incident logger is the most feature-rich activity in the app, allowing users to document abuse incidents with rich media evidence.

## Form Fields

| Field | UI Element | Required | Encrypted |
|---|---|---|---|
| Type | Chip group (Physical/Verbal/Financial/Sexual/Neglect) | ✅ | ✅ |
| Severity | Chip group (Low/Medium/High/Crisis) | ✅ | ✅ |
| Description | Multi-line EditText | ✅ | ✅ |
| Location | EditText | ✅ | ✅ |
| Who | EditText | Optional (defaults to "Unknown") | ✅ |

## Media Capture

### Voice Notes

| Feature | Detail |
|---|---|
| Format | AAC (m4a), 128kbps, 44.1kHz |
| Storage | `ExternalFilesDir/Music/SafeWay/voice_notes/` |
| UI | Record button with live waveform visualization |
| Duration | 200ms tick updates |
| Amplitude | Normalized from MediaRecorder maxAmplitude (0-32767) |

**Recording state:**
- Tap to start → waveform animation begins, timer counts up
- Tap to stop → validates recording, shows duration
- Preview button → plays back with animated waveform
- Delete → removes audio file

**Waveform rendering:**
- Recording: dynamic bars from microphone amplitude (4-28dp height)
- Playback: synthetic waveform generated from sin waves + noise (35 bars)
- Max visible bars: 80 (scrolls oldest off)

### Photo Evidence
- Uses `ActivityResultContracts.TakePicture()`
- Storage: `ExternalFilesDir/Pictures/SafeWay/photos/`
- Preview: thumbnail in UI, tap to open full-size
- Status indicator showing attached/detached state

### Video Evidence
- Uses `ActivityResultContracts.CaptureVideo()`
- Storage: `ExternalFilesDir/Movies/SafeWay/videos/`
- Preview: button to open in external player
- Status indicator showing attached/detached state

### FileProvider
All media files are shared via `FileProvider` using authority `${packageName}.fileprovider`.

## Smart Recommendations

After saving an incident, [[ResourceRecommendationEngine]] suggests relevant educational content:

| Incident Type | Suggested Resource |
|---|---|
| Physical, Sexual | Evidence Guide |
| Verbal, Financial, Neglect | Understand Abuse |

## Validation

```kotlin
// Fields checked before save:
description != empty
location != empty
selectedType != empty
selectedSeverity != empty
```

## Save Flow

1. Validate required fields
2. Stop any active recording/preview
3. Encrypt sensitive text fields via [[EncryptionManager]]
4. Insert into Room database via [[IncidentDao]]
5. Open suggested resource activity
6. Finish (return to caller)

## Permissions

| Permission | When Requested |
|---|---|
| `RECORD_AUDIO` | First tap of record button |
| `CAMERA` | First tap of photo or video button |

## Related

- [[Encryption System]] — How incident fields are encrypted
- [[Data Layer]] — Incident entity and DAO
- [[Records & Export]] — Viewing and editing saved incidents
- [[ResourceRecommendationEngine]] — Post-log recommendations
