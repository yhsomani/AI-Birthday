# Report 03 — Missing Requirements & Gaps in SSOT.md

**Date**: 2026-06-01
**Author**: Senior PM / Senior Architect (SSOT Audit)
**Scope**: Topics covered inadequately or missing in SSOT.md v3.0, with proposed additions.

---

## 1. Areas With Adequate Coverage

SSOT.md v3.0 is already comprehensive in these areas:
- ✅ Executive Summary, Vision, Personas (well-developed)
- ✅ Feature Inventory (§10) and Specifications (§11) — detailed
- ✅ Business Logic / Rules (§13)
- ✅ System Architecture / Module Structure (§14)
- ✅ Database Schema (§18) — full ERD
- ✅ API Documentation (§19) — Gemini, People, SMTP
- ✅ Authentication & Security (§21, §22)
- ✅ ADRs (§32) — 20 decisions documented

---

## 2. Gaps Identified

### 2.1 No Threat Model (§22) — **HIGH PRIORITY**
**Gap**: §22 Security Model covers encryption, AuthN/AuthZ, and a checklist, but lacks a structured threat model.

**Proposed addition** — new subsection §22.9 "Threat Model (STRIDE)":

| Threat | Asset | Mitigation | Status |
|---|---|---|---|
| **S**poofing | User identity | Google Sign-In (OAuth 2.0), biometric lock | ✅ |
| **T**ampering | Room DB | SQLCipher AES-256 | ✅ |
| **T**ampering | API requests | HTTPS only (no cert pinning in v1) | ⚠️ Cert pinning planned v2 |
| **R**epudiation | Message dispatch | WorkManager Result logging, sent_messages table | ✅ |
| **I**nfo Disclosure | OAuth tokens | EncryptedSharedPreferences (Android Keystore) | ✅ |
| **I**nfo Disclosure | PII in logs | No PII in Logcat (release), R8 strips `Log.d` | ✅ |
| **I**nfo Disclosure | Backup JSON | **NOT encrypted in v1** — user must store securely | ⚠️ Planned v2 (CRIT-03) |
| **D**oS | Gemini API | Adaptive rate limiter (60 req/min) | ✅ |
| **D**oS | WorkManager | Constraints (Network, Battery), setExpedited for time-sensitive | ✅ |
| **E**oP | Hilt DI | Compile-time graph validation | ✅ |
| **E**oP | Accessibility Service | Scoped to `com.whatsapp,com.whatsapp.w4b` only | ✅ |
| **E**oP | DB encryption key | ANDROID_ID + app cert (device-bound) | ✅ |
| **E**oP | MasterKey on main thread | **NOT yet fixed** — 200ms ANR risk | ❌ CRIT-01 |

### 2.2 No Test Plan / Coverage Targets (§27) — **MEDIUM PRIORITY**
**Gap**: §27 lists test files and counts but lacks:
- Coverage targets per module
- Test pyramid guidance (unit vs integration vs UI vs E2E)
- CI integration of coverage gates

**Proposed addition** — new subsection §27.6 "Coverage Targets":

| Module | Target | Current (est.) | Status |
|---|---|---|---|
| `:core:domain` | 100% | ~30% (only repos partially) | ❌ |
| `:core:data` (DAOs, Repos) | 90% | ~30% (DaoTest covers CRUD) | ❌ |
| `:core:data` (Gemini, Contacts) | 80% | ~70% (ResponseParser, PromptBuilder, ContactMerger) | ⚠️ |
| `:core:data` (Workers) | 70% | 0% | ❌ |
| `:core:ui` | 50% | 0% | ❌ |
| `:feature:*` | 30% (smoke) | ~10% (MainViewModel 2 tests) | ❌ |
| `:app` | 20% | 0% (except Roborazzi screenshot) | ❌ |

**Overall**: ~15% line coverage. Well below industry-standard 60–80%.

### 2.3 No Performance Budget Detail (§29) — **MEDIUM**
**Gap**: §29.1 lists targets but lacks:
- Frame budget (16ms per frame for 60fps)
- Memory budget (heap size, native heap)
- DB query budget (max p95 query time)
- Network budget (request timeout, retry policy per endpoint)

**Proposed addition** — new subsection §29.6 "Performance Budgets":

