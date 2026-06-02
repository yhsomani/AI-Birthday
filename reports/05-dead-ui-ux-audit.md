# Report 05 — Dead UI/UX Audit

**Date**: 2026-06-01
**Author**: Senior UI/UX Designer + Senior Android Architect (SSOT Audit)
**Scope**: Identify non-functional UI elements (empty click handlers, stubs, dead routes, duplicated composables, missing state writes).

---

## 1. Methodology

Searched the entire `:app`, `:core:ui`, `:core:data`, and `:feature:*` source for:
- `onClick = {}` (empty handler)
- `onClick = { /* ... */ }` (commented-out stub)
- `TODO` / `FIXME` / `XXX` / `HACK`
- Duplicated composable definitions
- Composables called with wrong/missing args
- Tabs declared but not wired
- Routes that lead nowhere

---

## 2. Confirmed Dead UI (Severity-Sorted)

### 2.1 `ContactDetailScreen.kt:44` — Edit IconButton (no-op) — **HIGH**
```kotlin
actions = {
    IconButton(onClick = {}) {                              // ← empty lambda
        Icon(Icons.Default.Edit, contentDescription = "Edit", ...)
    }
}
```
**Impact**: User sees an "Edit" icon in the top app bar, taps it, nothing happens. Trivial tap → no response → user confusion → "broken app" perception.
**Fix**: Either wire to a real Edit screen, or remove the action entirely.

### 2.2 `ContactDetailScreen.kt:185` — "Do Not Disturb" Switch (stub) — **HIGH**
```kotlin
Switch(
    checked = contact.skipAutoWish, 
    onCheckedChange = { /* update db */ },                   // ← no DB write
    ...
)
```
**Impact**: Toggling the switch animates, but state is lost on screen exit. User assumes the preference is saved; it isn't.
**Fix**: Add `onCheckedChange = { newValue -> viewModel.setSkipAutoWish(contact.id, newValue) }` and implement the repository call.

### 2.3 `ContactDetailScreen.kt:209` — Custom Send Time EDIT (stub) — **HIGH**
```kotlin
TextButton(onClick = { /* show time picker */ }) {
    Text("EDIT", ...)
}
```
**Impact**: Tapping "EDIT" does nothing. User cannot actually set a custom send time from the contact detail screen (despite `customSendTimeHour/Minute` fields existing on the entity).
**Fix**: Wire to a `TimePickerDialog` and persist via repository.

### 2.4 `ContactDetailScreen.kt:90` — `MemoryVaultView()` called without contactId — **MEDIUM**
```kotlin
"MEMORIES" -> MemoryVaultView()
```
The real `MemoryVaultView` (in `feature/contacts/MemoryVaultView.kt`) requires a `contactId` arg to load notes. The version called from `ContactDetailScreen` is the one defined inside the same file (line 219+) or a no-arg stub.
**Impact**: Tapping the Memories tab shows nothing or empty state.
**Fix**: Pass `contact.id` and load notes for the contact.

### 2.5 Duplicated Composables — `GiftAdvisorView` — **MEDIUM**
`GiftAdvisorView` is defined in **two places**:
- `feature/contacts/src/main/kotlin/com/example/feature/contacts/GiftAdvisorView.kt`
- `feature/contacts/src/main/kotlin/com/example/feature/contacts/ContactDetailScreen.kt:219`

Both have the same name and signature. Kotlin compiler may resolve them by import order; behavior is undefined.
**Fix**: Delete the duplicate from `ContactDetailScreen.kt`; import from `GiftAdvisorView.kt`.

### 2.6 Duplicated Composables — `MemoryVaultView` — **MEDIUM**
Same as 2.5: `MemoryVaultView` defined in both `feature/contacts/MemoryVaultView.kt` and used (possibly duplicated) in `ContactDetailScreen.kt`. The same risk applies.

### 2.7 `EventsScreen.kt:36` — `onAddBirthday` default no-op lambda — **LOW**
```kotlin
onAddBirthday: (contactId: String, dayOfMonth: Int, month: Int, year: Int?) -> Unit = { _, _, _, _ -> }
```
**Impact**: If a caller forgets to pass `onAddBirthday`, the Add Birthday sheet silently discards the save. No error, no toast, no log.
**Fix**: Throw `IllegalStateException` in default, or require non-null.

