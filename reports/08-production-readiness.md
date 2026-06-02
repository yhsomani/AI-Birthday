# Report 08 — Production Readiness Checklist

**Date**: 2026-06-01
**Author**: Senior PM + Senior Architect + Senior UX (SSOT Audit)
**Scope**: Go/no-go checklist for v1.0 Play Store submission. Each item: status, evidence, blocker vs nice-to-have.

---

## 1. Verdict: **NOT READY** for Play Store Production

**Target date**: 2-3 weeks from today (mid-Q3 2026), assuming focused 1-engineer execution of P0 items.

**Blocker count**: 5 critical, 7 high.
**Nice-to-have count**: 12 medium, 8 low.

---

## 2. Critical Blockers (Must Fix Before Submission)

### CRIT-01: MasterKey Derivation on Main Thread
- **Status**: ❌ Not started
- **Impact**: ANR on cold start (~200ms on mid-range devices)
- **Owner**: Android Engineer
- **Effort**: 1 day
- **Verification**: Cold-start trace should show MasterKey init in `Dispatchers.IO`

### CRIT-02: No Certificate Pinning
- **Status**: ✅ Done
- **Impact**: MITM attack possible if user installs rogue CA
- **Owner**: Android Engineer
- **Effort**: 1 day
- **Verification**: Network security config has pins for `generativelanguage.googleapis.com` and `people.googleapis.com`

### CRIT-03: Backup JSON Not Encrypted
- **Status**: ✅ Done
- **Impact**: User's full contact list + message history in plaintext if backup is leaked
- **Owner**: Android Engineer
- **Effort**: 2 days
- **Verification**: `BackupManager` export is AES-256-GCM encrypted with user passphrase

### CRIT-04: Dead UI Handlers in `ContactDetailScreen`
- **Status**: 🔄 In Progress
- **Impact**: User taps Edit, DND switch, Custom Send Time → nothing happens → "broken app" reviews
- **Owner**: Android Engineer
- **Effort**: 1-2 days
- **Files**: `feature/contacts/ContactDetailScreen.kt:44, 185, 209`
- **Verification**: All three controls either wire to real handlers or are removed

### CRIT-05: Privacy Policy Not Published
- **Status**: ❌ Not started
- **Impact**: Play Store will reject the app without a Data Safety form / privacy policy URL
- **Owner**: PM + Legal
- **Effort**: 0.5 day
- **Verification**: `privacy-policy.html` published (GitHub Pages or own site); Play Store Data Safety form filled

---

## 3. High-Priority (Strongly Recommended Before Submission)

### HIGH-01: Sign-Out Doesn't Wipe Room DB (FR-05)
- **Status**: ⚠️ Needs verification
- **Effort**: 0.5 day
- **Verification**: After sign-out, Room DB is fully cleared; SQLCipher passphrase dropped; user must re-auth

### HIGH-02: Sign-Out Doesn't Wipe EncryptedSharedPreferences
- **Status**: ⚠️ Needs verification
- **Effort**: 0.5 day
- **Verification**: All keys removed from SecurePrefs on sign-out

### HIGH-03: Duplicated Composables
- **Status**: ✅ Done
- **Files**: `GiftAdvisorView` (2x), `MemoryVaultView` (2x), `StyleCoachScreen` (2x)
- **Effort**: 0.5 day
- **Verification**: Only one definition of each remains; both are imported from the same file

### HIGH-04: Hilt Field Injection in MainActivity (NFR-MAINT-02)
- **Status**: ❌ Not started
- **File**: `MainActivity.kt:35-36`
- **Effort**: 0.5 day
- **Verification**: All `@Inject lateinit var` replaced with constructor injection or `@EntryPoint` pattern

### HIGH-05: Accessibility Service Disclosure Content
- **Status**: ⚠️ Likely present, needs content review
- **File**: `OnboardingScreen.kt` `whatsapp_setup` screen
- **Effort**: 0.5 day
- **Verification**: User sees clear disclosure of what Accessibility Service does/doesn't do before enabling

### HIGH-06: 2-Hour SMART_APPROVE Timeout Verification
- **Status**: ⚠️ Logic exists, needs end-to-end test
- **Effort**: 1 day
- **Verification**: If user doesn't approve within 2h, message sends automatically

### HIGH-07: Test Coverage < 20% Line Coverage
- **Status**: ❌ Current ~15%
- **Impact**: Cannot catch regressions; Play Store quality threshold uncertain
- **Effort**: 2 weeks for 60% coverage
- **Minimum for v1.0**: 30% line coverage (1 week of focused test writing)
- **Verification**: `./gradlew test` + JaCoCo report shows ≥30% line coverage

---

## 4. Medium-Priority (Should Fix in v1.0.x Patch)