| Layer | Metric | Budget | Current |
|---|---|---|---|
| UI | Frame render | ≤16ms (60fps) | ~8ms |
| UI | Cold start | <1.5s | ~2.5s |
| UI | Warm start | <0.5s | ~0.8s |
| DB | Query p95 | <50ms | ~5ms (estimated) |
| DB | Migration | <2s | N/A |
| Network | Request timeout | 30s | Default OkHttp (10s) |
| Network | Retry | 3x exponential | ✅ |
| Memory | Heap | <256MB | ~80MB |
| Memory | Native (SQLCipher) | <32MB | ~10MB |

### 2.4 No Release Checklist / Definition of Done (§30) — **HIGH**
**Gap**: §30 Known Issues covers technical debt but lacks a release-blocking checklist.

**Proposed addition** — new subsection §30.7 "Production Release Blockers":

| Blocker | Severity | Owner | Status |
|---|---|---|---|
| MasterKey on main thread (CRIT-01) | High | — | ❌ Not started |
| Certificate pinning (CRIT-02) | Medium | — | ❌ Not started |
| Encrypted backup JSON (CRIT-03) | Medium | — | ❌ Not started |
| Hilt test rules (HIGH-03) | Medium | — | ❌ Not started |
| Onboarding simplification (HIGH-02) | Medium | — | ❌ Not started |
| No work batching (HIGH-04) | Medium | — | ❌ Not started |
| OnboardingScreen monolithic (MED-05) | Medium | — | ❌ Not started |
| No pagination (MED-01) | Medium | — | ❌ Not started |
| No i18n (MED-03) | Medium | — | ❌ Not started |
| No haptic feedback (MED-02) | Low | — | ❌ Not started |
| No schema migration tests (MED-06) | Medium | — | ❌ Not started |

**Recommendation**: Block Play Store production release until at least CRIT-01, CRIT-02, CRIT-03 are resolved.

### 2.5 No Privacy Policy / Data Handling (§22) — **HIGH for Play Store**
**Gap**: No public privacy policy exists. Required for Play Store submission if app accesses:
- User accounts (Google Sign-In) ✅
- Contacts ✅
- SMS ✅
- Calendar (planned) ✅
- Accessibility Service ✅

**Proposed addition** — new subsection §22.10 "Privacy & Data Handling":

> RelateAI is a local-first app. The following data is stored on-device only:
> - Contacts (read from Google via OAuth, device via ContactsContract)
> - Events (birthdays, anniversaries, custom)
> - Messages (generated, sent, drafts)
> - Style profile
> - OAuth tokens, API keys (encrypted at rest)
>
> **Data leaving the device**:
> - Gemini API: prompt context only (name, age, interests, relationship) — message text + recipient name sent to generate variant
> - Google People API: OAuth metadata + contact reads
> - Gmail SMTP: email messages (user-configured)
> - WhatsApp: typed via UI (Accessibility Service)
> - SMS: sent via SmsManager (no third-party)
>
> **Data NOT collected**:
> - No analytics, no crash reports (v1), no usage telemetry
> - No advertising IDs
> - No social-graph upload
>
> **User controls**:
> - Backup/export: User-chosen location
> - Sign out: Clears all local data + OAuth tokens
> - Biometric lock: Optional
> - Onboarding disclosure: Required for Accessibility Service activation

This is the basis for the Play Store "Data Safety" form and a public `privacy-policy.html`.

### 2.6 No Onboarding Accessibility Disclosure (§8.9, §22) — **MEDIUM**
**Gap**: FR-82 says "System MUST explain Accessibility Service disclosure before activation" but no content is provided.

**Proposed addition** — new subsection §8.11 "Accessibility Service Disclosure":

> Before the user enables the WhatsApp Accessibility Service, the system MUST display:
>
> 1. **What it does**: "RelateAI uses this to send WhatsApp messages on your behalf. It reads the message input field and clicks the send button."
> 2. **What it does NOT do**: "It does not read your messages, contacts, or any other app's data. It only interacts with WhatsApp."
> 3. **How to disable**: "Go to Android Settings → Accessibility → RelateAI → Off."
> 4. **What breaks if disabled**: "WhatsApp messages will not send automatically. SMS and Email will still work."
> 5. **Permissions scope**: `com.whatsapp,com.whatsapp.w4b` only.

This is **required for Play Store review** of apps using Accessibility Services (high-risk permission).

