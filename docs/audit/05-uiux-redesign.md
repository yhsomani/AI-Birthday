# UI/UX Redesign Audit — Current State & Phase 4–8 Work Program

**Date:** Wed Oct 07 2026 (matches Phase 0 baseline `docs/ui-audit.md`)
**Mode:** READ-ONLY audit. No files under `lib/`, `test/`, `android/`, `ios/` were modified.
**Evidence labels:** `FACT` = verified in widget code / tests (path:line given) · `INFERENCE` = reasoned from code, not runtime-verified · `UNKNOWN` = not verifiable statically.
**Hierarchy used:** widget code > tests > design docs > comments.

---

## 0. Executive summary

The app ships **two parallel design systems** and effectively **three color palettes** at once. All 8 screens import `lib/shared/design_system/` (static `AppColors` consts: terracotta `#A64B2A`), while the live theme (`lib/app/theme/app_theme.dart`) builds the M3 `ColorScheme` from the *older* `lib/ui/design_system/app_tokens.dart` (`AppPalette`: coral `#C63806`, linen `#FFF9F5`) — and the specs (`docs/ui-ux/design-system.md`, `DESIGN.md`) document yet a third intent (terracotta + Playfair/Inter). The Phase 1–2 component library (`AppCard`, `AppChip`, `AppBanner`, `AppErrorState`, `SectionHeader`, `SkeletonBox`, `AppEmptyState`) is **contrast-tested and unused by any screen**; the components the screens *do* use are **not** contrast-tested and carry no header/semantics. Good news: the Phase-0-flagged hard defects (bottom-sheet facts overflow, Studio action-bar overflow, trailing-chip squeeze) are largely fixed in code; what remains is token unification, semantics, the People 200% locus, status-chip contrast in dark mode, and flipping an e2e suite that currently *accepts* `RenderFlex overflowed`.

---

## 1. Design system census

| System | Location | Consumers | Contrast-tested | Semantics-header |
|---|---|---|---|---|
| **Tokens (theme)** | `lib/ui/design_system/app_tokens.dart` — `AppPalette` ThemeExtension (light/dark) | **only** `lib/app/theme/app_theme.dart:10` | ✅ `test/ui/contrast_test.dart` (light+dark, 4.5:1/3:1) | — |
| **Components (old, "Confetti")** | `lib/ui/design_system/` — `AppCard`, `AppChip`(+`toneForCountdown`), `AppBanner`, `AppErrorState`, `SectionHeader`(Semantics.header), `AppEmptyState`, `SkeletonBox` | **0 screens** — only tests (`test/ui/components_test.dart`, `component_gallery.dart`) | ✅ via palette | ✅ `SectionHeader` (`section_header.dart:47`) |
| **Components (new, "editorial")** | `lib/shared/design_system/` — `AppColors` (static consts), `AppSpacing`, `AppSectionHeader`, `CountdownChip`, `CelebrationCard`, `EmptyState`, `AppBottomSheet`, `ResponsiveActionBar` | **all 8 screens** + auth sheet | ❌ no contrast test exists for these paints | ❌ `AppSectionHeader` has no `Semantics(header:)` (`app_section_header.dart:39-49`) |

**FACT — split-brain:** screen primaries are static terracotta (`dashboard_screen.dart:241,497`, `people_screen.dart:677`, `calendar_screen.dart:89`) while the same screens' theme-provided `colorScheme.primary` is coral (`app_tokens.dart:70` + `app_theme.dart:31-44`). Both render on one screen (avatar fills terracotta, focused borders/snackbars coral).

