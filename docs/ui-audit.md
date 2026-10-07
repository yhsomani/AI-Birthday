# AI-Birthday — UI Audit (Phase 0)

**Date:** October 7, 2026
**Purpose:** Baseline audit before the "Confetti, but grown-up" UI/UX redesign (9 phases).
**Method:** Full source inspection of `lib/`, `test/`, docs; live device verification on Redmi (Android 15, 1080×2400, device `1b87b5db`); baseline gates run on this commit.

---

## 1. Baseline (gate = green before any redesign code)

| Gate | Result |
|---|---|
| `flutter analyze` | **No issues found** (77.8s) |
| `flutter test` | **235 passed** (38s) |
| `dart format` | clean (0 files changed as of prior run) |
| Cold start (release-debug APK, `am start -W`, ×3) | **2606 / 2018 / 1801 ms** → median 2018 ms, best 1801 ms |
| Backend `npm test` / `npm run lint` | 75 passed / clean (unchanged by this work) |
| Screenshots "before" | 20 PNGs → `docs/screenshots/before/` (index in §9) |

Build under test: `app-debug.apk` built from commit `34e1b32` (`fix: surface real Google sign-in errors…`).

---

## 2. App structure: routes, shell, screens

### 2.1 Router (`lib/app/router.dart`, go_router 14)

| Path | Screen | Mounted |
|---|---|---|
| `/`, `/home`, `/birthdays` | redirects → `/dashboard`, `/people` | top-level (**never navigated to — dead aliases**) |
| `/onboarding` | `OnboardingScreen` | above shell (root navigator) |
| `/message-studio/:birthdayId`, `/message-studio/person/:personId` | `MessageStudioScreen` | above shell |
| `/people/add`, `/people/edit/:id` | `PersonFormScreen` | above shell |
| `/dashboard` | `DashboardScreen` | shell branch 0 |
| `/people` | `PeopleScreen` | branch 1 |
| `/calendar` | `CalendarScreen` | branch 2 |
| `/history` | `HistoryScreen` | branch 3 |
| `/settings` | `SettingsScreen` | branch 4 |

Onboarding guard: redirect to `/onboarding` until `credentialStorage.hasCompletedOnboarding()` (skipped when storage is null in tests). `AppScaffold` = Material 3 `NavigationBar` (5 destinations, haptic on tap) + notification deep-link into Message Studio (`getInitialNotificationPersonId`, `setNotificationOpenedHandler`).

**Dead code found:**
- `AppTheme.lightTheme()` / `AppTheme.darkTheme()` legacy wrappers — 0 callers.
- `lib/shared/design_system/app_typography.dart` — 0 importers (whole file dead; wraps `GoogleFonts.outfit`).
- Router aliases `/`, `/home`, `/birthdays` — never navigated from code or tests.
- The stale docs' "DashboardScreen vs HomeScreen / PeopleScreen vs PersonListScreen duplication" no longer exists — exactly 8 screen classes, all live. `docs/ui-ux/*` is wrong on that point.

### 2.2 Screen inventory (8 screens + 1 sheet)

| # | Screen | File | Lines | Providers read |
|---|---|---|---|---|
| 1 | Dashboard | `lib/features/dashboard/presentation/dashboard_screen.dart` | 534 | watch `birthdaysStreamProvider`, `peopleStreamProvider` |
| 2 | People | `lib/features/people/presentation/people_screen.dart` | 935 | watch `peopleStreamProvider`; read `contactCsvServiceProvider`, `nativeShareServiceProvider`, `peopleRepositoryProvider`, `birthdaysRepositoryProvider`, `deviceContactsServiceProvider`, `personServiceProvider` |
| 3 | Person form (add/edit) | `lib/features/people/presentation/person_form_screen.dart` | 717 | `personByIdProvider(id)`, `peopleRepositoryProvider`, `personServiceProvider`, `birthdaysRepositoryProvider` |
| 4 | Calendar | `lib/features/calendar/presentation/calendar_screen.dart` | 313 | watch `personListProvider` (**different source than every other screen**) |
| 5 | Message Studio | `lib/features/message_studio/presentation/message_studio_screen.dart` | 1109 | `birthdaysRepositoryProvider`, `peopleRepositoryProvider`, `draftsRepositoryProvider`, `aiRouterProvider`, `entitlementProvider`, `whatsappHandoffBuilderProvider`, `smsDeliveryServiceProvider`, `nativeShareServiceProvider`, `loggerProvider` |
| 6 | History | `lib/features/history/presentation/history_screen.dart` | 218 | watch `draftsStreamProvider`, `peopleStreamProvider` |
| 7 | Settings | `lib/features/settings/presentation/settings_screen.dart` | 1524 | watch `reminderSettingsProvider`, `entitlementProvider`, `authControllerProvider`; read `credentialStorageProvider`, `geminiNanoPlatformProvider`, `cloudSyncServiceProvider`, `notificationSchedulerGatewayProvider`, `userGeminiApiProvider`, `subscriptionNotifierProvider.notifier` |
| 8 | Onboarding | `lib/features/onboarding/presentation/onboarding_screen.dart` | 399 | `credentialStorageProvider` (writes `setCompletedOnboarding`) |
| 9 | Auth bottom sheet | `lib/features/auth/presentation/auth_bottom_sheet.dart` | 297 | `authControllerProvider.notifier.signIn()` — opened from Settings only |

