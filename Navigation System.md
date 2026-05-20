---
tags:
  - navigation
  - bottom-nav
---
# Navigation System

## Bottom Navigation

**File:** `com.example.safeway.BottomNavHelper`

A custom bottom navigation system (not using the standard `BottomNavigationView`).

### Tabs

| Tab | Icon | Activity | Enum |
|---|---|---|---|
| Home | Home icon | [[HomeActivity]] | `NavTab.HOME` |
| Log | Log icon | [[LogIncidentActivity]] | `NavTab.LOG` |
| Circle | Circle icon | [[SupportCircleActivity]] | `NavTab.CIRCLE` |
| Records | Records icon | [[RecordsActivity]] | `NavTab.RECORDS` |

### Active Tab Styling

Each tab is a `LinearLayout` containing:
- An `ImageView` (icon) — color filtered
- A `TextView` (label) — bold when active
- A `View` (indicator) — 20dp wide line when active, 0dp when inactive

| State | Icon/Label Color | Indicator Width | Label Weight |
|---|---|---|---|
| Active | `highlight_accent` | 20dp | Bold |
| Inactive | `neutral_muted` | 0dp | Normal |

### Navigation Behavior

- Tapping the **active** tab → no-op (click listener removed)
- Tapping an **inactive** tab → launches target activity with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`
- Transition animation: `fade_in` for enter

## Back Navigation

Resource and secondary activities use an `ImageButton` (back arrow) in the top-left corner:

```kotlin
findViewById<ImageButton>(R.id.btn_back_*).setOnClickListener {
    finish()
    overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
}
```

## Transition Animations

| Direction | Enter | Exit |
|---|---|---|
| Forward (Home → sub-page) | `slide_in_right` | `fade_in` |
| Back (sub-page → Home) | `fade_in` | `slide_out_left` |
| Bottom nav tab switch | `fade_in` | — |
| Splash → Home | `fade_in` | `fade_in` |

## Activity Flow Diagram

```
SplashActivity
    ↓ (1.5s delay, CLEAR_TASK)
HomeActivity ← → EmergencyAlertActivity
    │             LogIncidentActivity
    │             SupportCircleActivity
    │             RecordsActivity
    │
    └─ ResourcesActivity
           ├── UnderstandAbuseActivity
           ├── EvidenceGuideActivity
           ├── FindHelpNearYouActivity
           └── BreakingStigmaActivity
```

## Related

- [[UI Components]] — Layout elements and theming
