# Report 07 — Recommendations & Roadmap

**Date**: 2026-06-01
**Author**: Senior PM + Senior Architect (SSOT Audit)
**Scope**: Senior PM, Senior Architect, and Senior UX recommendations; impact-ranked roadmap; what's next quarter.

---

## 1. Executive Summary

RelateAI is in **MVP → Production Hardening** transition. The vision, architecture, and core features are sound. The blocker for Play Store production is a small set of **security, performance, and UX gaps** that can be resolved in ~2-3 weeks of focused engineering.

**Top recommendation**: Focus the next 4 weeks on **production readiness** before adding any new features. Then start the v2 roadmap (multi-language, cloud sync, Pro tier).

---

## 2. Recommendations by Role

### 2.1 Senior Product Manager

| # | Recommendation | Impact | Effort | Quarter |
|---|---|---|---|---|
| PM-01 | **Ship v1.0 to Play Store Internal Testing** within 2 weeks (after P0 fixes). Use 10% staged rollout. | High | 2 weeks | Q3 2026 |
| PM-02 | **Define v1.1 scope now**: i18n (Hindi), encrypted backup, Performance NFR-01 fix. Lock scope to prevent scope creep. | High | 1 day | Q3 2026 |
| PM-03 | **Pause feature additions** (Wear OS, Group messages) until v1.0 has 4.5+ stars and 40% D30 retention. | High | — | Q3-Q4 2026 |
| PM-04 | **Establish a "definition of done"** per Report 08 (production readiness checklist). Block PRs that don't meet it. | High | 0.5 day | Q3 2026 |
| PM-05 | **Start user-research interviews** (5-10 target users from India, Indonesia) for v2 validation. | Medium | 1 week | Q3 2026 |
| PM-06 | **Decide on Privacy Policy hosting** (own site, GitHub Pages, or Play Store Data Safety form only). Required for Play Store. | High | 0.5 day | Q3 2026 |
| PM-07 | **Define analytics event taxonomy** (Report 03 §2.12) and implement opt-in local DataStore. | Medium | 1 week | Q3 2026 |
| PM-08 | **Add Crashlytics opt-in** (was deferred to v2 in §28.4). For v1 production, this is **strongly recommended** for triage. | High | 2 days | Q3 2026 |
| PM-09 | **Plan v2 "Pro" tier** (per §7.4): multi-language Gemini, custom LLM fine-tuning, cloud backup, gift affiliate links. | High | 1 week planning | Q4 2026 |
| PM-10 | **Establish KPI dashboards** (Mixpanel, Firebase, or local): DAU/MAU, messages sent, activation rate, crash-free rate. | High | 1 week | Q3 2026 |

### 2.2 Senior Architect

| # | Recommendation | Impact | Effort | Quarter |
|---|---|---|---|---|
| ARCH-01 | **Fix CRIT-01** (MasterKey on main thread) — highest perf risk, ~1 day. | Critical | 1 day | Q3 2026 |
| ARCH-02 | **Fix CRIT-02** (certificate pinning) for Gemini + People API. | High | 1 day | Q3 2026 |
| ARCH-03 | **Fix CRIT-03** (encrypted backup JSON) — user-facing privacy risk. | High | 2 days | Q3 2026 |
| ARCH-04 | **Split `:core:data`** into 8 sub-modules (Report 06 §8.1). | High | 1-2 weeks | Q3 2026 |
| ARCH-05 | **Add integration tests** for the dispatch pipeline (Worker → Sender → SentMessageDao). | High | 1 week | Q3 2026 |
| ARCH-06 | **Implement Paging 3** for contact list (NFR-SCAL-01). | Medium | 2 days | Q3 2026 |
| ARCH-07 | **Add UseCase layer** (NFR-SCAL-03, P4-02). Currently 7 UseCases in `core/data/.../usecase/`; expand to all complex business logic. | Medium | 1-2 weeks | Q3 2026 |
| ARCH-08 | **Add certificate transparency** checks via `NetworkSecurityConfig`. | Medium | 0.5 day | Q3 2026 |
| ARCH-09 | **Add DB indices** (Report 06 §3.4) to avoid table scans as data grows. | Medium | 0.5 day | Q3 2026 |
| ARCH-10 | **Set up actual CI** (`.github/workflows/android.yml` per §23.5). Currently the file doesn't exist. | High | 1 day | Q3 2026 |
| ARCH-11 | **Add Detekt** static analysis with baseline file for legacy issues. | Medium | 0.5 day setup + ongoing | Q3 2026 |
| ARCH-12 | **Resolve duplicate composables** (GiftAdvisorView, MemoryVaultView, StyleCoachScreen) — see Report 05. | Medium | 0.5 day | Q3 2026 |
| ARCH-13 | **Migrate `org.json` to Moshi** in `MainActivity.kt:101-110`. | Low | 0.25 day | Q3 2026 |
| ARCH-14 | **Add WorkManager tests** for all 5 workers (currently 0 tests). | High | 1 week | Q3 2026 |
| ARCH-15 | **Document API style** for new endpoints (e.g., how to add a new Gemini variant). | Low | 0.5 day | Q3 2026 |
| ARCH-16 | **Add Hilt-in-Compose testing** (`hiltViewModel()` in `@HiltAndroidTest`). | Medium | 1 day | Q3 2026 |
| ARCH-17 | **Plan on-device LLM migration** (Gemini Nano via MediaPipe) for v2.2027. | High | 2 months | Q1-Q2 2027 |
| ARCH-18 | **Consider KMP** (Kotlin Multiplatform) for shared business logic if iOS port is serious. | High | 2 months | Q1 2027 |

