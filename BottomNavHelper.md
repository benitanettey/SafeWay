---
tags:
  - navigation
  - bottom-nav
---
# BottomNavHelper

**File:** `com.example.safeway.BottomNavHelper`

A custom bottom navigation bar implementation.

## Tabs

```kotlin
enum class NavTab { HOME, LOG, CIRCLE, RECORDS }
```

## How It Works

`setup(activity, activeTab)`:
1. Finds the 4 tab containers (`nav_home`, `nav_log`, `nav_circle`, `nav_records`)
2. For each tab, sets icon color and indicator width based on active state
3. Wires click listeners for inactive tabs → launch the target activity

## Active Tab Highlighting

Each tab has:
- An icon `ImageView` — color filtered to `highlight_accent` (active) or `neutral_muted` (inactive)
- An indicator `View` — 20dp wide when active, 0dp when inactive
- A label `TextView` — bold when active, normal weight when inactive

## Navigation

Inactive tabs launch their target with:
```kotlin
intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP)
```
This ensures proper back stack management (no duplicate activities).

## Layout

The bottom nav is defined in `bottom_nav_layout.xml` and included in each main activity layout.

## Related

- [[Navigation System]] — Full navigation overview
