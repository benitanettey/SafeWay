---
tags:
  - resources
  - education
  - help
---
# Resources Hub

**File:** `com.example.safeway.ResourcesActivity`

A central hub connecting users to educational content and help resources.

## Resource Cards

| Card | Target Activity | Purpose |
|---|---|---|
| **Understand Abuse** | [[UnderstandAbuseActivity]] | Educational content on abuse types |
| **Evidence Guide** | [[EvidenceGuideActivity]] | Guide on collecting evidence |
| **Find Help Near You** | [[FindHelpNearYouActivity]] | Local services with call/directions |
| **Breaking the Stigma** | [[BreakingStigmaActivity]] | Anti-stigma awareness |

## Activity Details

### UnderstandAbuseActivity
- Lists buttons for each abuse type (Physical, Verbal, Financial, Sexual, Psychological)
- Each button opens [[LogIncidentActivity]] directly — shortcuts to logging

### EvidenceGuideActivity
- Educational content about evidence collection
- **"Start Recording Evidence"** button → opens [[LogIncidentActivity]]

### FindHelpNearYouActivity
Four service categories with **Call** and **Directions** buttons:

| Service | Phone | Maps Query |
|---|---|---|
| Hospital | 112 | Nairobi Hospital Emergency |
| Counseling | 0724999999 | Counseling Center Nairobi |
| Legal Aid | 0800123456 | Amnesty International Kenya |
| Police | 999 | Police Gender Desk Nairobi |

- **Call:** Opens dialer via `ACTION_DIAL` intent
- **Directions:** Opens Google Maps with query

### BreakingStigmaActivity
- Minimal activity — layout only (no programmatic features)

## Resource Recommendation Engine

**File:** `com.example.safeway.ResourceRecommendationEngine`

After logging an incident, the engine suggests the most relevant resource:

```kotlin
primaryDestinationFor("physical")     → EVIDENCE_GUIDE
primaryDestinationFor("verbal")       → UNDERSTAND_ABUSE
primaryDestinationFor("sexual")       → EVIDENCE_GUIDE
primaryDestinationFor("financial")    → UNDERSTAND_ABUSE
primaryDestinationFor("neglect")      → UNDERSTAND_ABUSE
```

See [[Incident Logging]] for the post-save flow.

## Navigation

All resource activities use `BottomNavHelper.setup(this, NavTab.HOME)` for bottom navigation (Home tab highlighted) with a back button for toolbar navigation.

## Related

- [[Incident Logging]] — Post-log recommendations
- [[HomeActivity]] — Default hotline buttons
- [[Navigation System]] — How back navigation works
