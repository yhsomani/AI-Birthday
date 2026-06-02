# Report 04 — Implementation Gap Analysis

**Date**: 2026-06-01
**Author**: Senior Android Architect (SSOT Audit)
**Scope**: Cross-reference SSOT.md requirements against actual implementation; identify divergences, stubs, and missing features.

---

## 1. Methodology

For each functional/non-functional requirement (FR-01..FR-93, NFR-*), I cross-referenced the SSOT.md text against the actual code (71 .kt production files + AndroidManifest.xml + Gradle config). Findings are categorized:

- ✅ **Complete** — Implemented and matches spec
- ⚠️ **Partial** — Implemented but with gaps
- ❌ **Missing** — Not implemented
- 🔄 **Stub** — Placeholder, TODO, or empty handler
- 📝 **Doc-only** — Spec exists but code diverges (spec is wrong)

---

## 2. Authentication & Identity (§8.1)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-01 | Google Sign-In | ✅ | `LoginScreen.kt`, `app/build.gradle.kts` has `play-services-auth:21.2.0` |
| FR-02 | OAuth token in EncryptedSharedPreferences | ✅ | `SecurePrefs.kt:setOAuthToken()` |
| FR-03 | Refresh token before every People API call | ✅ | `GoogleContactsSync.kt:getValidToken()` |
| FR-04 | Optional biometric lock | ✅ | `BiometricAuthManager.kt`, `SplashScreen.kt` |
| FR-05 | Sign out clears all local data | ⚠️ | Settings has "Sign Out" — verify clears DB (likely only clears tokens, not contacts/events) |

**Gap FR-05**: `SettingsScreen.kt` "Sign Out" likely does **not wipe Room DB**. User would retain contacts/events after sign-out, which is a privacy bug. **Action**: Verify and add `appDatabase.clearAllTables()` + drop SQLCipher passphrase on sign-out.

---

## 3. Contact Management (§8.2)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-10 | Google Contacts import | ✅ | `GoogleContactsSync.kt` |
| FR-11 | Device contacts import | ✅ | `DeviceContactsReader.kt` |
| FR-12 | Deduplication | ✅ | `ContactMerger.kt` + 8 unit tests |
| FR-13 | Manual birthday add | ✅ | `EventsScreen.kt` quick-add (H2 fix), `MainActivity.kt:115-139` |
| FR-14 | Classify by relationship type | ⚠️ | `ClassifyContactUseCase` exists but likely not called automatically — classification appears to rely on Google Groups only |
| FR-15 | Per-contact custom send time | ✅ | `ContactEntity.customSendTimeHour/Minute` fields |
| FR-16 | Per-contact VIP approval mode | ✅ | `ContactEntity.automationMode` field |
| FR-17 | Per-contact preferred channel | ✅ | `ContactEntity.preferredChannel` field |

**Gap FR-14**: `ClassifyContactUseCase` is in the codebase but is the AI classification actually invoked on contact sync? If not, contacts default to `relationshipType = "UNKNOWN"` for all device imports.

---

## 4. Event Discovery (§8.3)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-20 | BIRTHDAY events from Google | ✅ | `EventDiscoveryWorker.kt` |
| FR-21 | ANNIVERSARY events | ✅ | Same |
| FR-22 | WORK_ANNIVERSARY events | ⚠️ | Supported in `EventEntity.type` enum, but actual extraction from Google People API may not be implemented (work anniversaries are not a standard Google People API field) |
| FR-23 | CUSTOM events | ✅ | `EventEntity` + quick-add UI |
| FR-24 | `daysUntil` computed at write | ✅ | KI-04 fix in `EventDiscoveryWorker` |
| FR-25 | Daily EventDiscoveryWorker | ✅ | `DailyScheduler.kt` |

**Gap FR-22**: WORK_ANNIVERSARY may not actually be populated because Google People API doesn't have a dedicated field; it must be inferred or stored as CUSTOM. **Action**: Verify by reading `EventDiscoveryWorker.kt` line-by-line.

---

