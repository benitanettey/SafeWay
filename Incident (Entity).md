---
tags:
  - entity
  - database
  - incident
---
# Incident Entity

**File:** `com.example.safeway.data.Incident`

The core data entity for the private incident journal.

## Schema

```kotlin
@Entity(tableName = "incidents")
data class Incident(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val description: String,
    val severity: String,
    val location: String,
    val who: String,
    val hasVoiceNote: Boolean,
    val voiceNotePath: String?,
    val voiceDurationSec: Int,
    val photoPath: String? = null,
    val videoPath: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
```

## Field Details

| Field | Type | Encrypted | Notes |
|---|---|---|---|
| id | Int | — | Auto-generated primary key |
| type | String | ✅ | "Physical", "Verbal", "Financial", "Sexual", "Neglect" |
| description | String | ✅ | Free-text description of incident |
| severity | String | ✅ | "Low", "Medium", "High", "Crisis" |
| location | String | ✅ | Where it happened |
| who | String | ✅ | Who was involved |
| hasVoiceNote | Boolean | — | Whether voice recording exists |
| voiceNotePath | String? | — | File path to .m4a recording |
| voiceDurationSec | Int | — | Length of recording |
| photoPath | String? | — | File path to photo (added in v4) |
| videoPath | String? | — | File path to video (added in v4) |
| createdAtMillis | Long | — | Timestamp of creation |

## Database Column History

- **v1-v2:** Initial table (type, description, severity, location, who, hasVoiceNote, voiceDurationSec, createdAtMillis)
- **v2-v3:** Added `voiceNotePath`
- **v3-v4:** Added `photoPath`, `videoPath`
- **v4 (unchanged):** `hotlines` table added separately

## Encryption

The `type`, `description`, `severity`, `location`, and `who` fields are encrypted at rest using [[Encryption System]]. They are:
- Encrypted when writing ([[Incident Logging]])
- Decrypted when reading ([[Records & Export]])

## Related

- [[Data Layer]] — Database configuration
- [[IncidentDao]] — Data access operations
- [[Encryption System]] — How fields are encrypted
