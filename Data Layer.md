---
tags:
  - database
  - room
  - data-layer
---
# Data Layer

## Room Database

**File:** `com.example.safeway.data.AppDatabase`

The app uses **Room** with an SQLite database named `shield_database`. The database version is **5** with 4 migration steps (MIGRATION_1_2 through MIGRATION_4_5).

### Entities

```kotlin
@Database(
  entities = [Contact::class, Incident::class, Hotline::class],
  version = 5,
  exportSchema = false
)
```

### Migration History

| From | To | Change |
|---|---|---|
| 1 | 2 | Initial `incidents` table creation |
| 2 | 3 | Added `voiceNotePath` column to incidents |
| 3 | 4 | Added `photoPath` and `videoPath` columns to incidents |
| 4 | 5 | Created `hotlines` table |

## Entities

### [[Incident (Entity)|Incident]]

The core journal entry — each incident stores:

| Field | Type | Encrypted? |
|---|---|---|
| id | Int (PK, auto) | — |
| type | String | ✅ |
| description | String | ✅ |
| severity | String | ✅ |
| location | String | ✅ |
| who | String | ✅ |
| hasVoiceNote | Boolean | — |
| voiceNotePath | String? | — |
| voiceDurationSec | Int | — |
| photoPath | String? | — |
| videoPath | String? | — |
| createdAtMillis | Long | — |

### [[Contact (Entity)|Contact]]

A trusted contact in the user's support circle:

| Field | Type | Notes |
|---|---|---|
| id | Int (PK, auto) | — |
| name | String | — |
| phone | String | — |
| relationship | String | e.g. "Friend", "Sibling" |
| smsAlerts | Boolean | Whether to include in SOS (default: true) |
| includeGPS | Boolean | Whether to include GPS in SMS (default: true) |

### [[Hotline (Entity)|Hotline]]

Custom hotline numbers added by the user (besides the 4 defaults):

| Field | Type |
|---|---|
| id | Int (PK, auto) |
| name | String |
| phone | String |

## DAOs

### [[IncidentDao]]

| Method | Description |
|---|---|
| `insertIncident(Incident)` | Insert a new incident |
| `updateIncident(Incident)` | Update existing incident |
| `deleteIncident(Incident)` | Delete incident |
| `getAllIncidents()` | Get all, newest first |
| `getIncidentsPaged(limit, offset)` | Paginated query |
| `getIncidentCount()` | Total count |

### [[ContactDao]]

| Method | Description |
|---|---|
| `insertContact(Contact)` | Add contact |
| `deleteContact(Contact)` | Remove contact |
| `getAllContacts()` | List all |
| `getContactById(id)` | Get single contact |
| `updateContact(id, name, phone, relationship)` | Edit contact |
| `getContactsWithSmsAlerts()` | Get contacts eligible for SOS |

### [[HotlineDao]]

| Method | Description |
|---|---|
| `insertHotline(Hotline)` | Add custom hotline |
| `deleteHotline(Hotline)` | Remove hotline |
| `getAllHotlines()` | List all |

## Singleton Pattern

```kotlin
AppDatabase.getDatabase(context)
```

Thread-safe double-checked locking with `@Volatile` + `synchronized`. Database file: `shield_database`.

## Related

- [[Encryption System]] — How incident fields are encrypted before storage
- [[Incident Logging]] — How incidents are created
- [[Records & Export]] — How incidents are read, decrypted, and exported