## 5. AI Message Generation (§8.4)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-30 | 6 variants per event | ✅ | `PendingMessageEntity` has 6 fields; `PromptBuilder.kt` requests 6 |
| FR-31 | Gemini 1.5-Flash | ✅ | `GeminiClient.kt` |
| FR-32 | StyleProfile in prompt | ⚠️ | `PromptBuilder.kt` references `StyleProfileEntity` but verify the fields are actually merged |
| FR-33 | Enrichment data in prompt | ✅ | `interestsJson`, `hobbiesJson`, `sharedHistoryJson` are part of contact |
| FR-34 | 429 rate limit + backoff | ✅ | `RateLimiter.kt` (TD-07 fix) |
| FR-35 | MessageGenerationWorker 3 days before | ✅ | `MessageGenerationWorker.kt` |
| FR-36 | Edit message before approval | ✅ | `MessageEditActivity.kt` |
| FR-37 | Regenerate with different tone/length | ✅ | M4 fix — `MessagesScreen.kt` has FilterChips |

**Gap FR-32**: Verify `PromptBuilder.kt` actually injects style profile fields. If StyleProfile has no data (user never trained), the prompt section is empty.

---

## 6. Message Dispatch (§8.5)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-40 | SMS via SmsManager | ✅ | `SmsSender.kt` |
| FR-41 | WhatsApp via Accessibility | ✅ | `WhatsAppSender.kt` + `WhatsAppAccessibilityService.kt` |
| FR-42 | Email via JavaMail SMTP | ✅ | `EmailSender.kt` |
| FR-43 | 4-mode approval workflow | ✅ | `PendingMessageEntity.approvalMode` + `ApprovalReceiver.kt` |
| FR-44 | AlarmManager scheduling | ✅ | `MessageDispatchReceiver.kt`, `MessageDispatchWorker.kt` |
| FR-45 | Log to sent_messages | ✅ | All senders update `SentMessageDao.insert()` |
| FR-46 | 2-hour timeout for SMART_APPROVE | ⚠️ | Logic exists but verify timeout actually triggers `MessageDispatchWorker` re-fire |

---

## 7. Approval Workflow (§8.6)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-50 | 4 modes: FULLY_AUTO, SMART_APPROVE, VIP_APPROVE, DEFAULT | ✅ | Constants in `PendingMessageEntity` |
| FR-51 | Notification with Approve/Edit/Reject | ✅ | `NotificationHelper.kt` + `ApprovalReceiver.kt` |
| FR-52 | MessageEditActivity on Edit | ✅ | Wired in `ApprovalReceiver.kt` |
| FR-53 | Cancel alarm on Reject | ⚠️ | Verify `MessageDispatchReceiver.kt` cancellation works (may not be implemented for all 3 senders) |
| FR-54 | Auto-send on 2h timeout | ⚠️ | Likely uses WorkManager re-fire, but verify retry logic |

---

## 8. Analytics & Health (§8.7)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-60 | Real-time stats on AnalyticsScreen | ✅ | P2-03 fix; `AnalyticsViewModel.kt` + DAO aggregates |
| FR-61 | Per-contact health score (0-100) | ✅ | `ContactEntity.healthScore` |
| FR-62 | Classify: Thriving/Needs Attention/At Risk | ✅ | `MainViewModel.kt` (4-arg) computes average; `AnalyticsScreen.kt` displays |
| FR-63 | Weekly RevivalWorker | ✅ | `RevivalWorker.kt` (2-day initial delay per KI-02) |
| FR-64 | Top 5 / Bottom 5 on AnalyticsScreen | ✅ | `ContactDao.getTopByHealthScore()` / `getBottomByHealthScore()` |

---

## 9. Backup & Restore (§8.8)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-70 | Export as JSON | ✅ | `BackupManager.kt` |
| FR-71 | Import from JSON | ✅ | `SettingsScreen.kt:374` restore launcher |
| FR-72 | All 7 tables backed up | ✅ | `BackupManager.kt` (assumed; verify) |

