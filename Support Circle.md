---
tags:
  - contacts
  - support
  - circle
---
# Support Circle

**File:** `com.example.safeway.SupportCircleActivity`

Manage the user's trusted contacts — the people who receive SOS alerts.

## UI

### Contact Card Layout
Each contact is displayed as a horizontal card with:
- **Avatar:** Circular background with first 2 letters of name
- **Info:** Name (bold), phone number, relationship label
- **Status Chip:** "SMS alerts" / "Disabled" indicator
- **Remove button:** Opens confirmation dialog before deleting

### Empty State
> *"No trusted contacts added yet. Tap the + button to add one."*

## Add Contact Dialog

Uses `dialog_add_contact.xml` layout with:

| Field | Widget | Required |
|---|---|---|
| Name | EditText | ✅ |
| Phone | EditText | ✅ |
| Relationship | EditText | ❌ (defaults to "Friend") |
| SMS Alerts | CheckBox | — (default: unchecked) |

### Validation
- Name and phone are required
- Toast shown if missing

## Data Flow

```kotlin
// Insert
database.contactDao().insertContact(contact)

// Delete with confirmation
AlertDialog → database.contactDao().deleteContact(contact) → reload

// Query for SOS
database.contactDao().getContactsWithSmsAlerts()
```

## Integration with Emergency System

The `smsAlerts` flag determines whether a contact is included in SOS broadcasts. [[EmergencyAlertDispatcher]] queries:
```kotlin
database.contactDao().getContactsWithSmsAlerts()
```

## Edit Limitation

Currently, contacts cannot be edited after creation — only added or removed. The DAO has an `updateContact()` method but it is not exposed in the UI.

## Related

- [[Emergency System]] — How contacts receive SOS
- [[Data Layer]] — Contact entity and DAO