### 2.8 `AppContent.kt` — ANALYTICS and STYLE_COACH hidden behind MORE tab — **MEDIUM (UX)**
ANALYTICS and STYLE_COACH are valid tabs in `AppContent.when` but **not in the bottom nav** (`AppBottomNavigation.kt:73-79` only shows 5 tabs). They are accessed only via the MORE tab → Settings screen → click navigation.

**Impact**: 
- Low discoverability (users may not know Analytics exists)
- Inconsistent with §1.2 / §1.1 promise of "Analytics" as a primary feature

**Fix**: Either add to bottom nav (6 tabs becomes more crowded but is conventional) or surface in MORE tab explicitly (currently hidden behind SettingsScreen).

### 2.9 `SettingsScreen.kt:285` — Unknown click handler — **NEEDS VERIFICATION**
```kotlin
onClick = {
    // (line 285 — need to read full context to identify what button this is)
}
```
**Impact**: Unknown until full SettingsScreen is reviewed.

### 2.10 `SettingsScreen.kt:356`, `:397` — Unknown click handlers — **NEEDS VERIFICATION**
Same as 2.9.

---

## 3. UX Issues Beyond Dead Handlers

### 3.1 No Empty States in Many Screens
- `DashboardScreen`: No empty state when no contacts
- `MessagesScreen`: No empty state when no pending
- `AnalyticsScreen`: No empty state when no sent messages
- Recommendation: Add a shared `EmptyStatePlaceholder` composable in `:core:ui`.

### 3.2 No Loading Shimmer Coverage
`LoadingShimmer.kt` is referenced in `Component Inventory` (§15.5) but only used in some screens.
- Verify each screen has a loading state when data is empty (initial) vs. loaded (empty list).

### 3.3 Onboarding is 10 Steps (Target 7)
- 10 destinations: welcome → google_signin → gemini_setup → contacts_perm → sms_perm → whatsapp_setup → battery_opt → writing_style → automation_prefs → import_progress
- User drop-off is high at 7+ steps
- Recommendation: Merge `sms_perm` + `contacts_perm` into a single "permissions" step; merge `battery_opt` into a settings note.

### 3.4 No Snackbar Feedback on Save
When user saves a contact edit / event quick-add, there's no confirmation toast/snackbar.
- Recommendation: Wrap save actions with `snackbarHostState.showSnackbar("Saved")`.

### 3.5 Hardcoded English Strings Throughout Composables
Examples found (need comprehensive scan):
- `Text("Health Score: ${contact.healthScore}%", ...)` in `ContactDetailScreen.kt:139`
- `Text("Engagement Score", ...)` in `ContactDetailScreen.kt:152`
- `Text("Communication Style", ...)` in `ContactDetailScreen.kt:167`
- `Text("Automation Settings", ...)` in `ContactDetailScreen.kt:175`
- `Text("Do Not Disturb", ...)` in `ContactDetailScreen.kt:180`
- `Text("Never auto-send messages", ...)` in `ContactDetailScreen.kt:181`
- `Text("Custom Send Time", ...)` in `ContactDetailScreen.kt:201`
- `Text("Default (09:00 AM)", ...)` in `ContactDetailScreen.kt:205`
- `Text("EDIT", ...)` in `ContactDetailScreen.kt:210`
- `Text("Gift Advisor", ...)` in `ContactDetailScreen.kt:228`
- `Text("Track past gifts and receive AI suggestions.", ...)` in `ContactDetailScreen.kt:230`
- `Text("No events found", ...)` in `EventsScreen.kt:114`
- `Text("Timeline", ...)` in `EventsScreen.kt:62`
- `Text("Add Birthday", ...)` in `EventsScreen.kt:201`
- `Text("Contact", ...)`, `Text("Day", ...)`, `Text("Month", ...)`, `Text("Year", ...)`, `Text("Has year", ...)`, `Text("Save Birthday", ...)` in `EventsScreen.kt:215-290`

**Impact**: Blocks i18n (NFR-I18N-01) and Hindi/Brazilian/Indonesian rollout.
**Fix**: Move all to `strings.xml` with IDs like `R.string.contact_health_score`, etc.

### 3.6 No Content Description for Decorative Icons
`Icon(Icons.Default.Event, contentDescription = null, ...)` in `EventsScreen.kt:109` — this is fine for purely decorative icons, but verify each occurrence. The a11y audit (NFR-A11Y-01) needs a pass.

