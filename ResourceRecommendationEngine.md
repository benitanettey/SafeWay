---
tags:
  - recommendation
  - routing
  - resource
---
# ResourceRecommendationEngine

**File:** `com.example.safeway.ResourceRecommendationEngine`

A lightweight recommendation engine that routes users to relevant educational resources based on the type of incident they logged.

## Logic

```kotlin
enum class ResourceDestination {
    UNDERSTAND_ABUSE,
    EVIDENCE_GUIDE,
    FIND_HELP_NEARBY,
    BREAK_STIGMA
}
```

### Primary Destination

Returns the single most relevant resource:

| Incident Type | Primary Destination |
|---|---|
| `physical` | Evidence Guide |
| `sexual` | Evidence Guide |
| `verbal` | Understand Abuse |
| `financial` | Understand Abuse |
| `neglect` | Understand Abuse |
| *(default)* | Understand Abuse |

### Additional Suggestions

Returns a list of recommended resources:

| Incident Type | Suggestions |
|---|---|
| Physical, Sexual | Evidence Guide, Find Help Near You |
| Verbal, Financial, Neglect | Understand Abuse, Breaking the Stigma |
| *(default)* | Understand Abuse, Find Help Near You |

## Integration

Called from [[LogIncidentActivity]] after a successful save:

```kotlin
val destination = ResourceRecommendationEngine.primaryDestinationFor(selectedType)
openSuggestedResource(destination)
```

## Related

- [[Incident Logging]] — Where recommendations are used
- [[Resources Hub]] — Available resource activities