Data plumbing: `birthdaysStreamProvider` runs `BirthdayLifecycleService().refresh(people, birthdaysRepo)` before yielding the `birthdays` table stream — birthday cycle rows are derived from `persons` automatically.

---

## 3. UI states per screen

Legend: ✅ exists · ⚠️ exists but weak · ❌ missing

| Screen | Loading | Empty | Error | Populated | Offline | Permission |
|---|---|---|---|---|---|---|
| Dashboard | ⚠️ plain `CircularProgressIndicator` ×2 | ⚠️ hand-rolled header + "No upcoming birthdays" card; no `EmptyState`, no illustration | ⚠️ static text, **no retry action** | ✅ | ❌ | ❌ |
| People | ⚠️ spinner | ⚠️ hand-rolled column, grey icon | ⚠️ static text, no retry | ✅ | ❌ | ⚠️ via dialog + snackbar only (contacts import) |
| Person form | ❌ no load indicator for existing person (silent populate; missing id → snackbar + pop) | n/a | ⚠️ inline field validation ✅ but no load error state | ✅ (saving spinner on button) | ❌ | ❌ |
| Calendar | ⚠️ spinner | ❌ bare grid, no message when month is empty | ⚠️ terse "Could not load birthdays.", no retry | ✅ | ❌ | ❌ |
| Message Studio | ⚠️ full-screen spinner | ❌ empty editor is default, no empty guidance | ✅ inline banner with Retry / Write manually / AI Settings actions | ✅ | ❌ (AI offline fallback happens inside `AiRouter`, never surfaced) | ❌ |
| History | ⚠️ spinner | ✅ `EmptyState` (only screen using it) | ⚠️ static text, no retry | ✅ | ❌ | ❌ |
| Settings | ✅ tile "Checking…" + 3 inline button spinners | n/a | ✅ two error systems (reminder sync banner + `GeminiConnectionStatus` banner + ~10 snackbars) | ✅ | ⚠️ only `networkUnavailable` banner for Gemini | ⚠️ notification + exact-alarm handled; no global denied state |
| Onboarding | n/a (static pages) | n/a | n/a | ✅ 3 pages | ❌ | ⚠️ permission requested? **No — current onboarding never asks** (redesign will request notifications in context) |
| Auth sheet | ✅ in-button spinner | n/a | ✅ error banner | ✅ | ❌ | ❌ |