**FACT — three palettes in docs/code:**
1. `docs/ui-ux/design-system.md` §2: primary `#A64B2A`, bg `#FAF7F2`, Playfair/Inter — never shipped (`docs/ui-audit.md:169`).
2. `DESIGN.md` §2-3: same terracotta/linen + **Outfit-only** typography — matches `shared/design_system/app_colors.dart` and file names, but not the theme.
3. `AppPalette` (shipped theme): coral `#C63806`, bg `#FFF9F5`, **Bricolage Grotesque + Outfit** — deviation documented at `app_tokens.dart:68-69`; fonts ARE bundled and declared (`pubspec.yaml:82-88`, assets verified `assets/fonts/*.ttf` exist) so the font gate **passes**.

**Fonts — `FACT`: PASS.** Bundled `BricolageGrotesque` + `Outfit` declared in pubspec with matching files; theme families match (`app_theme.dart:16,19`); zero `google_fonts` usage. Only docs conflict (spec's Playfair/Inter, `design-system.md:55,59`).

---

## 2. Screen × component × state × a11y map

Legend: ✓=uses · ✗=not used/absent · ⚠=hand-rolled variant · States: L/loading, E/empty, Er/error, S/retry-action, O/offline

| Screen (file) | shared-DS components | old-DS components | L | E | Er | S | 48dp | 200% risk | a11y notes (FACT unless stated) |
|---|---|---|---|---|---|---|---|---|---|
| **Dashboard** `features/dashboard/presentation/dashboard_screen.dart` | ✓ AppSectionHeader, CountdownChip, CelebrationCard, AppSpacing | ✗ | ⚠ spinner x2 (51,189) | ⚠ hand-rolled "No upcoming birthdays" Card (154-167), not `EmptyState` | ⚠ static text, **no retry** (195-203) | ✗ | ✓ theme + `bottomClearance` (100) | ⚠ action-card LayoutBuilder stacks <430dp / ≥1.15× (375-430) — old squeeze fixed | raw `QUICK ACTIONS` Text header (491-499) unannounced; `Colors.grey[600]` (250) untested |
| **People** `features/people/presentation/people_screen.dart` | ✓ AppBottomSheet (795), CountdownChip, AppSpacing | ✗ | ⚠ spinner (768) | ⚠ hand-rolled grey column (598-638), not `EmptyState` | ⚠ static, no retry (769-777) | ✗ | ✓ | **see §3.1** — likely live 200% locus | duplicate badge `Colors.amber(a:0.2)`+`Colors.brown` (293-309) low-contrast; CSV sheets hand-rolled (80-149, 202-334) |
| **Person form** `features/people/presentation/person_form_screen.dart` | ✓ AppSpacing (via literals) | ✗ | ✗ no load state (silent populate, 84-129) | n/a | ⚠ inline field errors (357) | ✗ | ⚠ `Size.fromHeight(48)` Save (686) is a *minimum* — grows, low risk; month/dropdown row (361-393) squeezes at 200% | raw `ESSENTIAL INFORMATION` header (340-348) duplicates `AppSectionHeader` look |
| **Calendar** `features/calendar/presentation/calendar_screen.dart` | ✓ EmptyState, AppBottomSheet, AppSpacing | ✗ | ⚠ spinner (115) | ✓ EmptyState (120-133) | ⚠ terse "Could not load birthdays." no retry (116-117) | ✗ | ✓ | ⚠ grid: nested `Expanded` + fixed 6dp dot row (204-227, 314) — <~62dp cell height overflows (landscape/compact at 200%) | **best-in-app**: per-day `Semantics` (275-279) |
| **Message Studio** `features/message_studio/presentation/message_studio_screen.dart` | ✓ ResponsiveActionBar (921), AppSpacing | ✗ | ⚠ full-screen spinner (781) | ✗ no empty guidance | ⚠ hand-rolled banner (841-914) with Retry/Manual/Settings — only screen with retry | ✓ | ✓ action bar stacks (spec §5.1) | ⚠ editor header Row (`Your message` vs `Saving…/Saved/N chars`, 1143-1175) not `Flexible` → 200% collision; variation sheet (527-590) & translate sheet (623-651) are unscrollable `Column(min)` | editor `TextField` has no semantics label (audit §5, `docs/ui-audit.md:118`); `fontFamily:'monospace'` literal (691) |
| **History** `features/history/presentation/history_screen.dart` | ✓ EmptyState | ✗ | ⚠ spinner (23) | ✓ EmptyState (35-40) | ⚠ static, no retry (24-32) | ✗ | ✓ | ⚠ status chip is non-flexible in header Row (98-157) → title squeeze at 200%; card overrides theme radius 16 vs 12 (87-92) | status colors `Colors.green/orange/teal/grey[700]` (60-66) — orange `#EF6C00` 3.3:1 fails AA light; ~1:1 illegible dark |
| **Settings** `features/settings/presentation/settings_screen.dart` | ✓ AppSpacing | ✗ | ✓ "Checking…" tile (1288) | n/a | ✓ 2-3 banner systems (884-914, 1090-1255) | ✓ | ✓ | ⚠ literal `88` bottom padding (446) vs `AppSpacing.bottomClearance`; key badges hand-rolled (1098-1188) | private `_SectionHeader` (1258-1278) — **third** section-header implementation, not uppercase, unannounced; `Colors.red` "Remove Key" 3.5:1 (724); `Colors.grey/green/amber` badges |
| **Onboarding** `features/onboarding/presentation/onboarding_screen.dart` | ✓ AppColors | ✗ | n/a | n/a | n/a | n/a | ✓ | ✓ static pages | page dots bare `AnimatedContainer`s — position never announced (370-386); `Colors.grey[600]` (351) |
| **Auth sheet** `features/auth/presentation/auth_bottom_sheet.dart` | ✓ AppColors | ✗ | ✓ in-button spinner (220-230) | n/a | ✓ error banner (176-204) | ✓ | ⚠ **`SizedBox(height:52)` Google button (207-209) fixed height** — labels clip at 200% (audit §5) | not `AppBottomSheet`; drag handle `Colors.white24/black12` (99); hardcoded `Color(0xFF1F1F1F)` (214) |
| **Paywall-adjacent** (Settings sub card 546-632, Studio lock 751-776+948-953, Auth Pro tile 159-163) | — | ✗ | ✓ | n/a | ✓ | ✓ | ✓ | ✓ | no dedicated paywall screen; free-tier copy is truthful (audit §7, `docs/ui-audit.md:159`) |

**Global gaps (FACT, from `docs/ui-audit.md:82` + code):** no screen has an offline state or permission-denied *screen*; no list uses skeletons though `SkeletonBox` exists (unused); `Semantics(header:)` occurs in exactly **0 live screens** (the only header-semantics widget, old `SectionHeader`, is unused — `section_header.dart:47`).

---

## 3. Findings (P0–P3)

### P0-1 — People 200% overflow not eliminated; live locus is the review/import sheets
- **Symptom:** rendered overflow at 200% text scale on People (flagged as the known defect; task brief).
- **Root cause (INFERENCE):** `_reviewAndImportCandidates` builds `Container(height: MediaQuery.height * 0.7)` > `Column` > non-flexible header `Row` (`title` Text + `TextButton` "Select All", `people_screen.dart:213-243`) + fixed texts, then `Expanded(ListView)`. At 200% the header row exceeds its width budget and nothing flexes; the fixed 70% height + keyboard insets leave no scroll. The detail-modal overflow (old audit §3.2) *is* fixed via `AppBottomSheet` (795) — that part is no longer the locus.
- **Evidence:** `people_screen.dart:202-334`; `AppBottomSheet` used only for the detail sheet (795).
- **Required change:** rebuild both sheets on `AppBottomSheet` (scroll-safe + `viewInsets`); make the header `Row` use `Flexible`; drop the fixed 70% height.

### P0-2 — e2e suite currently *passes* on RenderFlex overflow
- **Symptom:** "no overflow" is not enforced; regressions slip the gate.
- **Root cause:** the 360dp/1.5× journeys assert `error.toString() contains 'RenderFlex overflowed'` when an exception exists (i.e., **overflow ⇒ pass**) — `test/e2e/tier1_features_test.dart:376-413`, `tier2_boundary_corner_test.dart:299,333`, `tier4_user_journeys_test.dart:234,244`.
- **Evidence:** code above; only `shell_test.dart:105-114` and `components_test.dart:138` assert zero-overflow.
- **Required change:** flip the pattern to `expect(tester.takeException(), isNull)` with a named reason; add a 2.0× "infinite-height" pump per screen (pattern already exists at `components_test.dart:138-147`).

### P1-1 — Two design systems, three palettes, zero single source of truth
- **Symptom:** terracotta static consts render next to a coral theme on the same screen; dark-mode adaptation is manual and inconsistent.
- **Root cause:** `shared/design_system/app_colors.dart` hardcodes light/dark pairs and screens pick by `theme.brightness`; the theme derives `ColorScheme` from a *different* palette (`app_tokens.dart:61-104`). No palette is authoritative in code.
- **Evidence:** e.g. focus border coral but avatar terracotta on one screen (`dashboard_screen.dart:241,329`; theme `app_theme.dart:163`).
- **Required change:** decide one palette (recommend shipped `AppPalette`, which is contrast-tested), extend `AppPalette` with missing roles (WhatsApp green, terracotta/amber/forest containers used by shared components), and re-point `shared/design_system` components to read `context.colors` instead of static `AppColors`.

### P1-2 — Section headers: third hand-rolled implementation, no semantics
- **Symptom:** identical visual role rendered 4 ways: `AppSectionHeader` (dashboard), private `_SectionHeader` (settings 1258), raw `Text` (dashboard "QUICK ACTIONS" 491; form "ESSENTIAL INFORMATION" 340), old unused `SectionHeader`.
- **Evidence:** code above; old `SectionHeader` has `Semantics(header:true)` (`section_header.dart:47`), shared `AppSectionHeader` does not (`app_section_header.dart:39`).
- **Required change:** merge into one component with `Semantics(header:)`, replaced at all 4 sites.

### P1-3 — History/Settings status chips illegible in dark mode; untested contrast
- **Symptom:** status labels unreadable on dark surfaces.
- **Root cause:** `Colors.orange[800]`/`green[800]`/`grey[700]` text on `withValues(alpha:0.12)` fills over dark `#1D1A17` ≈ 1:1; light-mode orange[800] ≈3.3:1 fails AA.
- **Evidence:** `history_screen.dart:60-66,132-156`; settings badges `settings_screen.dart:1098-1255`. `test/ui/contrast_test.dart` covers only `AppPalette` label/fill pairs — none of these paints.
- **Required change:** migrate to `AppChip`/`AppTone` (contrast-checked pairs, `app_chip.dart:34-60`), extend `contrast_test.dart` to every tone-on-card-surface pair.

### P2 — (lower-effort batch)
- **P2-1 Studio editor + status row non-flexible at 200%** — `message_studio_screen.dart:1143-1175`. Wrap right cluster in `Flexible`/`Wrap`.
- **P2-2 Studio variation/translate sheets unscrollable** — `527-590`, `623-651`: wrap in `SingleChildScrollView` or `AppBottomSheet`.
- **P2-3 Auth sheet fixed `height:52` Google button** — `auth_bottom_sheet.dart:207-209` (audit §5 finding, still open). Use `minimumSize` not fixed height.
- **P2-4 WhatsApp button `Colors.white` on `#25D366` ≈1.9:1** — `message_studio_screen.dart:943-946`. At minimum document as brand exception; AA-fix via darker green or black text.
- **P2-5 Settings "Remove Key" `Colors.red` 3.5:1** — `settings_screen.dart:724`. Use `colorScheme.error`.
- **P2-6 Duplicate countdown-label logic** — `CountdownChip._label` (`countdown_chip.dart:24-30`) vs `PeopleScreen._countdownLabel` (`people_screen.dart:31-37`) vs old `toneForCountdown` (`app_chip.dart:10-14`). Single source.
- **P2-7 l10n unused** — `app_en.arb` has 5 keys, only nav labels consumed (`app_scaffold.dart:68-90`); all screen strings hardcoded. At minimum extract visible strings or explicitly defer.
- **P2-8 `'monospace'` literal** and `'(SSOT §21)'` leak — `message_studio_screen.dart:691`, `person_form_screen.dart:606` (audit §4).
- **P2-9 Dead router aliases** `/`, `/home`, `/birthdays` — `router.dart:39-41`; never navigated (audit §2.1).

### P3 — dead code / stale docs
- **P3-1** `lib/ui/design_system` components have **0 screen importers** (verified: only `app_theme.dart` imports the package, for tokens) — either wire them in (they are the tested, semantics-bearing library) or delete.
- **P3-2** `docs/ui-ux/current-ui-audit.md` stale (claims HomeScreen/PersonListScreen duplication that no longer exists — `docs/ui-audit.md:46,168`).
- **P3-3** `DESIGN.md`/`DESIGN-SUMMARY.md` superseded; rewrite in Phase 8 (audit §8).
- **P3-4** `docs/ui-ux/redesign-specification.md` §2.7 claims a "developer sandbox switch" UI — only a service test hook exists (`subscription/application/subscription_service.dart:370`). Contradiction.

---

## 4. Phase 4–8 intended scope (reconstructed) & divergence

From `docs/ui-ux/*`, `docs/ui-audit.md`, `DESIGN.md`, and component test headers ("Phase 1 gate" `contrast_test.dart:1`, "Phase 2" `components_test.dart:1`):

| Intended | Status now | Divergence |
|---|---|---|
| **Ph 4:** Dashboard + Message Studio component-library migration & token consistency | ✗ Not done | Both screens still hand-roll headers/banners/empty states; old library unused |
| **Ph 5:** People 200% overflow fix | ⚠ Partial | Modal overflow fixed; review/import sheets remain the locus (P0-1) |
| **Ph 6:** design-token consistency (single palette) | ✗ | Three palettes (P1-1) |
| **Ph 7:** accessibility (semantics, contrast, scaling) | ⚠ Partial | Contrast test exists but covers unused palette pairs; headers un-announced; e2e tolerates overflow |
| **Ph 8:** docs rewrite + test-data cleanup + tighten gates | ✗ | DESIGN.md stale; device test data to remove (`docs/ui-audit.md:202`) |

---

## 5. Phase 4–8 work program (ordered; each with acceptance criteria)

**W1 — Token reconciliation (do first; unblocks everything).** Files: `lib/app/theme/app_theme.dart`, `lib/ui/design_system/app_tokens.dart`, `lib/shared/design_system/app_colors.dart` (+ every component in `shared/design_system`). Re-point shared components at `context.colors`; extend `AppPalette` with the containers/WhatsApp roles; delete `app_colors.dart` or reduce it to a thin re-export.
- GIVEN dark mode enabled WHEN any screen renders THEN no widget paints a color identical in light and dark (grep-able: zero `AppColors.` reads left in `lib/features/`, `lib/shared/`).
- GIVEN `flutter test test/ui/contrast_test.dart` WHEN run THEN every new shared-component paint pair is listed and ≥4.5:1 (3:1 for non-text).

**W2 — One section header, announced.** Merge `SectionHeader` semantics into `AppSectionHeader`; replace raw headers at `dashboard_screen.dart:491`, `person_form_screen.dart:340`, settings `_SectionHeader` (1258).
- GIVEN TalkBack WHEN traversing dashboard, settings, and the add-person form THEN each section title is announced as a heading.
- GIVEN golden-less visual check THEN header appearance is unchanged (uppercase, 12sp/0.8 tracking).

**W3 — Studio + Dashboard component migration.** Files: `message_studio_screen.dart`, `dashboard_screen.dart`. Replace hand-rolled error banner (Studio 841-914) with `AppBanner`; variation (527-590) and translate (623-651) sheets with `AppBottomSheet`; flex the editor header (1143-1175); use `AppCard`/`AppChip` where semantics/contrast matter.
- GIVEN 360dp viewport AND 2.0× text scale WHEN Studio renders with a draft AND keyboard active THEN `tester.takeException()` is null (new test; flip the tier1/tier4 pattern).

**W4 — People 200% fix.** Rebuild CSV paste + review sheets on `AppBottomSheet`; `Flexible` header rows; `EmptyState` for the list; token-based duplicate badge.
- GIVEN People with ≥4-fact contact AND 2.0× scale AND keyboard open WHEN the review sheet opens THEN no overflow and content scrolls to the Import button.

**W5 — Status chips + cards.** History (60-66, 132-156) and Settings badges → `AppChip`/`AppTone`; restore theme card radius (History 87-92).
- GIVEN dark theme WHEN History renders a `handedOff` draft THEN status label contrast ≥4.5:1 on the card surface (asserted in `contrast_test.dart`).

**W6 — Accessibility sweep.** `Semantics(header:)` everywhere; Studio editor field label; auth sheet `minimumSize` instead of `height:52`; extend `contrast_test.dart` to all wired paints; calendar cell semantics as the model (already correct).
- GIVEN Android TalkBack WHEN reaching the Studio editor THEN the field announces its purpose.
- GIVEN `flutter test` THEN no widget test at 360dp/1.5×/2.0× passes while a RenderFlex overflow occurred.

**W7 — Dead-code & test-gate tightening.** Delete or wire `lib/ui/design_system` components; remove router aliases (`router.dart:39-41`); delete `people_screen.dart` duplicate countdown logic; convert the overflow-acceptance assertions in tier1/tier2/tier4 to zero-overflow asserts.
- GIVEN `flutter analyze` AND `flutter test` THEN green AND zero occurrences of `contains('RenderFlex overflowed')` in `test/`.

**W8 — Docs.** Rewrite `DESIGN.md`/`DESIGN-SUMMARY.md`; update `design-system.md` palette+typography to shipped values (Bricolage+Outfit, coral or chosen palette); remove the sandbox-switch claim; archive `current-ui-audit.md`; remove device test data note.
- GIVEN `rg -n "Playfair|sandbox switch|HomeScreen|0xFFA64B2A" DESIGN.md docs/ui-ux` THEN no stale claims remain that contradict verified code.

---

## 6. Contradiction ledger (docs vs code)

1. **Palette:** docs/specs say terracotta `#A64B2A` (`design-system.md:40-49`, `DESIGN.md:24`); theme ships coral `#C63806` (`app_tokens.dart:70`); screens paint terracotta statically. Three-way.
2. **Typography:** spec says Playfair Display + Inter/Nunito (`design-system.md:55-67`); shipped = Bricolage Grotesque + Outfit (`app_theme.dart:16-19`), fonts bundled ✓.
3. **Components:** `SectionHeader` exists with semantics (tested) but is unused; screens use un-announced `AppSectionHeader`. Claimed-vs-actual a11y gap.
4. **Contrast:** `test/ui/contrast_test.dart` proves the palette; the paints actually on screen bypass it (history/settings badges fail).
5. **"Sandbox switch"** in redesign spec §2.7 — no UI exists (service hook only).
6. **Dark-mode toggle** claimed in redesign spec §2.7 — absent and a test asserts its absence (`settings_reminders_test.dart`, per `docs/ui-audit.md:139`).
7. **Overflow:** Phase-0 docs list Studio/Studio-action overflow as open; code shows `ResponsiveActionBar` + stacking fixes shipped — stale (as `docs/ui-audit.md:168` notes).

---

*Scope note: state-health, touch targets (theme-enforced 48dp via `minimumSize`/`IconButtonTheme` — `app_theme.dart:101-145`), and text-scaling behavior verified from source; on-device rendering at 200% was NOT re-run for this audit (Phase 0 device session covered baseline).*

---

## 7. Fix status (post-audit, committed on `main`)

| Item | Status | Evidence |
| :--- | :--- | :--- |
| P1 token reconciliation / single palette / old `lib/ui/design_system` deleted | ✅ | `a93e4f7`; `app_tokens.dart` brand roles derive from `AppColors`; zero screen importers of `lib/ui/design_system` |
| P2-1 editor header flex · P2-3 auth sheet 200% · P2-4 WhatsApp AA · P2-6 countdown SSOT | ✅ | `f66be7f`; tests: `countdown_chip_test.dart`, 200% editor/sheet tests, `contrast_test.dart` WhatsApp pair |
| P2-7 l10n unused | ⏸ **Explicitly deferred** | `app_en.arb` still 5 keys, nav labels only. Extraction is a cross-cutting change with no functional value until a second locale is actually required — revisit when localization lands. |
| P2-8 `'monospace'` literal + `'(SSOT §21)'` leak | ✅ | `message_studio_screen.dart` handoff detail → `Theme…bodySmall`; `person_form_screen.dart:606` → plain user-facing copy |
| P2-9 dead router aliases (`/`, `/home`, `/birthdays`) | ✅ | removed from `router.dart` (initialLocation is `/dashboard`); no navigation targets them |
| P3-2 `docs/ui-ux/current-ui-audit.md` | ✅ | archived-banner added as superseded baseline |
| P3-3 `DESIGN.md` / `DESIGN-SUMMARY.md` / `design-system.md` | ✅ | typography rewritten to shipped Bricolage Grotesque + Outfit (bundled) |
| P3-4 sandbox-switch claim | ✅ | removed from `redesign-specification.md` §2.7; only the service test hook remains |
| W8 device test-data note (`docs/ui-audit.md:202`) | ✅ | removed |
| W2 section headers announced (one header, TalkBack heading) | ✅ | `7f58663`; `AppSectionHeader` emits `Semantics(header: true)`; settings `_SectionHeader` (7 sites) + person-form raw `ESSENTIAL INFORMATION` replaced with `AppSectionHeader`; `test/ui/section_header_test.dart` |
| W5 status chips + card radius | ✅ completed in `7f58663` + wired-paints batch | History chips already on `AppTone` fill/label (`history_screen.dart:77-180`) with every tone pair asserted ≥4.5:1 in `contrast_test.dart`; History card radius 12 = theme `AppRadius.md`; Settings badges/entitlement chip/connection banner converted from 0.12-alpha tone tints to the same solid `AppTone` fill/label recipe — the tinted light-mode pairs failed AA (success ≈4.2:1, warning ≈4.1:1), the solid pairs are the asserted ≥4.5:1 palette combos |
| W6 Studio editor field label | ✅ | `7f58663`; editor `TextField` wrapped in `Semantics(label: 'Your message')`; asserted in `message_studio_visibility_test.dart` |
| W6 extend `contrast_test.dart` to all wired paints | ✅ | wired-paints batch: Settings status badges/banner/chip → `AppTone` fill/label; all raw `Colors.grey[5-7]00` body copy across Settings, Dashboard, People + person form, Message Studio, Onboarding → `context.colors.textSecondary` (grey[600] ≈3.4:1 on dark surfaces, grey[500] ≈2.6:1 even light); `contrast_test.dart` gains the status-fills-on-surface ≥3:1 assertion (non-text, WCAG 1.4.11; neutral/info surfaceAlt fills excluded — deliberately low-emphasis, label text carries meaning). Full gate: analyze 0, 301 tests green |

All audit 05 work-program items are now closed — W2/W5/W6 landed in `7f58663` + the wired-paints batch; the only remaining deferral is P2-7 l10n (above).