# AI-Birthday — Current UI/UX Audit & Layout Defect Analysis

**Date:** October 4, 2026  
**Auditor:** Principal Product Designer, Senior UI/UX Engineer & Flutter Architect  
**Scope:** Full application inspection across `lib/`, `android/`, `test/`, theme, components, and responsive behaviors.

---

## 1. Executive Summary

This audit assesses the visual design, responsive layout, component architecture, and accessibility of the AI-Birthday Flutter application prior to redesign. While the functional core (birthday calculation engine, local repositories, Gemini AI routing, and WhatsApp intent handoff) is operational, the user interface suffers from layout fragility, component overlap risks, inconsistent spacing, weak hierarchy, and divergent screen implementations.

---

## 2. Global Design System & Theming Deficiencies

### 2.1 Lack of Cohesive Reusable Design System Tokens
- **Hardcoded Colors**: Screen widgets frequently hardcode hex values like `Color(0xFFA64B2A)`, `Color(0xFFD9822B)`, `Color(0xFF2D5A46)`, and `Colors.grey[600]` rather than referencing semantic theme tokens (`colorScheme.primary`, `colorScheme.secondary`, `colorScheme.outlineVariant`, etc.).
- **Missing Shared Component Library**: Aside from a basic `EmptyState` widget, there are no reusable design system components for birthday cards, action tiles, countdown badges, responsive button rows, section headers, or modal containers. Each screen re-implements its own ad-hoc cards and styles.
- **Inconsistent Elevation and Shape**: Radii fluctuate across screens between 6px, 8px, 10px, 12px, 16px, and 20px without a standard token hierarchy.

### 2.2 Typography Hierarchy
- Screen titles, section labels, and card headers use varying combinations of `titleLarge`, `titleMedium`, `headlineSmall`, or raw `TextStyle` definitions.
- Uppercase tracking (`letterSpacing: 0.8`) is applied manually on some headers while omitted on others.

---

## 3. Screen-by-Screen Layout & UX Audit

### 3.1 Dashboard (`DashboardScreen` / `HomeScreen`)
- **Divergence**: Two competing screen implementations exist: `DashboardScreen` (routed in `router.dart`) and `HomeScreen` (tested in unit tests). Both must be unified under a single authoritative design system.
- **Overlapping / Squeezed Action Buttons**: In `DashboardScreen._buildActionCard`, the card contains:
  ```dart
  Row(
    mainAxisAlignment: MainAxisAlignment.spaceBetween,
    children: [
      Text('Channel: ...'),
      FilledButton.icon(
        icon: Icon(Icons.arrow_forward),
        label: Text('Review & Send'),
      ),
    ],
  )
  ```
  On narrow viewports (320dp–360dp) or under large font scaling (1.3x–1.5x), the channel text and button collide, pushing the button off-screen or causing `RenderFlex overflowed by xx pixels on the right`.
- **Above-The-Fold Density**: The command header provides basic counts, but lacks visual breathing room, clear status separation, and dynamic date formatting in dark mode.
- **Empty States**: If no upcoming birthdays exist, a generic card with grey text renders without an intuitive call-to-action button to add a contact.

### 3.2 People List & Management (`PeopleScreen` / `PersonListScreen`)
- **ListTile Trailing Collision**: In `PeopleScreen`, the trailing widget of each contact card combines a countdown badge container and a `PopupMenuButton`:
  ```dart
  trailing: Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(... child: Text(_countdownLabel(next))),
      PopupMenuButton<String>(...),
    ],
  )
  ```
  On screens with large font scale or contacts with long names and multiple facts, this large trailing block squashes the title and subtitle, causing text wrapping or vertical overflow.
- **Floating Action Button (FAB) Obstruction**: The FAB sits directly over the bottom-most list item. If the list is scrolled to the end, the last contact is obscured by the FAB.
- **Modal Bottom Sheet Overflow**: In `_showPersonDetailsModal`, the modal bottom sheet is built with:
  ```dart
  Padding(
    padding: EdgeInsets.all(24),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        ...
        ...person.importantFacts.map(...),
        ...action buttons
      ],
    ),
  )
  ```
  If a contact has 4 or more facts, or when viewed in landscape mode or on a compact screen (e.g. 640px height), this unconstrained `Column` exceeds the viewport height and produces an immediate **`RenderFlex overflowed by xxx pixels on the bottom`** crash! It lacks a `SingleChildScrollView` and bottom safe area padding.

### 3.3 Add / Edit Person Form (`PersonFormScreen`)
- **Dropdown Row Squeeze**: The Birthday Month and Birthday Day dropdowns are placed inside a `Row` with `Expanded`. On narrow devices or when large text accessibility is enabled, the month names ("September", "November") clip or trigger layout warnings.
- **Save Button Hardcoded Height**: The save button at the bottom has a fixed `height: 48`. At text scales $\ge 1.3\times$, the label "Save" clips vertically.
- **Keyboard Viewport Ingestion**: The form is wrapped in a `ListView`, but when soft input (IME) is displayed, focused inputs near the bottom do not automatically scroll into clear view without proper bottom view insets handling.
- **Visual Chunking**: The progressive disclosure cards (ExpansionTiles) use the same flat card style as the essential inputs card, creating visual monotony.

