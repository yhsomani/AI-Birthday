# Report 01 — Documentation Discovery & Conflicts

**Date**: 2026-06-01
**Author**: Senior PM / Senior Architect (SSOT Audit)
**Scope**: Inventory all project documentation and identify conflicts requiring consolidation.

---

## 1. Documentation Inventory

| # | File | Path | Version | Size | Lines | Status |
|---|---|---|---|---|---|---|
| 1 | `SSOT.md` | repo root | v3.0 (2026-06-01) | ~134 KB | 3269 | Primary authority |
| 2 | `SSOT_TEMPLATE.md` | repo root | v2.0 (template) | ~19 KB | 377 | Meta-template (how to write SSOT) |
| 3 | `README.md` | repo root | n/a | ~0.9 KB | 21 | AI Studio run instructions |

No other documentation files exist (no `docs/`, no `ARCHITECTURE.md`, no `CONTRIBUTING.md`).

---

## 2. Document Roles

### 2.1 `SSOT.md` (v3.0) — Primary
The product specification. Contains all 33 required sections (Executive Summary → AI Context & Glossary). Claims to be "production-grade" and "comprehensive." It is the **single source of truth** for all product, design, and engineering decisions.

### 2.2 `SSOT_TEMPLATE.md` (v2.0) — Meta-Template
A **template for writing SSOT documents**, not project content. Provides guidance such as "Context Test", "Link Don't Duplicate", "Common Mistakes per Section". It is intended for use when creating a new SSOT, not for describing RelateAI.

**Critical insight**: Despite its 30+ sections, SSOT_TEMPLATE.md contains **no RelateAI-specific information**. It is purely instructional. Should be **kept as-is** for future SSOT authors; do NOT merge into SSOT.md.

### 2.3 `README.md` (21 lines) — Deploy Guide
Contains only AI Studio run instructions:
- Open in Android Studio
- Create `.env` with `GEMINI_API_KEY`
- Remove `signingConfig = signingConfigs.getByName("debugConfig")` from `app/build.gradle.kts` for production
- View in AI Studio: `https://ai.studio/apps/c24a367f-5df5-4d64-885b-65caf74bfe9b`

**Critical insight**: This is **deploy-only documentation**. There is **zero overlap with SSOT.md** (no product content). It is appropriate for AI Studio's audience but **insufficient as the project's main README**. A developer landing on the repo would learn nothing about RelateAI's purpose, architecture, or how to contribute.

---

## 3. Conflicts Identified

### 3.1 `SSOT.md` Module Count — **STALE**
- §1.2: "**Codebase Size**: ~7,000+ lines of Kotlin across **7 modules**"
- §14.3 Table: Lists **11 modules** (`:app` + 3 `:core:*` + 7 `:feature:*`)
- **Actual** (per `settings.gradle.kts`): 12 modules (`:app` + 3 `:core:*` + 8 `:feature:*` — confirms `:feature:settings` exists in addition to the 7 listed)
- **Resolution**: Update §1.2 to "12 modules"; §14.3 is correct.

### 3.2 `SSOT.md` Onboarding Steps — **STALE**
- §1.4 row "P2-08 / Onboarding": "10-step onboarding" (status: Done)
- §8.9 / FR-80: "System MUST guide user through **7-step** onboarding (target simplification from 10 steps)"
- §31.1: "**Onboarding simplification (10→7 steps)** — P2, Not started"
- **Actual** (`OnboardingScreen.kt:53-67` NavHost): **10 destinations** (`welcome`, `google_signin`, `gemini_setup`, `contacts_perm`, `sms_perm`, `whatsapp_setup`, `battery_opt`, `writing_style`, `automation_prefs`, `import_progress`)
- **Resolution**: Onboarding is still 10 steps. The "7-step target" is aspirational. Either reduce to 7 (HIGH-02 in §30.2) or update FR-80 to reflect 10 steps.

### 3.3 `SSOT.md` MainViewModel Constructor — **INCORRECT**
- §15.3 Code Example shows 3-arg constructor:
  ```kotlin
  class MainViewModel @Inject constructor(
      private val contactRepository: ContactRepository,
      private val eventRepository: EventRepository,
      private val messageRepository: MessageRepository
  )
  ```
- **Actual** (`feature/dashboard/.../MainViewModel.kt:22-27`): **4-arg constructor**, includes `getDashboardMetrics: GetDashboardMetricsUseCase`
- **Resolution**: Update §15.3 example to match actual signature. This caused test compile errors (already fixed in `MainViewModelTest.kt`).

### 3.4 `SSOT.md` §14.3 List of Modules — **MINOR DISCREPANCY**
- §14.3 says `:feature:settings` is a separate module, but the §14.2 dependency graph (`graph TD` block at lines 967-1012) does **not include** `:feature:settings` in the diagram (it shows F_SET labelled but not in the edge list).
- **Resolution**: Diagram is missing the `APP → F_SET` edge; add it for clarity.

### 3.5 `SSOT.md` Component Hierarchy §15.2 — **MINOR**
- Shows `F[MainAppScreen] → G[AppContent] → H[DashboardScreen]` (correct)
- Shows `F[MainAppScreen] → M[AppBottomNavigation]` (correct)
- But §15.4 says "**Phone**: Bottom navigation; **Tablet (>600dp)**: Navigation rail" — actual code (`MainAppScreen.kt:31`) checks `screenWidthDp > 600`, which is **correct**.
- The §15.2 diagram does not show `AppNavigationRail` (the tablet variant) — minor gap.