### 2.7 No Crash Reporting / Monitoring Detail (§28) — **MEDIUM**
**Gap**: §28 mentions Crashlytics as "Future: Opt-in" but no decision is documented.

**Proposed addition** — new subsection §28.7 "Crash Reporting Decision":

> **Status**: Deferred to v2 (per §31.3).
>
> **Rationale for v1 deferral**:
> - Privacy-first: No PII leaves device
> - Local-first: No backend for crash ingestion
> - Resource constraints: Small team
>
> **v1 mitigation**:
> - Logcat logs retained (debug builds only)
> - Users can export logs via `adb logcat` for bug reports
> - Manual triage via GitHub Issues
>
> **v2 plan**:
> - Opt-in Firebase Crashlytics (consent during onboarding)
> - Only stack traces, device model, OS version (no PII)
> - Disable in Settings

### 2.8 No Localization Plan Detail (§31, §9.7) — **LOW**
**Gap**: §9.7 lists supported languages (en, hi, id, pt-rBR) but no localization workflow is documented.

**Proposed addition** — new subsection §9.9 "Localization Workflow":
- `values/strings.xml` is the source
- `values-hi/strings.xml` for Hindi, etc.
- Pseudo-locale (`values-en-rXA`) for testing
- Crowdin or Lokalise for community translation (v2)
- AI message generation: `preferredLanguage` field on ContactEntity drives prompt language

### 2.9 No Backup/Restore Detail (§8.8, §10 F-038) — **MEDIUM**
**Gap**: F-038 is "Complete" but BackupManager details are thin.

**Proposed addition** — new subsection §8.12 "Backup/Restore Specification":

> **Export format**: JSON file containing:
> ```json
> {
>   "version": 1,
>   "exportedAtMs": 1717200000000,
>   "contacts": [...],
>   "events": [...],
>   "pendingMessages": [...],
>   "sentMessages": [...],
>   "memoryNotes": [...],
>   "giftHistory": [...],
>   "styleProfile": {...}
> }
> ```
>
> **Encryption**: v1 — None (user must store securely). v2 — AES-256 with user-provided passphrase.
>
> **Import behavior**:
> - Merge strategy: Upsert by ID; conflicts prefer imported
> - Skip if `id` already exists with newer `updatedAt`
> - Show confirmation dialog with summary
>
> **Limitations**:
> - Cannot restore OAuth tokens (user must re-auth)
> - Cannot restore BiometricLock enabled state (security)

### 2.10 No Multi-Module Migration Plan Detail (§14, §30) — **LOW**
**Gap**: P4-01 "Multi-module architecture" is "In Progress" with no migration order documented.

**Proposed addition** — new subsection §14.6 "Module Migration Roadmap":

| Phase | Module | Status | Dependencies |
|---|---|---|---|
| 1 | `:core:domain` | ✅ Done | None |
| 2 | `:core:data` | ✅ Done | `:core:domain` |
| 3 | `:core:ui` | ✅ Done | None |
| 4 | `:feature:splash`, `:feature:login` | ✅ Done | All core |
| 5 | `:feature:dashboard` | ✅ Done | All core |
| 6 | `:feature:contacts`, `:feature:events`, `:feature:analytics` | ✅ Done | All core |
| 7 | `:feature:onboarding`, `:feature:settings` | ✅ Done | All core |
| 8 | UseCase layer in `:core:domain` | ❌ P4-02 | Repository interfaces |
| 9 | Separate `:core:gemini`, `:core:contacts`, `:core:automation` | 🔜 Planned | None |
| 10 | `:core:designsystem` (extract theme/components) | 🔜 Planned | `:core:ui` split |

### 2.11 No In-App Update / Migration Strategy for Users — **HIGH**
**Gap**: No mention of how existing users (v1.x) will be migrated to v2/v3.

**Proposed addition** — new subsection §23.8 "In-App Update Strategy":

> - **Minor updates (1.x → 1.y)**: Auto-update via Play Store; no data migration
> - **Major updates (1.x → 2.0)**: 
>   - Room migrations handle schema
>   - Backup-before-upgrade prompt (Settings)
>   - OAuth re-auth required (token refresh)
>   - WhatsApp re-enable required (Accessibility Service)
> - **Critical hotfixes (1.x → 1.x.y)**: Play Store staged rollout (10% → 50% → 100%)

