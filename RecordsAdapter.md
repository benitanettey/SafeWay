---
tags:
  - adapter
  - recyclerview
  - records
  - playback
---
# RecordsAdapter

**File:** `com.example.safeway.RecordsAdapter`

A `RecyclerView.Adapter` for displaying incident records with inline voice playback.

## Layout

Uses `item_record_incident.xml` — each item shows:
- Title (incident type), timestamp, description
- Chips for severity, GPS, voice note, photo, video status
- Voice play/pause button with circular progress ring
- Animated waveform during playback
- Details button

## Playback Optimization

The adapter uses a **direct ViewHolder update strategy** to avoid RecyclerView rebinds during active playback:

```kotlin
fun setPlaybackState(incidentId, isPlaying, progressPercent, elapsedLabel, durationLabel)
```

- **Transitioning** (play started or stopped): Full `notifyDataSetChanged()` to show/hide waveform across items
- **Active tick** (200ms intervals): Directly updates `activeViewHolder` via `updatePlaybackUi()` — no rebind, no flicker

## Severity Chip Styling

| Severity | Background | Text Color |
|---|---|---|
| High / Crisis | Dark red (`#2A1418`) with stroke (`#4A1A1A`) | `emergency_red` |
| Other | `card_background` | `text_primary` |

## Related

- [[Records & Export]] — Records activity that uses this adapter
- [[UI Components]] — Chip styling and waveform