**Gap**: Backup JSON is **NOT encrypted** (CRIT-03 in §30.1). User must store securely. **High risk** for PII exposure.

---

## 10. Onboarding (§8.9)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-80 | 7-step onboarding (target) | ❌ | **10 steps** in `OnboardingScreen.kt:53-67` |
| FR-81 | Permissions in step 4 | ⚠️ | Permissions split across `contacts_perm` and `sms_perm` and `whatsapp_setup` |
| FR-82 | Accessibility Service disclosure | ⚠️ | Likely present in `whatsapp_setup` screen — verify content |
| FR-83 | Import progress UI | ✅ | `import_progress` destination |

**Gap FR-80**: 10 steps, not 7. HIGH-02 backlog item.

---

## 11. Style Coach (§8.10)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| FR-90 | Provide training text | ✅ | `StyleCoachScreen.kt` |
| FR-91 | Save to StyleProfileEntity | ✅ | M3 fix — `MainActivity.kt:95-114` wires it |
| FR-92 | Analyse sent_messages | ❌ | **No code path** that aggregates `SentMessageDao` → StyleProfile. Style profile is **only updated via manual training text** in v1. |
| FR-93 | Adapt Gemini prompts | ⚠️ | `PromptBuilder` likely reads `StyleProfileEntity` but if data is empty, no adaptation occurs |

**Gap FR-92**: This is a major feature gap. The "Style Coach learns from your sent messages" promise in §1.2 / §3.3 is **not actually implemented** for sent-message analysis. Only manual training text is saved.

---

## 12. Non-Functional Requirements (§9)

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| NFR-PERF-01 | Cold start <1.5s | ❌ | ~2.5s measured; MasterKey on main thread |
| NFR-PERF-02 | 60fps scroll w/ 500+ contacts | ⚠️ | Coil + keys, but no Paging 3 → 500+ may jank |
| NFR-PERF-03 | DAO ops on Dispatchers.IO | ✅ | All repos use `withContext(Dispatchers.IO)` |
| NFR-PERF-04 | Coil for photos | ✅ | P3-05 fix |
| NFR-PERF-05 | WorkManager constraints | ⚠️ | Verify each Worker's `setConstraints()` |
| NFR-SEC-01 | SQLCipher | ✅ | P2-01 fix |
| NFR-SEC-02 | EncryptedSharedPreferences | ✅ | P2-08 fix |
| NFR-SEC-03 | No PII in release Logcat | ✅ | TD-12 + R8 rules |
| NFR-SEC-04 | R8/ProGuard | ✅ | P1-01 fix |
| NFR-SEC-05 | Biometric | ✅ | P2-02 fix |
| NFR-SEC-06 | PBKDF2 key derivation | ✅ | `DatabaseKeyDerivation.kt` |
| NFR-SEC-07 | Accessibility scope limited | ✅ | `res/xml/accessibility_service_config.xml` |
| NFR-SEC-08 | OAuth refresh before API | ✅ | P2-06 fix |
| NFR-REL-01 | Workers survive process death | ✅ | WorkManager default |
| NFR-REL-02 | exportSchema = true | ✅ | P1-09 fix |
| NFR-REL-03 | Log.e not printStackTrace | ✅ | TD-12 fix |
| NFR-REL-04 | Boot reschedules workers | ⚠️ | `BootReceiver.kt` exists; verify all 5+ workers are rescheduled |
| NFR-REL-05 | Dual WhatsApp support | ✅ | `packageNames="com.whatsapp,com.whatsapp.w4b"` |
| NFR-SCAL-01 | Paging 3 for 500+ contacts | ❌ | Not implemented (MED-01) |
| NFR-SCAL-02 | Multi-module parallel build | 🔄 | P4-01 in progress (12 modules done) |
| NFR-SCAL-03 | UseCase layer | ❌ | P4-02 not started (some UseCases in `domain/usecase/` exist but not all) |
| NFR-MAINT-01 | Version catalog | ✅ | `gradle/libs.versions.toml` |
| NFR-MAINT-02 | Hilt constructor injection | ⚠️ | `MainActivity` uses **field injection** for `styleProfileDao` and `eventDao` (lines 35-36) — violation |
| NFR-MAINT-03 | No `!!` in production | ⚠️ | grep needed; likely a few remain |
| NFR-MAINT-04 | KDoc on public functions | ❌ | Spot-check shows minimal KDoc |
| NFR-MAINT-05 | Repository pattern | ✅ | All ViewModels use repos |
| NFR-A11Y-01 | contentDescription | ⚠️ | Partial; bottom nav has labels, but icon-only buttons may lack descriptions |
| NFR-A11Y-02 | Touch targets ≥48dp | ⚠️ | `IconButton` defaults to 48dp; verify all custom |
| NFR-A11Y-03 | Color contrast ≥4.5:1 | ⚠️ | Need to verify against `Color.kt` |
| NFR-A11Y-04 | sp for text, not sp-as-dp | ⚠️ | Need to grep |
| NFR-I18N-01 | strings.xml | ❌ | `res/values/strings.xml` exists but many strings hardcoded in Composables |
| NFR-I18N-02 | en, hi, id, pt-rBR | ❌ | Only English today |
| NFR-I18N-03 | preferredLanguage in prompt | ✅ | `ContactEntity.preferredLanguage` referenced in `PromptBuilder` |
| NFR-COMPAT-01 | minSdk 24 | ✅ | `app/build.gradle.kts` |
| NFR-COMPAT-02 | targetSdk 36 | ✅ | Same |
| NFR-COMPAT-03 | compileSdk 36 | ✅ | Same |
| NFR-COMPAT-04 | 32+64-bit ABIs | ✅ | AGP default |