### 2.12 No Analytics Decision Detail (§28) — **LOW**
**Gap**: §28 says "Local-only analytics" but no event taxonomy is defined.

**Proposed addition** — new subsection §28.8 "Analytics Event Taxonomy":

| Event | Trigger | Properties | Privacy |
|---|---|---|---|
| `app_open` | Cold start | `source` (notification, shortcut) | No PII |
| `onboarding_complete` | All steps done | `durationSec`, `skippedCount` | No PII |
| `contact_synced` | SyncWorker done | `count`, `source` (google, device) | No PII |
| `event_discovered` | EventDiscoveryWorker | `type`, `count` | No PII |
| `message_generated` | GeminiClient success | `variantCount`, `durationMs` | No PII |
| `message_sent` | Sender success | `channel`, `approvalMode` | No PII |
| `message_failed` | Sender error | `channel`, `errorCode` | No PII |
| `approval_approve` | User tapped Approve | `pendingMessageId` | No PII |
| `approval_reject` | User tapped Reject | `pendingMessageId` | No PII |
| `revival_suggested` | RevivalWorker | `contactId` (hashed) | No PII |

Storage: Local DataStore (v2) or append-only JSON file (v1).

---

## 3. Missing Visual / Diagram Content

### 3.1 Sequence Diagram for First-Launch (Onboarding → Main)
SSOT §12.1 has onboarding flow but no sequence diagram showing DI / Worker scheduling. Add a sequence diagram.

### 3.2 Component Diagram for WhatsApp Sender
The flow from `WhatsAppSender.kt` → `WhatsAppAccessibilityService.kt` → `packageNames` filter → text injection is complex but undocumented. Add a sequence diagram.

### 3.3 Entity Lifecycle Diagrams
Each entity (Contact, Event, Pending, Sent) has a lifecycle (created → updated → archived/deleted). Add state diagrams.

---

## 4. Missing Operational Docs

### 4.1 Runbook for Common Production Issues
- "Messages aren't sending" → check AlarmManager, WorkManager status, network
- "Gemini 429" → check RateLimiter, suggest upgrading to paid tier
- "DB migration failed" → restore from backup, log to Crashlytics (v2)

### 4.2 Support / FAQ Document
For Play Store "Help" link:
- How to set up Gemini API key
- How to enable Accessibility Service
- How to back up / restore
- How to enable Biometric lock
- How to switch channels
- How to disable notifications

### 4.3 Versioning / Compatibility Matrix
- minSdk 24 (Android 7.0) — supported until 2024 (Google Play)
- targetSdk 36 (Android 16) — current Play Store requirement
- compileSdk 36 with minorApiLevel = 1

### 4.4 Data Retention Policy
- Sent messages: retained indefinitely (user can manually delete)
- Pending messages: 7 days after scheduledFor (auto-purged)
- Contacts: retained until user signs out
- Logs: 0 days (not retained)

---

## 5. Summary

| Gap | Severity | Action |
|---|---|---|
| Threat Model | HIGH | Add §22.9 |
| Privacy Policy content | HIGH | Add §22.10 + public `privacy-policy.html` |
| Production Release Blockers | HIGH | Add §30.7 |
| Accessibility Service Disclosure | MEDIUM | Add §8.11 |
| Test Plan / Coverage | MEDIUM | Add §27.6 + `docs/TEST_PLAN.md` |
| Backup/Restore spec | MEDIUM | Add §8.12 |
| In-App Update Strategy | HIGH | Add §23.8 |
| Crash Reporting decision | MEDIUM | Add §28.7 |
| Performance Budgets | MEDIUM | Add §29.6 |
| Analytics Event Taxonomy | LOW | Add §28.8 |
| Module Migration Roadmap | LOW | Add §14.6 |
| Localization Workflow | LOW | Add §9.9 |
| Sequence/State diagrams | LOW | Add to relevant sections |
| Runbook | MEDIUM | Create `docs/RUNBOOK.md` |
| FAQ / Support | MEDIUM | Create `docs/FAQ.md` |
| Data Retention Policy | LOW | Add §22.11 |
| Versioning/Compat Matrix | LOW | Add §23.9 |

**Recommended order** (by impact): Privacy Policy → Threat Model → Production Release Blockers → In-App Update → Test Plan → others.
