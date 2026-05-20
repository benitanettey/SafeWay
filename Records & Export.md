---
tags:
  - records
  - export
  - search
  - filter
---
# Records & Export

**File:** `com.example.safeway.RecordsActivity` + `RecordsAdapter`

The private records journal — users can view, search, filter, edit, delete, and export all logged incidents.

## Features

### Search & Filter

| Control | Options |
|---|---|
| Search bar | Full-text across type, description, location, who, severity |
| Severity filter | All / Low / Medium / High / Crisis |
| Voice note filter | All / With voice note / Without voice note |
| Sort order | Newest first / Oldest first |
| Pagination | 20 items per page, "Load More" button |

### Record Cards (RecyclerView)

Each card shows:
- **Title:** Incident type (decrypted)
- **Timestamp:** Formatted as "MMM dd • HH:mm"
- **Description:** Decrypted description
- **Chips:** Severity, GPS logged, Voice note, Photo status, Video status
- **Playback controls:** If voice note exists → play/pause with circular progress ring + animated waveform
- **Details button:** Opens edit dialog

### Detail Dialog

A modal dialog (`dialog_record_detail.xml`) for viewing and editing:
- Editable fields: type, description, severity, location, who
- Buttons to open photo/video evidence
- Voice note playback with waveform
- **Save** button → updates encrypted data via [[IncidentDao]]
- **Delete** button → removes incident + all associated media files

### Voice Playback

Sophisticated playback UI:
- Play/Pause/Resume support
- Seek-to on pause/resume
- Animated waveform (35 bars, sin wave + noise algorithm)
- Circular progress ring on the card
- Real-time elapsed/total duration overlay

### Empty State

Shows contextual message:
- "No records yet" (no filter active)
- "No records match filters" (filter/search active)
- Alert banner with latest record timestamp and count

## Exports

Three export formats:

### CSV Export
- File: `records_<timestamp>.csv`
- Standard CSV with headers
- All fields included (including file paths)
- Proper CSV escaping (quotes doubled)

### PDF Export
- File: `records_<timestamp>.pdf`
- A4 page (595×842)
- Title: "SafeWay Private Records"
- Max ~45 records per page
- Truncated descriptions (70 chars)

### Encrypted Export
- File: `records_<timestamp>.enc`
- Uses **PBKDF2WithHmacSHA256** with 10,000 iterations
- AES-256/GCM with random salt + IV
- Hardcoded passphrase: `"SafeWayExportSecret"`
- Format: `salt[16] + iv[12] + ciphertext`

### Share
All exports are shared via Android's `ACTION_SEND` intent with `FileProvider`.

## Decryption

When records are loaded, `decryptIncident()` decrypts each encrypted field:
```kotlin
incident.copy(
    type = EncryptionManager.decrypt(incident.type),
    description = EncryptionManager.decrypt(incident.description),
    severity = EncryptionManager.decrypt(incident.severity),
    location = EncryptionManager.decrypt(incident.location),
    who = EncryptionManager.decrypt(incident.who)
)
```

## Related

- [[Incident Logging]] — How incidents are created
- [[Encryption System]] — How decryption works
- [[Data Layer]] — Database structure
- [[RecordsAdapter]] — RecyclerView adapter details