### 2.3 Senior UX Designer

| # | Recommendation | Impact | Effort | Quarter |
|---|---|---|---|---|
| UX-01 | **Fix dead UI** in `ContactDetailScreen.kt` (Edit button, DND switch, Custom Send Time) — P0 blockers. | Critical | 1-2 days | Q3 2026 |
| UX-02 | **Add empty states** to Dashboard, Messages, Analytics screens. | High | 0.5 day | Q3 2026 |
| UX-03 | **Add Snackbar feedback** for save/edit/regenerate actions. | High | 0.5 day | Q3 2026 |
| UX-04 | **Extract hardcoded strings** to `strings.xml` (15+ found in ContactDetailScreen + EventsScreen). | High | 2 days | Q3 2026 |
| UX-05 | **Simplify onboarding** from 10 to 7 steps (per §30.2 HIGH-02). | High | 3 days | Q3 2026 |
| UX-06 | **Surface Analytics and Style Coach** in bottom nav (currently hidden in MORE). | Medium | 1 day | Q3 2026 |
| UX-07 | **Run accessibility audit** with TalkBack, switch access, large font (200%). | High | 2 days | Q3 2026 |
| UX-08 | **Verify color contrast** ≥4.5:1 for all text. Use Material Theme Builder. | High | 0.5 day | Q3 2026 |
| UX-09 | **Add haptic feedback** on key actions (save, regenerate, send). | Low | 0.5 day | Q3 2026 |
| UX-10 | **Add adaptive icon** for launch icon (LOW-01). | Low | 0.25 day | Q3 2026 |
| UX-11 | **Customize splash screen** (LOW-03) using Android 12+ SplashScreen API. | Low | 0.5 day | Q3 2026 |
| UX-12 | **Polish dark theme** (LOW-02) — currently default Material 3 dark. | Low | 1 day | Q4 2026 |
| UX-13 | **Test tablet landscape** with NavigationRail (currently works, but polish needed). | Medium | 1 day | Q4 2026 |
| UX-14 | **Add first-time user tooltips** for non-obvious UI (e.g., "Edit" button in ContactDetail). | Medium | 1 day | Q4 2026 |
| UX-15 | **Conduct UX research** (5-10 users, India + Indonesia) to validate personas and pain points. | High | 2 weeks | Q3 2026 |

### 2.4 Senior Security Engineer (Cross-Cutting)

| # | Recommendation | Impact | Effort | Quarter |
|---|---|---|---|---|
| SEC-01 | **All CRIT items from §30.1** — MasterKey thread, cert pinning, encrypted backup. | Critical | 4 days | Q3 2026 |
| SEC-02 | **Add Play Integrity API** to detect rooted devices and tampered APKs. | High | 1 day | Q3 2026 |
| SEC-03 | **Add Screenshot prevention** flag in OnboardingScreen (FLAG_SECURE) for sensitive screens. | Medium | 0.5 day | Q3 2026 |
| SEC-04 | **Audit Accessibility Service** — log all actions for user review (planned v2 per §22.6). | Medium | 1 week | Q4 2026 |
| SEC-05 | **Add app attestation** via Play Integrity for sensitive operations (Gemini key usage). | High | 1 day | Q3 2026 |
| SEC-06 | **Penetration test** the SQLCipher implementation. | High | 1 week | Q3 2026 |
| SEC-07 | **GDPR compliance review** — Right to be Forgotten, Data Portability, Breach Notification. | High | 1 week | Q3 2026 |

---

## 3. Q3 2026 Roadmap (Next 12 Weeks)

### Week 1-2: Production Hardening (P0)
- ARCH-01: MasterKey on main thread
- ARCH-02: Certificate pinning
- ARCH-03: Encrypted backup
- ARCH-12: Resolve duplicate composables
- UX-01: Fix dead UI in ContactDetailScreen
- PM-04: Definition of done
- PM-06: Privacy policy
- ARCH-10: Set up actual CI