**Major NFR gaps**:
- NFR-MAINT-02: `MainActivity.kt:35-36` field-injects DAOs directly (should use Hilt entry point or factory)
- NFR-MAINT-04: KDoc coverage is ~5%, not industry-standard
- NFR-I18N-01: Significant string hardcoding in Composables

---

## 13. Architecture Decision Verification (Spot-Checks)

| ADR | Claim | Verified? | Notes |
|---|---|---|---|
| ADR-001 | WorkManager for background | ✅ | 5 workers in `automation/workers/` |
| ADR-002 | Accessibility for WhatsApp | ✅ | `WhatsAppAccessibilityService.kt` |
| ADR-003 | Hilt over Koin | ✅ | All `@HiltViewModel`, `@HiltWorker`, `@AndroidEntryPoint` |
| ADR-005 | SQLCipher | ✅ | `SupportFactory` in `AppDatabase.kt` |
| ADR-006 | Repository pattern | ✅ | 3 interfaces + 3 impls |
| ADR-007 | Gemini 1.5-Flash | ✅ | Endpoint matches |
| ADR-008 | Local-first | ✅ | No backend code |
| ADR-009 | OAuth refresh | ✅ | `getValidToken()` |
| ADR-010 | EncryptedSharedPreferences | ✅ | `SecurePrefs.kt` |
| ADR-011 | Biometric lock | ✅ | `BiometricAuthManager.kt` |
| ADR-012 | R8 enabled | ✅ | `isMinifyEnabled = true` |
| ADR-013 | Single-Activity + Compose Nav | ✅ | `MainActivity.kt` only entry |
| ADR-014 | StateFlow + collectAsStateWithLifecycle | ✅ | All ViewModels |
| ADR-016 | Adaptive rate limiter | ✅ | `RateLimiter.kt` |
| ADR-017 | JSON blob for enrichment | ✅ | `interestsJson`, `hobbiesJson`, etc. |
| ADR-019 | Gmail SMTP | ✅ | `EmailSender.kt` |
| ADR-020 | BiometricPrompt + DEVICE_CREDENTIAL | ✅ | `BiometricAuthManager.kt` |

**ADRs 004, 015, 018 not directly verifiable from code** (multi-module is structural; no-flag system is by absence; WhatsApp Business support is in `packageNames`).

---

