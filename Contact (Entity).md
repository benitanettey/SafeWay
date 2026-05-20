---
tags:
  - entity
  - database
  - contact
---
# Contact Entity

**File:** `com.example.safeway.data.Contact`

Represents a trusted contact in the user's [[Support Circle]].

## Schema

```kotlin
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String,
    val relationship: String,
    val smsAlerts: Boolean = true,
    val includeGPS: Boolean = true
)
```

## Field Details

| Field | Type | Default | Purpose |
|---|---|---|---|
| id | Int | auto | Primary key |
| name | String | — | Contact's name |
| phone | String | — | Phone number for SMS |
| relationship | String | — | e.g. "Friend", "Sibling", "Partner" |
| smsAlerts | Boolean | true | Include in SOS broadcasts |
| includeGPS | Boolean | true | Include GPS coordinates in SMS |

## Notes

- Not encrypted (unlike [[Incident (Entity)]] fields)
- The `updateContact()` DAO method exists but is not exposed in the UI
- The `smsAlerts` flag is the key filter for [[EmergencySystem]]

## Related

- [[Data Layer]] — Database configuration
- [[ContactDao]] — Data access operations
- [[Support Circle]] — UI for managing contacts
- [[Emergency System]] — How contacts receive SOS
