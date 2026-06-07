---
tags:
  - entity
  - database
  - hotline
---
# Hotline Entity

**File:** `com.example.safeway.data.Hotline`

Represents a user-added custom hotline number.

## Schema

```kotlin
@Entity(tableName = "hotlines")
data class Hotline(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String
)
```

## Usage

Custom hotlines are added via the [[HomeActivity]] dashboard. The app ships with 4 default hotlines (Police: 999, Hospital: 112, Amnesty: 0800123456, Counselor: 0724999999) that are hardcoded — not stored in the database.

Custom hotlines are stored in the database and rendered below the defaults in a `GridLayout`.

## Related

- [[Data Layer]] — Database configuration
- [[HotlineDao]] — Data access operations
- [[HomeActivity]] — Where hotlines are managed