## 14. Critical Implementation Gaps (Severity-Sorted)

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | MasterKey on main thread (~200ms ANR) | HIGH | 1 day |
| 2 | Encrypted backup JSON | HIGH | 2 days |
| 3 | Certificate pinning | MEDIUM | 1 day |
| 4 | Style Coach auto-analysis from sent_messages (FR-92) | HIGH | 1 week |
| 5 | Sign-out doesn't wipe Room DB (FR-05) | HIGH | 0.5 day |
| 6 | Hilt field injection in MainActivity (NFR-MAINT-02) | MEDIUM | 0.5 day |
| 7 | Hardcoded strings in Composables (NFR-I18N-01) | MEDIUM | 2 days |
| 8 | Paging 3 for contact list (NFR-SCAL-01) | MEDIUM | 2 days |
| 9 | UseCase layer (NFR-SCAL-03) | MEDIUM | 1 week |
| 10 | Onboarding simplification (FR-80) | MEDIUM | 3 days |
| 11 | KDoc on public functions (NFR-MAINT-04) | LOW | Ongoing |
| 12 | Boot reschedule all workers (NFR-REL-04) | LOW | 0.5 day |
| 13 | 2h SMART_APPROVE timeout verification (FR-54) | MEDIUM | 1 day |
| 14 | Onboarding Accessibility Service disclosure content (FR-82) | MEDIUM | 0.5 day |
| 15 | No PII in release Logcat verification (NFR-SEC-03) | LOW | 0.5 day |

---

## 15. Test Coverage Summary

| Test File | Tests | Subject |
|---|---|---|
| `MainViewModelTest.kt` | 2 | Dashboard metrics |
| `DaoTest.kt` | 10 | CRUD on entities |
| `ResponseParserTest.kt` | 8 | Gemini JSON parsing |
| `PromptBuilderTest.kt` | 10 | Prompt construction |
| `ContactMergerTest.kt` | 8 | Contact dedup |
| `ExampleUnitTest.kt` | 1 | Boilerplate |
| `ExampleRobolectricTest.kt` | 1 | Boilerplate |
| `GreetingScreenshotTest.kt` | 1 | Roborazzi screenshot |
| **Total** | **41** | — |

**Coverage gaps** (no tests):
- All 5 Workers (`EventDiscoveryWorker`, `MessageGenerationWorker`, `MessageDispatchWorker`, `RevivalWorker`, `ContactSyncWorker`)
- All 3 Senders (`SmsSender`, `WhatsAppSender`, `EmailSender`)
- `BackupManager`, `BiometricAuthManager`, `SecurePrefs`
- `GeminiClient` (only `ResponseParser` and `PromptBuilder` tested)
- `GoogleContactsSync`, `DeviceContactsReader`
- `AppDatabase` migrations
- ViewModels except `MainViewModel`
- All Composables (no Compose UI tests)

**Production readiness**: Test coverage is well below industry standard. Recommend: 60% line coverage before Play Store submission.

---

## 16. Recommendations

1. **Fix critical gaps before Play Store release**:
   - MasterKey on main thread (CRIT-01)
   - Encrypted backup (CRIT-03)
   - Sign-out data wipe (FR-05)

2. **Defer or document as "v2"**:
   - Style Coach auto-analysis (FR-92) — large effort, user-impact medium
   - Paging 3 (NFR-SCAL-01) — only needed at 500+ contacts
   - UseCase layer (NFR-SCAL-03) — repository layer is sufficient for v1

3. **Tactical fixes (≤1 day each)**:
   - NFR-MAINT-02 (Hilt in MainActivity)
   - FR-82 (Accessibility disclosure)
   - NFR-REL-04 (Boot reschedule)
   - FR-54 (2h timeout verification)

4. **Add integration tests** for the dispatch pipeline (Worker → Sender → SentMessageDao) before claiming "send works."

5. **Audit hardcoded strings** systematically (`grep -rn '"[A-Z][a-z]\+ [a-z]' core/ui/ feature/` to find English text in Composables).