| ID | Item | Effort |
|---|---|---|
| MED-01 | Paging 3 for contact list | 2 days |
| MED-02 | Add empty states to Dashboard, Messages, Analytics | 0.5 day |
| MED-03 | Add Snackbar feedback for save actions | 0.5 day |
| MED-04 | Extract hardcoded strings to `strings.xml` (15+ found) | 2 days |
| MED-05 | Simplify onboarding (10 → 7 steps) | 3 days |
| MED-06 | Surface Analytics and Style Coach in bottom nav | 1 day |
| MED-07 | Add Boot Receiver reschedule for all workers (NFR-REL-04) | 0.5 day |
| MED-08 | Add DB indices beyond primary keys | 0.5 day |
| MED-09 | Add Crashlytics opt-in (currently deferred to v2) | 2 days |
| MED-10 | Resolve `org.json` usage in `MainActivity.kt:101-110` | 0.25 day |
| MED-11 | Add Detekt with baseline for legacy issues | 0.5 day |
| MED-12 | Set up CI (`.github/workflows/android.yml`) | 1 day |

---

## 5. Low-Priority (v1.1 / v1.2 Material)

| ID | Item | Effort |
|---|---|---|
| LOW-01 | Adaptive icon | 0.25 day |
| LOW-02 | Custom splash screen | 0.5 day |
| LOW-03 | Haptic feedback on key actions | 0.5 day |
| LOW-04 | Dark theme polish | 1 day |
| LOW-05 | Tablet landscape polish | 1 day |
| LOW-06 | First-time user tooltips | 1 day |
| LOW-07 | Material You dynamic color polish | 0.5 day |
| LOW-08 | KDoc on all public functions (NFR-MAINT-04) | Ongoing |

---

## 6. Non-Code Requirements for Play Store Submission

### 6.1 Store Listing
- ❌ App icon (1024x1024 PNG)
- ❌ Feature graphic (1024x500 PNG)
- ❌ Screenshots (minimum 2 per device type: phone, tablet)
- ❌ Short description (80 chars)
- ❌ Full description (4000 chars)
- ❌ App category (Communication)
- ❌ Content rating (IARC questionnaire)
- ❌ Target audience (Everyone / 13+)
- ❌ Contact email
- ❌ Privacy policy URL (CRIT-05)

### 6.2 Data Safety Form
- ❌ Data collected: Contact info (names, birthdays), App activity (in-app messages)
- ❌ Data shared: None (local-first)
- ❌ Data security: Encrypted in transit (HTTPS), encrypted at rest (SQLCipher)
- ❌ User controls: Data can be deleted on sign-out
- ❌ Data linked to user: No
- ❌ Data used for tracking: No

### 6.3 Permissions Disclosure
- ❌ READ_CONTACTS: Justification required ("To find birthdays and anniversaries")
- ❌ READ_SMS: Justification required ("NOT used; present for legacy reasons, can be removed")
- ❌ READ_CALL_LOG: Justification required ("NOT used; present for legacy reasons, can be removed") — **Remove if not used**
- ❌ SEND_SMS: Justification required ("To send birthday wishes")
- ❌ SCHEDULE_EXACT_ALARM: Justification required ("To send messages at user-specified time")
- ❌ USE_EXACT_ALARM: Justification required
- ❌ RECEIVE_BOOT_COMPLETED: Justification required ("To reschedule background workers after device reboot")
- ❌ REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Justification required ("To ensure workers run on time")
- ❌ FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC: Justification required
- ❌ POST_NOTIFICATIONS: Justification required ("To notify user of pending approvals, revivals, birthdays")
- ❌ Accessibility Service: Required disclosure text per §8.11 (FR-82) and CRIT-05

### 6.4 Accessibility Service Justification (Play Store Review)
Required text in app description and on support page:
> RelateAI uses Android's Accessibility Service to send messages via WhatsApp. The service is scoped to `com.whatsapp` and `com.whatsapp.w4b` (WhatsApp Business) only. It does not read your messages, contacts, or any other app's data. It only interacts with the WhatsApp message input field to type and send the message you approved.

### 6.5 Target API Level
- ✅ targetSdk = 36 (Android 16) — meets Play Store 2026 requirement
- ✅ minSdk = 24 (Android 7.0) — covers 95%+ of active devices

### 6.6 Testing
- ❌ Internal Testing track: 5-10 testers for 1 week
- ❌ Closed Testing track: 50-100 testers for 2 weeks
- ❌ Production track: 10% staged rollout → 50% → 100% over 7 days

### 6.7 App Bundle
- ❌ Build AAB (not APK) for Play Store
- ❌ Enable App Signing by Google Play
- ❌ Upload signing key (or use Google-generated key)

---

## 7. Pre-Submission Verification Script

