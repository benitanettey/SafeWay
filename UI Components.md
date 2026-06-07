---
tags:
  - ui
  - layouts
  - theming
  - animations
---
# UI Components

## Theme & Styling

The app uses a **dark material theme** with custom colors:

### Color Palette

| Token | Usage |
|---|---|
| `primary_background` | Main background (#0D0D0D dark) |
| `card_background` | Card surfaces |
| `primary_accent` | Accent (selected chips, highlights) |
| `highlight_accent` | Active icons, indicators, links |
| `text_primary` | Primary text |
| `text_secondary` | Secondary text |
| `neutral_muted` | Inactive nav items |
| `neutral_text` | Less prominent text |
| `border_dark` | Card borders, chip strokes |
| `emergency_red` | Danger, delete actions |

### Layouts

All layouts are XML-based (`res/layout/`):

| Layout | Activity/Dialog |
|---|---|
| `activity_splash` | [[SplashActivity]] |
| `activity_home` | [[HomeActivity]] |
| `activity_emergency_alert` | [[EmergencyAlertActivity]] |
| `activity_log_incident` | [[LogIncidentActivity]] |
| `activity_records` | [[RecordsActivity]] |
| `activity_resources` | [[ResourcesActivity]] |
| `activity_support_circle` | [[SupportCircleActivity]] |
| `activity_understand_abuse` | [[UnderstandAbuseActivity]] |
| `activity_evidence_guide` | [[EvidenceGuideActivity]] |
| `activity_find_help_near_you` | [[FindHelpNearYouActivity]] |
| `activity_breaking_stigma` | [[BreakingStigmaActivity]] |
| `bottom_nav_layout` | Bottom nav bar (included in main layouts) |
| `dialog_add_contact` | Add contact dialog |
| `dialog_record_detail` | Record detail/edit dialog |
| `item_record_incident` | Record card in RecyclerView |
| `spinner_item` | Filter dropdown styling |
| `spinner_dropdown_item` | Dropdown expanded styling |

## Key UI Patterns

### Chip Selection (Incident Logger)
Mutual-exclusion chip group using Material `Chip` with custom styling:
- Selected: `primary_accent` background, bold text
- Unselected: `card_background` with `border_dark` stroke
- `isCheckedIconVisible = false`

### Record Card Chips (RecordsAdapter)
Dynamic chip styling based on content:
- **Highlighted** chips (hasPhoto, hasVoiceNote): `primary_accent` background
- **High/Crisis severity**: Dark red background, `emergency_red` text
- **Default**: `card_background` with `border_dark` stroke

### Hotline Cards (HomeActivity)
Custom-built cards in a `GridLayout`:
- 2-column grid with `GridLayout.spec(row, col)`
- Each card: icon + name, click to call
- Long-press reveals delete button (X) for user-added hotlines
- 4 default hotlines always present; custom ones appended below

### Contact Card (SupportCircleActivity)
Horizontal card with:
- Circular avatar (initials)
- Name / phone / relationship
- SMS alerts status chip
- Remove button with confirmation dialog

### Waveform Visualizations
Used in [[Incident Logging]] and [[Records & Export]]:
- Small vertical bars (4dp wide)
- Heights vary with amplitude/playback position
- Colors: `highlight_accent` with dynamic alpha
- Recording: live from microphone amplitude
- Playback: synthetic generation using sin waves

## Animations

See [[Navigation System]] for transition animations. Additional UI animations:
- Shield overlay expand/collapse (ValueAnimator)
- Live waveform updates (Handler-based)
- Playback progress ring (CircularProgressIndicator)

## Related

- [[Navigation System]] — Animations and routing