### 3.6 `SSOT.md` Bottom Navigation Tabs — **STALE**
- §15.2 hierarchy implies 6 tabs (HOME, CONTACTS, EVENTS, MESSAGES, ANALYTICS, MORE) based on feature references.
- **Actual** (`AppBottomNavigation.kt:73-79`): **5 tabs** in bottom nav: HOME, CONTACTS, EVENTS, MESSAGES, MORE.
- **Actual** (`AppContent.kt:36-51`): **7 routes** total, with ANALYTICS and STYLE_COACH accessed via MORE tab (not in bottom nav).
- **Resolution**: Update §15.2 to show 5 bottom nav tabs + 2 hidden sub-routes. This is a known UX issue (see Report 05 — Dead UI/UX).

### 3.7 `SSOT.md` §18.2 Database Version — **STALE**
- §18.2 Table claims "Current Version: 6" with MIGRATION_5_6 adding `relationsJson`.
- **Actual** (`AppDatabase.kt`): Version is **6**, with `MIGRATION_4_5` (adds `contactGroup`) and an auto-migration for `relationsJson`. The migration numbering is irregular.
- **Resolution**: Clarify the migration history; some may have been renumbered.

### 3.8 `SSOT.md` §1.4 "Fixes Applied" Table — **UNICODE CORRUPTION**
- Status column contains `✅ Done` correctly in early rows.
- Later rows contain garbled characters: `�o.`, `�?O`, `�s��,?`, `dY"`, `�?"`, `🔄 In Progress` (sometimes).
- **Resolution**: Replace all corrupted emoji with proper Unicode (✅, ❌, 🔄, ⚠️).

### 3.9 `SSOT.md` §29.1 Performance Table — **UNICODE CORRUPTION**
- "Status" column contains: `�?O NFR-PERF-01`, `�s��,? Acceptable`, `�o.`, etc.
- **Resolution**: Replace with proper Unicode checkmarks/crosses.

### 3.10 `SSOT.md` §30–31 Technical Debt Tables — **UNICODE CORRUPTION**
- Status columns: `�o. Done`, `�?O Not started`, `dY" In Progress`.
- **Resolution**: Replace all corrupted symbols.

### 3.11 `SSOT.md` §32 ADRs — **UNICODE CORRUPTION**
- Consequences sections use `�o.` and `�?O` instead of ✅/❌.

### 3.12 `SSOT.md` §33 Do's and Don'ts — **UNICODE CORRUPTION**
- "DO" subheading shows `�o.` instead of `✅ DO`.

---

## 4. Missing Documentation

### 4.1 No `CHANGELOG.md`
The "Fixes Applied (This Session & Prior)" table in §1.4 is the closest thing to a changelog, but it is buried inside SSOT.md and not in a discoverable location. Recommended: extract into a top-level `CHANGELOG.md` and link from SSOT §1.4.

### 4.2 No `CONTRIBUTING.md`
New developer onboarding (§33.6) is excellent but lives inside SSOT.md. Should be a top-level `CONTRIBUTING.md` with link to SSOT for context.

### 4.3 No Architecture Diagrams in `docs/`
Mermaid diagrams in SSOT.md are good but should also be exported as PNG/SVG and placed in `docs/architecture/` for non-text audiences (designers, stakeholders, investors).

### 4.4 No Test Plan Document
§27 Testing Strategy is a high-level overview. There is no detailed test plan (what to test, when, by whom, coverage targets per module). Recommended: add `docs/TEST_PLAN.md`.

### 4.5 No Threat Model
§22 Security Model covers encryption and AuthN/AuthZ but lacks a STRIDE-style threat model. For an app handling PII (contacts, message content), a threat model would strengthen security posture.

### 4.6 No Privacy Policy / Data Handling Doc
For Play Store submission and GDPR compliance, a public privacy policy is required. Not in repo.

### 4.7 No API Style Guide
GeminiClient uses raw OkHttp + Moshi. No documentation of the request/response conventions used (e.g., how to add a new endpoint, how to handle rate limits in code).

---

## 5. Recommendations

1. **Promote SSOT.md v3.0 to v3.1** with the following fixes:
   - Fix module count (§1.2: 12 modules, not 7)
   - Fix MainViewModel example (§15.3: 4-arg constructor)
   - Fix onboarding step count (§8.9: 10 steps, simplification is HIGH-02)
   - Fix bottom nav tab count (§15.2: 5 + 2 hidden)
   - Fix database version table (§18.2)
   - Replace all Unicode-corrupted emoji with proper Unicode

2. **Keep SSOT_TEMPLATE.md as-is** — it is a meta-template, not project content.

3. **Keep README.md as-is** for AI Studio audience, but add a top-level pointer: "**For product/architecture documentation, see [`SSOT.md`](./SSOT.md)**"

4. **Create new top-level docs**:
   - `CHANGELOG.md` (extracted from §1.4)
   - `CONTRIBUTING.md` (extracted from §33.6)
   - `docs/THREAT_MODEL.md` (new, STRIDE-based)
   - `docs/TEST_PLAN.md` (new, per-module coverage targets)

5. **Export key Mermaid diagrams** to PNG/SVG in `docs/architecture/` for non-text audiences.

---

## 6. Authority Hierarchy

Going forward, conflicts between docs are resolved as:
1. **Code is the source of truth** (per §33.11 / §33 last note)
2. **SSOT.md** is the human-readable product/architecture spec
3. **SSOT_TEMPLATE.md** informs SSOT structure but is never content
4. **README.md** is for AI Studio / quick-start only
5. New docs (`CHANGELOG.md`, `CONTRIBUTING.md`) link to SSOT.md for context