```bash
#!/bin/bash
# Pre-submission verification — run before each Play Store release

set -e

echo "=== 1. Build Verification ==="
./gradlew clean
./gradlew assembleDebug assembleRelease
echo "✅ Build successful"

echo "=== 2. Unit Tests ==="
./gradlew test
echo "✅ Unit tests pass"

echo "=== 3. Lint ==="
./gradlew lint
test -f app/build/reports/lint-results-debug.html || { echo "❌ Lint report missing"; exit 1; }
echo "✅ Lint passes"

echo "=== 4. Static Analysis ==="
./gradlew detekt
echo "✅ Detekt passes"

echo "=== 5. Coverage Check ==="
./gradlew jacocoTestReport
COVERAGE=$(grep -oP 'Total[^%]*\K\d+\.\d+' core/data/build/reports/jacoco/test/jacocoTestReport.xml | head -1)
if (( $(echo "$COVERAGE < 30" | bc -l) )); then
    echo "❌ Coverage $COVERAGE% < 30%"
    exit 1
fi
echo "✅ Coverage $COVERAGE% ≥ 30%"

echo "=== 6. No Hardcoded Strings in Composables ==="
HARDCODED=$(grep -rn 'Text("[A-Z]' --include='*.kt' feature/ core/ui/ | wc -l)
if [ "$HARDCODED" -gt 0 ]; then
    echo "⚠️  $HARDCODED hardcoded strings found (should be 0)"
    echo "Run: grep -rn 'Text(\"[A-Z]' --include='*.kt' feature/ core/ui/"
fi

echo "=== 7. No printStackTrace ==="
PST=$(grep -rn 'printStackTrace()' --include='*.kt' core/ feature/ app/ | grep -v test/ | wc -l)
if [ "$PST" -gt 0 ]; then
    echo "❌ $PST printStackTrace() calls found"
    exit 1
fi
echo "✅ No printStackTrace in production code"

echo "=== 8. No !! (null assertion) in production code ==="
NULL_ASSERT=$(grep -rn '!!' --include='*.kt' core/ feature/ app/ | grep -v test/ | wc -l)
if [ "$NULL_ASSERT" -gt 0 ]; then
    echo "⚠️  $NULL_ASSERT !! usages found (review needed)"
fi

echo "=== 9. Manifest Permissions ==="
grep -E 'uses-permission' app/src/main/AndroidManifest.xml

echo "=== 10. ProGuard Rules ==="
test -f app/proguard-rules.pro && echo "✅ ProGuard rules exist" || echo "❌ ProGuard rules missing"

echo ""
echo "=== Pre-submission verification complete ==="
```

---

## 8. Recommended Release Phases

### Phase 0: Internal Testing (Week 1-2 after P0 fixes)
- Upload to Play Console Internal Testing
- 5-10 testers (team + close friends)
- Monitor Logcat for crashes (manual)
- Verify core flows: onboarding, contact sync, message generation, send via all 3 channels, approval workflow, backup, restore

### Phase 1: Closed Beta (Week 3-4)
- 50-100 external testers
- Enable Crashlytics opt-in
- Monitor DAU, crash-free rate, message-send success rate
- Collect user feedback (Google Form)

### Phase 2: Open Beta (Week 5-6, optional)
- Public beta listing (no Play Store promotion, but discoverable)
- 500-1000 testers
- A/B test onboarding flow (10 steps vs proposed 7 steps)

### Phase 3: Staged Production (Week 7+)
- 10% rollout for 3 days (monitor for crashes)
- 50% rollout for 3 days
- 100% rollout
- Monitor KPIs: DAU/MAU, messages sent, crash-free rate

---

## 9. Rollback Plan

If v1.0 has critical issues:
- **Play Console**: Halt staged rollout (1-click)
- **Hotfix**: Release v1.0.1 via emergency rollout (skip staged)
- **Disable specific features remotely**: Build variants (no flag system in v1 per ADR-015)
- **Communicate**: Update store listing, post on support page

---

## 10. Sign-Off Criteria

v1.0 is **approved for production** when:

- [ ] All 5 CRIT items resolved
- [ ] All 7 HIGH items resolved OR explicitly deferred with PM sign-off
- [ ] Test coverage ≥30%
- [ ] All CRIT/HIGH lint warnings resolved
- [ ] Pre-submission verification script passes
- [ ] Internal testing completed with 0 critical bugs
- [ ] Closed beta (50+ users) for ≥1 week with crash-free rate ≥99%
- [ ] Privacy policy live and linked
- [ ] Data Safety form filled
- [ ] Permissions disclosure text reviewed by legal
- [ ] PM, Architect, and UX leads have signed off in writing

**Estimated time to green**: 2-3 weeks with focused 1-engineer execution.