**Global gaps:** no screen has an offline state (only Settings' Gemini banner); no list uses skeletons (0 in repo); no screen has permission-denied *screen* states.

---

## 4. Hardcoded strings and colors

### Colors (per file, line counts containing the token)

| File | `Colors.*` | raw `Color(0x…)` | `AppColors.*` (existing tokens) |
|---|---|---|---|
| settings_screen.dart | **61** | 0 | 26 |
| people_screen.dart | 17 | 0 | 7 |
| onboarding_screen.dart | 15 | 0 | 13 |
| message_studio_screen.dart | 13 | 0 | 3 |
| auth_bottom_sheet.dart | 10 | **1** (`0xFF1F1F1F`) | 4 |
| dashboard_screen.dart | 7 | 0 | 5 |
| person_form_screen.dart | 5 | 0 | 1 |
| history_screen.dart | 4 | 0 | **0** (status colors `Colors.green[800]/orange[800]/teal[800]/grey[700]` raw) |
| calendar_screen.dart | 2 | 0 | 2 |
| app_theme.dart | — | **2** (`0xFF1C1917` AppBar foreground) | — |
| `app_colors.dart` (token file) | — | 26 (tokens themselves) | — |

Examples: `Colors.amber.withValues(alpha: 0.2)` duplicate badge, `Colors.brown` badge, `Colors.grey[600]` subtitles throughout, `Colors.white` on WhatsApp button, `Colors.green` "Saved" text.

**Spacing/shape:** no shared scale in use at call sites — `SizedBox(height: …)` lines: settings 31, people 20, studio 19, form 16, dashboard 15, onboarding 15; `EdgeInsets` magic numbers everywhere (settings 19, dashboard 10, studio 10…). Radii scatter 6/8/10/12/16/20 across screens (old audit's finding, still true).

**Strings:** every user-facing string is a hardcoded literal in widgets (no ARB, no `gen-l10n`, no `l10n.yaml` in repo; `intl` is used only for date formatting). Some literals leak internals: `'User-provided facts to personalize drafts (SSOT §21)'` renders `§21` in the UI (confirmed on device). Emoji appear in status copy (`'Birthday is today! 🎂'`, `'🎉 Birthday Reminder Test'`, snackbar `'… 🎉'`).

---

## 5. Accessibility gaps

- **`Semantics(` in all of `lib/`: 2** (calendar day cell, `EmptyState`). **`semanticHeader`: 0.**
- Section headers are bare `Text` everywhere: Dashboard ("Action Needed", "Today's Birthdays", "Upcoming Birthdays", "QUICK ACTIONS"), Settings (`_SectionHeader` for Account/Backup/Subscription/AI/Reminders/Help), Form ("ESSENTIAL INFORMATION"), History timeline.
- Tooltips: calendar 2, people 5, dashboard 1, studio 1, form 1, settings 1; **onboarding 0, history 0, auth sheet 0**.
- Onboarding page dots: bare `AnimatedContainer`s — page position never announced.
- Message Studio: `'Your message'` is a plain `Text` above the `TextField` — screen readers get no field name (device dump confirms both EditTexts report `desc=[]`).
- Settings: status chips (`FREE TIER`, `UNLOCKED`, `NOT AVAILABLE`) are color + ALL-CAPS text with no semantics container; `Sign in` button nested inside tappable ListTile = duplicate tap targets.
- Tappable `CelebrationCard`/variation cards (`InkWell`) have no button semantics of their own.
- Fixed-height text risks: `Size.fromHeight(48)` Save button (form), `SizedBox(height: 52)` Google button (auth), `navigationBarTheme height 68`.
- Only Calendar ships real per-day semantics: "Day 7, 1 birthday" (verified on device).
- Old audit's sub-44dp targets and 1.5× overflow defects → superseded by `ResponsiveActionBar`/theme 48dp defaults in most places, but **no guideline test exists** (`androidTapTargetGuideline` etc. not used anywhere) so regressions are unguarded.

---

## 6. Tests that touch UI (what will break when screens are rewritten)

**Widget-pumping tests (10 files, 27 `testWidgets` cases):**

| Test file | Pumps | Asserts on |
|---|---|---|
| `test/widget_test.dart` (2) | full `AiBirthdayApp` + router + shell | onboarding `'Never miss a birthday that matters.'`, `'Next'`, `'Skip'`; after onboarding `'AI-Birthday'`, `themeMode == ThemeMode.system`, nav labels `Dashboard/People/Settings` |
| `onboarding_screen_test.dart` (3) | `OnboardingScreen` on mini router | 14 exact strings incl. `'Remember → Prepare → Personalize → Review → Send'`, `'Local by Default'`, `'Get Started'`; `hasCompletedOnboarding()` |
| `history_screen_test.dart` (1) | `HistoryScreen` | `'Opened in WhatsApp'`, `'Sent'`, `'Removed contact'` ×2 |
| `calendar_screen_test.dart` (4) | `CalendarScreen(now: …)` | `'February 2026'`, `'Mon'/'Sun'`, `'Birthdays Feb 5'`, `'2025 · turns 1'`, Feb-29→28 behavior, tooltips `Next/Previous month` |
| `people_screen_delete_test.dart` (1) | `PeopleScreen` + in-memory Drift | `'Ana'`, tooltip `'Person actions'`, menu `Delete`, snackbar `'Deleted Ana.'` + `'Undo'` |
| `settings_auth_test.dart` (3) | `SettingsScreen` + `FakeAuthGateway` | `'Sign in with Google'`, `'Not configured for this build'`, sheet `'Continue with Google'`, sign-out flow |
| `settings_reminders_test.dart` (5) | `SettingsScreen` | section titles, `SwitchListTile` labels, quiet-hours picker; **asserts `'Dark Mode'` is ABSENT** (redesign must update this deliberately — Appearance section is required by the new spec) |
| `settings_gemini_onboarding_test.dart` (7) | `SettingsScreen` | ~40 exact strings incl. guide sheet steps, banner copy, `'Paste your Gemini API key'` |
| `tier1/tier2/tier4` e2e (5) | `DashboardScreen` at 360dp/1.5× | renders OR tolerates `'RenderFlex overflowed'` (i.e. the suite currently accepts overflow — redesign should tighten to zero-overflow) |

**No test uses `find.byKey`** — all finders are text/type/tooltip. Screens carry no test keys.
**Zero UI test coverage:** `PersonFormScreen`, `MessageStudioScreen`, `AuthBottomSheet`, `AppScaffold` navigation.

Non-widget tests (≈200 of the 235): pure domain/service/repository — unaffected by redesign.

---

## 7. Live-device findings (defects discovered while capturing the baseline)

All three verified on device at **Wed Oct 7, 01:15–01:25 IST 2026** (device local) — presentation-layer, in scope for the redesign:

1. **People countdown runs on UTC, Dashboard on local time.**
   `BirthdayEngine.computeNext` defaults its reference to `DateTime.now().toUtc()` (`birthday_engine.dart:122`) and `PeopleScreen._next()` passes no `reference`. On device: Dashboard says Asha (Oct 7) = "Birthday is today!" while People says "Tomorrow" and "In 9 days" for Oct 15. Off-by-one for everyone between local midnight and UTC midnight (and the mirror case for zones behind UTC). Fix belongs in the caller (pass a local reference / read the same source Dashboard uses) — engine untouched.
2. **History timestamps render in UTC.** Draft created 01:19 IST Oct 7 shows "Oct 6, 2026 7:51 PM". Same class of bug; fix at format call site.
3. **Internal doc reference leaks into UI:** form section subtitle shows "…(SSOT §21)".

Also observed (working as designed, documented here so the redesign doesn't regress it): free-tier lock banner in Studio is truthful ("AI features are locked: You need an active AI-Birthday subscription…") with Retry / Write manually / AI Settings actions; draft autosave on exit works; History labels the draft "Draft" (truthful state).

---

## 8. Existing design docs — status

| Doc | Status |
|---|---|
| `DESIGN.md`, `DESIGN-SUMMARY.md` | Superseded by the new "Confetti, but grown-up" direction; must be rewritten in Phase 8. Keep their good rules: truthfulness, 48dp targets, safe areas, one primary action per surface. |
| `docs/ui-ux/current-ui-audit.md` (Oct 4) | Useful defect list (bottom-sheet overflow, trailing-chip squeeze, fixed 48-height button). **Stale:** claims HomeScreen/PersonListScreen duplication (doesn't exist), quotes code that has since changed. Fold into this audit; do not treat as current. |
| `docs/ui-ux/design-system.md` v2.0 | Specifies Playfair Display + Inter/Nunito — **never shipped** (code uses Outfit via `GoogleFonts`). Color tokens Terracotta/Amber/Forest superseded by the new palette. Its component specs (`ResponsiveActionBar`, scroll-safe sheet) are still accurate. |
| `docs/ui-ux/redesign-specification.md` v2.0 | Route map accurate. Claims dark-mode toggle (doesn't exist; a test forbids it), tone chips list wrong, "developer sandbox switch" doesn't exist. New prompt supersedes it. |
| Root stale reports (`IMPLEMENTATION_REPORT.md`, `TEST_READY.md`, …) | Moved to `docs/archive/` in this phase. |

**Old palette vs new:** shipped app uses Terracotta `#A64B2A`/Amber `#D9822B`/Forest `#2D5A46` on linen surfaces; redesign replaces with the coral/sun/mint/sky/berry palette (`#D93F0B` primary etc.).

---

## 9. Screenshot index (before)

`docs/screenshots/before/` — captured on device, all via real flows:

| File | State |
|---|---|
| 01–03-onboarding-{1,2,3} | Onboarding pages (fresh `pm clear`) |
| 04-dashboard-empty | Dashboard after first-run, 0 contacts |
| 05-people-empty | People empty state |
| 06-add-person-form | Add form, untouched |
| 07-calendar | October 2026, no birthdays |
| 08-history-empty | History empty (`EmptyState`) |
| 09-settings | Settings top (Account/Backup/Subscription) |
| 10-settings-ai | Settings AI + Reminders + Help sections |
| 11-dashboard-populated | 2 tracked, "1 Birthday Today", Action Needed card |
| 12-people-populated | Asha + Rohan rows with countdown chips |
| 13-person-detail-sheet | Person detail bottom sheet (Asha) |
| 14-calendar-populated | Oct grid, marks on 7 + 15 |
| 15-calendar-day-sheet | "Birthdays Oct 7" day sheet |
| 16-message-studio | Studio fresh (recipient, tones, lengths) |
| 17-studio-with-draft | Message typed, keyboard open |
| 18-studio-ai-locked | Draft text + truthful free-tier lock banner |
| 19-history-populated | Draft entry with Copy/Studio actions |
| 20-add-form-validation | "Name is required" + "Pick a birthday (month and day)" |

---

## 10. Definition-of-done checklist for the audit (Phase 0 gate)

- [x] Screen inventory, providers, states documented (§2–§3)
- [x] Hardcoded strings/colors quantified (§4)
- [x] Accessibility gaps listed (§5)
- [x] UI-touching tests listed (§6)
- [x] Before screenshots captured (§9)
- [x] Baseline analyze/test/cold-start recorded (§1)
- [x] Stale root reports archived (`docs/archive/`)
- [x] `dart analyze` + `flutter test` green on this commit