### Week 3-4: Quality Bar (P1)
- UX-02, UX-03: Empty states + Snackbar
- UX-04: Extract strings
- UX-05: Simplify onboarding
- UX-06: Surface Analytics in bottom nav
- ARCH-05: Add integration tests
- ARCH-09: Add DB indices
- ARCH-13: Migrate org.json to Moshi
- ARCH-14: Add WorkManager tests
- SEC-02: Play Integrity
- PM-08: Crashlytics opt-in
- ARCH-11: Add Detekt

### Week 5-8: UX Polish (P2)
- UX-07: A11y audit
- UX-08: Color contrast
- UX-09: Haptic feedback
- UX-10: Adaptive icon
- UX-11: Splash screen
- UX-14: First-time tooltips
- ARCH-06: Paging 3
- ARCH-16: Hilt-in-Compose testing
- PM-05: User research interviews
- PM-07: Analytics event taxonomy
- SEC-03: FLAG_SECURE on sensitive screens

### Week 9-12: v1.0 Release + v1.1 Planning
- PM-01: Play Store Internal Testing → Production
- PM-02: Lock v1.1 scope
- ARCH-04: Split `:core:data` (start)
- ARCH-07: UseCase layer (start)
- PM-09: Plan Pro tier
- PM-10: KPI dashboards

---

## 4. Q4 2026 (Beyond)

- v1.1: Hindi, encrypted backup, MasterKey fix, Paging 3
- Cloud backup via Google Drive (2 weeks)
- Multi-language Gemini models (1 week)
- Wear OS tile (3 weeks)
- Smart reply detection (2 weeks)
- Web companion (read-only) (1 month)
- Crashlytics full rollout
- Pro subscription tier (1 month)

---

## 5. Q1-Q2 2027 (Long-Term)

- On-device LLM (Gemini Nano) (2 months) — significant privacy + cost win
- Family plan (2 months) — multi-user
- WhatsApp Business API (1 month) — reliable sending
- iOS port (SwiftUI or KMP) (2 months) — major market expansion
- Voice message generation (TTS) (2 months)

---

## 6. Anti-Recommendations (What NOT to Do)

| Don't | Why |
|---|---|
| Don't add more features before v1.0 ships | Scope creep killed the previous version (v2.8). |
| Don't rewrite the architecture | Multi-module Clean Architecture is correct. Refactor within it, not around it. |
| Don't switch to KMP/iOS-port tools before validating Android retention | Build on success, not speculation. |
| Don't introduce feature flags in v1 | ADR-015 explicitly deferred this. |
| Don't add Java/Kotlin bridges for legacy code | None exists; would be premature. |
| Don't add Hilt test rules before Hilt itself is mature in our usage | Wait for the Hilt-in-Compose test infra to stabilize. |
| Don't promise on-device LLM in v1.0 | §31.3 defers to Q1 2027. Don't move the date. |
| Don't collect analytics without explicit consent | GDPR violation. Privacy-first is a core value prop. |

---

## 7. Quick-Win Slog (Next 5 Days)

If the team has 1 person-week of slack, these are the highest-ROI items:

| # | Item | Effort | ROI |
|---|---|---|---|
| 1 | Fix `ContactDetailScreen.kt:44, 185, 209` dead handlers | 1 day | Very High |
| 2 | Resolve `GiftAdvisorView`, `MemoryVaultView`, `StyleCoachScreen` duplicates | 0.5 day | High |
| 3 | MasterKey on main thread (CRIT-01) | 1 day | Very High |
| 4 | Add `.github/workflows/android.yml` CI | 0.5 day | High |
| 5 | Verify FR-05 (sign-out wipes DB) | 0.25 day | High |

**Total**: 3.25 person-days for very high ROI. Recommend doing immediately.

---

## 8. Definition of Done (for any v1 PR)

A PR may be merged only if:

1. ✅ Compiles without warnings (`./gradlew assembleDebug`)
2. ✅ Passes all unit tests (`./gradlew test`)
3. ✅ Passes lint (`./gradlew lint`) — Detekt baseline may exist for legacy
4. ✅ Adds at least 1 unit test for new business logic
5. ✅ No `!!` in production code
6. ✅ No hardcoded strings (must be in `strings.xml`)
7. ✅ No `printStackTrace` (use `Log.e`)
8. ✅ No new direct DAO access from ViewModels (use repository)
9. ✅ No new field-injection of Hilt deps
10. ✅ Touch targets ≥48dp
11. ✅ contentDescription on all interactive icons
12. ✅ SSOT.md updated if architectural change

A PR that does not meet these should be **rejected with a checklist link**.