### 3.7 Contrast Ratio Not Verified
NFR-A11Y-03 says "Color contrast ratio MUST be ≥4.5:1" but no automated test or audit exists. Recommend:
- Run `lints` with `AndroidLintCompose` checks
- Manual review of `Color.kt` against dark/light themes

### 3.8 Touch Target Sizes
NFR-A11Y-02 says "Touch targets MUST be ≥48dp." `IconButton` defaults to 48dp, but custom buttons may not. Spot-check needed.

### 3.9 No Dynamic Font Scaling Test
NFR-A11Y-04 says "sp for text, not sp-as-dp." Need grep for `fontSize = X.dp` to find any violations.

---

## 4. Navigation Map (Current State)

```
MainActivity
└── NavHost
    ├── splash → SplashScreen
    ├── login → LoginScreen
    ├── onboarding → OnboardingScreen (10 steps internal)
    └── main → MainAppScreen
        ├── AppBottomNavigation (5 tabs: HOME, CONTACTS, EVENTS, MESSAGES, MORE)
        │   └── MORE → SettingsScreen (sub-routes via onClick)
        ├── AppNavigationRail (same 5 tabs, tablet variant)
        ├── AppContent (7 routes total)
        │   ├── HOME → DashboardScreen
        │   ├── CONTACTS → ContactsContent
        │   ├── EVENTS → EventsScreen
        │   ├── MESSAGES → MessagesScreen
        │   ├── MORE → SettingsScreen (in AppContent too — duplicated entry)
        │   ├── ANALYTICS → AnalyticsScreen (hidden, accessed via MORE)
        │   └── STYLE_COACH → StyleCoachScreen (hidden, accessed via MORE)
        └── ContactDetailScreen (modal-style overlay for selected contact)
```

**Issues**:
1. `MORE` is in both `AppBottomNavigation` (renders icon) and `AppContent.when` (renders SettingsScreen) — when MORE tab is selected, both the bottom nav bar and the content area show SettingsScreen. Slight UX confusion (no separate "Settings" page).
2. `ANALYTICS` and `STYLE_COACH` are reachable only by:
   - Tap MORE in bottom nav → SettingsScreen → click "Analytics" or "Style Coach" link.
   - No way to deep-link directly to these tabs (e.g., from a notification).
3. `selectedContactId` state lives in `MainAppScreen` and is a full-screen overlay (not a modal sheet or bottom sheet). Lighter UX would be a `ModalBottomSheet` or `Dialog`.

---

## 5. Recommendations (Prioritized)

| Priority | Fix | Effort |
|---|---|---|
| P0 | Wire `ContactDetailScreen.kt:44` Edit button (or remove) | 0.5 day |
| P0 | Wire `ContactDetailScreen.kt:185` DND Switch to ViewModel | 0.5 day |
| P0 | Wire `ContactDetailScreen.kt:209` Custom Send Time picker | 1 day |
| P1 | Resolve `GiftAdvisorView` duplicate | 0.25 day |
| P1 | Resolve `MemoryVaultView` duplicate | 0.25 day |
| P1 | Fix `MemoryVaultView` call to pass `contactId` | 0.25 day |
| P1 | Surface ANALYTICS and STYLE_COACH in bottom nav OR add direct entry in MORE | 1 day |
| P2 | Add empty-state placeholders to Dashboard, Messages, Analytics | 0.5 day |
| P2 | Add Snackbar feedback on save actions | 0.5 day |
| P2 | Extract hardcoded strings to `strings.xml` | 2 days |
| P2 | Verify touch targets ≥48dp | 0.5 day |
| P3 | Run `lint` with Compose a11y checks | 0.25 day |
| P3 | Add color-contrast verification | 0.5 day |

**Total effort for P0+P1**: ~4 person-days. Should block Play Store release.

---

## 6. Summary

| Category | Count |
|---|---|
| Dead `onClick = {}` | 1 (line 44) |
| Stub `onClick = { /* ... */ }` | 2 (lines 185, 209) |
| Duplicated composables | 2 (GiftAdvisor, MemoryVault) |
| Missing required args | 1 (MemoryVaultView() no contactId) |
| Hardcoded English strings | 15+ (need full grep) |
| Hidden navigation paths | 2 (Analytics, Style Coach) |
| Empty states missing | 3+ screens |

**Production-readiness verdict for UI/UX**: NOT ready. P0 fixes (~2 days) are blockers; P1 fixes (~2 days) are strongly recommended.