### 3.4 Calendar Screen (`CalendarScreen`)
- **Grid Cell Constraint Fragility**: The calendar uses `Expanded` inside a `Column` for weeks and `Expanded` inside a `Row` for days. On smaller screens or when orientation rotates to landscape, the cell height becomes smaller than the combined height of the day number circle and birthday indicator dots, causing render overflow.
- **Day Modal Sheet**: The day celebration modal displays a simple list without avatar styling, actions, or direct deep navigation into the Message Studio.

### 3.5 Message Studio Screen (`MessageStudioScreen`)
- **Action Buttons Horizontal Overflow (CRITICAL)**: At the bottom of `MessageStudioScreen`:
  ```dart
  Row(
    children: [
      Expanded(child: FilledButton.tonalIcon(label: Text('Generate with AI'))),
      SizedBox(width: 12),
      Expanded(child: FilledButton.icon(label: Text('Send on WhatsApp'))),
    ],
  )
  ```
  This two-column layout cannot fit both verbose button labels and icons on standard 360dp Android devices without horizontal overflow. At 1.2x font scale, it overflows by ~28–45 pixels.
- **Editor Height Under Virtual Keyboard**: The message editing `TextField(maxLines: 5)` does not adapt when the keyboard opens, pushing the action buttons entirely off-screen.
- **Chip Overcrowding**: Tone chips (`Wrap(spacing: 8)`) and Length chips wrap onto multiple uneven lines with no clear grouping or visual feedback.

### 3.6 History & Activity Screen (`HistoryScreen`)
- **Card Action Alignment**: Each activity card displays a date, body preview, status chip, copy button, and studio button. The status chip and timestamp can collide when the recipient name is long.
- **Feedback & Interaction**: Tap targets for the "Copy" and "Studio" buttons are slightly below standard height when text expands.

### 3.7 Settings Screen (`SettingsScreen`)
- **Visual Clutter**: The settings screen groups account, subscription, API keys, on-device AI, reminders, and appearance into cards, but lacks consistent icon treatment, padding tokens, and surface color separation.
- **API Key Text Field Jumpiness**: Entering or deleting an API key shifts the card layout abruptly.
- **Pro Upgrade CTA**: The Pro subscription upgrade banner inside Settings is visually indistinguishable from standard settings tiles.

---

## 4. Layout & Responsive Root Causes

| Issue Type | Underlying Technical Root Cause | Proposed Architecture Fix |
| :--- | :--- | :--- |
| **Horizontal Button Overflow** | Unconstrained `Row` with two verbose `Expanded(FilledButton.icon)` | Introduce responsive `ActionButtonBar` that automatically switches to a stacked vertical column on compact widths or large font scales. |
| **Bottom Sheet Clipping** | Unconstrained `Column(mainAxisSize: MainAxisSize.min)` inside modal bottom sheet | Wrap modal body in `Flexible` + `SingleChildScrollView(physics: BouncingScrollPhysics())` with `SafeArea(bottom: true)`. |
| **Dropdown Cell Squeeze** | Two `DropdownButtonFormField` inside a `Row` without min-width check | Use responsive layout or adaptive DatePicker bottom sheet with dedicated day/month controls. |
| **FAB Obstructing Content** | List views lack bottom scroll clearance | Add standard `SizedBox(height: 80)` or `padding: EdgeInsets.only(bottom: 88)` to all scrollable views. |
| **Calendar Cell Overflow** | Fixed nested `Expanded` widgets without minimum cell aspect ratio | Use `GridView` with `SliverGridDelegateWithFixedCrossAxisCount` and flexible vertical scroll when constraints are tight. |
| **Inconsistent Theming** | Direct `Color(0x...)` instantiation in presentation widgets | Centralize all tokens in `AppColors`, `AppTypography`, `AppSpacing`, and `AppTheme`. |

---

## 5. Accessibility Audit (WCAG 2.1 AA & Android Guidelines)

1. **Touch Targets**: While major buttons meet 48dp, several text buttons (`TextButton.icon`), chip selections, and inline icons have tap targets under 44x44dp.
2. **Text Scaling Support**: Fixed-height containers (`height: 48` on Save button) break when Android system text size is increased to 1.3x–1.5x.
3. **Contrast Ratios**: Status badges with light opacity backgrounds (`Colors.grey.withValues(alpha: 0.12)`) and light grey text fail the 4.5:1 contrast ratio requirement in light mode.
4. **Semantics**: Calendar cells, tone chips, and status badges need explicit semantic labels for screen readers (TalkBack).

---

## 6. Functional Preservation Invariant List

The redesign must strictly preserve:
- ✅ Reactive broadcast streams across `peopleStreamProvider`, `birthdaysStreamProvider`, and `draftsStreamProvider`.
- ✅ Contact CRUD with soft-delete, confirmation dialog, and instant SnackBar Undo restoration.
- ✅ Next birthday, leap year (Feb 29 -> Feb 28), and countdown calculations.
- ✅ Live Gemini 2.5 Flash Lite BYOK API generation with multi-tone synthesis and prompt injection defense.
- ✅ Offline template fallback when network is unavailable.
- ✅ WhatsApp Click-to-Chat intent dispatch (`whatsapp://send?text=...`) and post-launch sent confirmation modal.
- ✅ History activity timeline with clipboard copy and Message Studio deep navigation.
- ✅ Reminder scheduling with exact alarms, notification channels, and quiet hours shift.
- ✅ Hardware-backed credential encryption and automatic PII log scrubbing.
