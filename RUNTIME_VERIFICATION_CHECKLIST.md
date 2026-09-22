# Runtime Verification Checklist

**Purpose:** Prove that the implemented AI features actually work in a runtime environment before expanding functionality.

**Status:** NOT_STARTED

---

## Prerequisites

### Firebase Project Setup

- [ ] Firebase project created (staging or production)
- [ ] Vertex AI API enabled in Google Cloud Console
- [ ] Gemini API key generated and stored securely
- [ ] `google-services.json` downloaded and placed in `android/app/`
- [ ] App Check configured with Play Integrity

### Development Environment

- [ ] Android Studio installed and updated
- [ ] JDK 17+ configured
- [ ] Android SDK with API 34+ installed
- [ ] Physical test device available (Android 10+)
- [ ] USB debugging enabled on test device
- [ ] ADB working: `adb devices` shows device

### Configuration Files

- [ ] `local.properties` contains API key (for lab builds only):
  ```properties
  gemini.api.key=YOUR_API_KEY_HERE
  ```
- [ ] OR Secrets Manager configured for production builds

### Test Accounts

- [ ] Google test account created
- [ ] Test account added to Firebase Auth allowed list
- [ ] Test contacts synced to device (5-10 contacts with birthdays)

---

## Phase 1: Build & Install

### Step 1.1: Build Lab Flavor (Debug)

```bash
cd /workspace/android
./gradlew clean assembleLabDebug
```

**Expected Output:**

```
BUILD SUCCESSFUL in XXs
Build output: android/app/build/outputs/apk/lab/debug/app-lab-debug.apk
```

**Verification:**

- [ ] APK exists: `ls -lh android/app/build/outputs/apk/lab/debug/`
- [ ] APK size reasonable: ~40-80 MB
- [ ] No build errors in console

### Step 1.2: Install on Device

```bash
adb install -r android/app/build/outputs/apk/lab/debug/app-lab-debug.apk
```

**Expected Output:**

```
Success
```

**Verification:**

- [ ] App icon appears on device home screen
- [ ] App launches without crash
- [ ] Package name correct: `com.yashsomani.birthdayautopilot`

---

## Phase 2: Basic Functionality

### Step 2.1: Launch & Welcome

**Actions:**

1. Tap app icon
2. Observe welcome screen

**Expected Results:**

- [ ] App launches within 3 seconds
- [ ] No crash dialogs
- [ ] Welcome screen displays in English
- [ ] "Get Started" button visible

**Evidence:** Screenshot #1 (welcome screen)

### Step 2.2: Google Sign-In

**Actions:**

1. Tap "Get Started"
2. Tap "Continue with Google"
3. Select test account
4. Grant permissions

**Expected Results:**

- [ ] Google picker appears
- [ ] Sign-in completes within 5 seconds
- [ ] Contacts permission screen appears
- [ ] No auth errors

**Evidence:** Screenshot #2 (signed-in state)

### Step 2.3: Contacts Sync

**Actions:**

1. Review contacts permission dialog
2. Grant READ_CONTACTS permission
3. Wait for sync to complete

**Expected Results:**

- [ ] Permission dialog explains why contacts needed
- [ ] Sync progress indicator shows
- [ ] Contact count displays (e.g., "12 contacts found")
- [ ] At least 3 contacts have birthday dates

**Evidence:** Screenshot #3 (contacts list)

---

## Phase 3: AI Feature Testing

### Step 3.1: Navigate to Message Creation

**Actions:**

1. Tap on a contact with upcoming birthday
2. Tap "Create Message" or "Write Message"

**Expected Results:**

- [ ] Message creation screen opens
- [ ] Contact name displays
- [ ] Birthday date displays
- [ ] Text editor visible
- [ ] "Suggest" or "AI Assist" button visible

**Evidence:** Screenshot #4 (message screen)

### Step 3.2: Trigger AI Suggestion

**Actions:**

1. Select relationship (e.g., "Friend")
2. Select tone (e.g., "Warm")
3. Tap "Suggest" button
4. Start timer

**Expected Results:**

- [ ] Loading indicator appears immediately
- [ ] No ANR (App Not Responding) dialog
- [ ] Suggestion appears within 15 seconds
- [ ] Suggestion is relevant to birthday context
- [ ] Suggestion matches selected tone

**Metrics:**

- Response time: **\_\_\_** seconds
- Target: < 10 seconds

**Evidence:**

- Screenshot #5 (loading state)
- Screenshot #6 (suggestion displayed)
- Logcat excerpt (sanitized)

### Step 3.3: Verify Fallback Behavior

**Actions:**

1. Turn off WiFi and mobile data
2. Return to message creation screen
3. Tap "Suggest" button

**Expected Results:**

- [ ] Loading indicator appears briefly
- [ ] Error message or fallback template shown
- [ ] No crash
- [ ] User can still manually edit message

**Evidence:** Screenshot #7 (offline fallback)

### Step 3.4: Rate Limiting Test

**Actions:**

1. Re-enable network
2. Rapidly tap "Suggest" 10 times
3. Observe behavior

**Expected Results:**

- [ ] First 8 requests succeed (or per configured limit)
- [ ] Subsequent requests show rate limit message
- [ ] No duplicate suggestions
- [ ] App remains responsive

**Evidence:** Logcat showing rate limit enforcement

---

## Phase 4: Privacy & Security Verification

### Step 4.1: Logcat Audit

**Actions:**

```bash
adb logcat | grep -i "birthday\|gemini\|ai" > /tmp/ai_logs.txt
```

**Check For:**

- [ ] NO contact names in logs
- [ ] NO phone numbers in logs
- [ ] NO birthdates in logs
- [ ] NO full message content in logs
- [ ] Gemini API calls use opaque identifiers only

**Evidence:** Sanitized log excerpt attached

### Step 4.2: Network Traffic Analysis (Optional)

**Actions:**

1. Enable HTTP proxy (e.g., Charles Proxy, Wireshark)
2. Capture traffic during AI suggestion
3. Inspect request body

**Expected Results:**

- [ ] Request contains relationship type only (not names)
- [ ] Request contains tone preference only
- [ ] No PII transmitted to Gemini API
- [ ] HTTPS encryption verified

**Evidence:** Network capture (redacted)

---

## Phase 5: Performance Metrics

### Step 5.1: Cold Start Time

**Measurement:**

```bash
adb shell am start -W com.yashsomani.birthdayautopilot/.MainActivity
```

**Result:**

- TotalTime: **\_\_\_** ms
- Target: < 2000 ms

### Step 5.2: AI Response Time (Multiple Samples)

| Attempt | Response Time (ms) | Success? | Notes |
| ------- | ------------------ | -------- | ----- |
| 1       |                    |          |       |
| 2       |                    |          |       |
| 3       |                    |          |       |
| 4       |                    |          |       |
| 5       |                    |          |       |

**Statistics:**

- Average: **\_\_\_** ms
- p95: **\_\_\_** ms
- Min: **\_\_\_** ms
- Max: **\_\_\_** ms

### Step 5.3: Memory Usage

**Measurement:**

```bash
adb shell dumpsys meminfo com.yashsomani.birthdayautopilot
```

**Results:**

- Total PSS: **\_\_\_** MB
- Target: < 200 MB

---

## Phase 6: Edge Cases

### Step 6.1: Network Transition

**Test:**

1. Start AI request on WiFi
2. Mid-request, turn off WiFi (let it failover to cellular)
3. Observe result

**Expected:**

- [ ] Request completes successfully OR
- [ ] Graceful failure with retry option

### Step 6.2: Background/App Switch

**Test:**

1. Trigger AI request
2. Immediately press Home button
3. Wait 5 seconds
4. Return to app

**Expected:**

- [ ] Request continues in background OR
- [ ] Clear indication of interrupted state
- [ ] No crash on return

### Step 6.3: Low Battery Mode

**Test:**

1. Enable battery saver mode
2. Trigger AI request

**Expected:**

- [ ] Request succeeds (may be slower) OR
- [ ] Clear message about power restrictions

### Step 6.4: Screen Rotation

**Test:**

1. Trigger AI request
2. Rotate device mid-request
3. Observe behavior

**Expected:**

- [ ] Request continues (not cancelled)
- [ ] UI state preserved
- [ ] No duplicate requests

---

## Pass/Fail Criteria

### Must Pass (Blockers)

- [ ] App launches without crash
- [ ] Google sign-in works
- [ ] Contacts sync completes
- [ ] AI suggestion returns within 15 seconds
- [ ] Offline fallback works
- [ ] No PII in logs
- [ ] No crashes during testing

### Should Pass (High Priority)

- [ ] AI response time < 10 seconds (avg)
- [ ] Rate limiting enforced
- [ ] Network transitions handled gracefully
- [ ] Background execution works

### Nice to Pass (Medium Priority)

- [ ] Response time < 5 seconds (p95)
- [ ] Memory usage < 150 MB
- [ ] All edge cases handled perfectly

---

## Results Summary

**Test Date:** **\*\***\_\_\_**\*\***

**Tester:** **\*\***\_\_\_**\*\***

**Device:** **\*\***\_\_\_**\*\***

**Android Version:** **\*\***\_\_\_**\*\***

**Network Conditions:** **\*\***\_\_\_**\*\***

### Overall Result

- [ ] ✅ PASS — Ready for expansion
- [ ] ⚠️ PARTIAL — Minor issues, document and proceed
- [ ] ❌ FAIL — Blockers found, fix before continuing

### Issues Found

| ID  | Severity | Description | Workaround | Status |
| --- | -------- | ----------- | ---------- | ------ |
| 1   |          |             |            |        |
| 2   |          |             |            |        |
| 3   |          |             |            |        |

### Evidence Collected

- [ ] Screenshots (minimum 7)
- [ ] Logcat excerpts (sanitized)
- [ ] Performance metrics
- [ ] Network captures (optional)

---

## Next Steps After Verification

### If PASS:

1. Create RUNTIME_VERIFICATION_REPORT.md with full results
2. Update SSOT.md: change status to `RUNTIME_VERIFIED`
3. Proceed to Phase 1 AI expansion (multiple variations)
4. Schedule real-device E2E testing

### If PARTIAL:

1. Document all issues in tracker
2. Implement quick fixes (< 4 hours each)
3. Re-test failed scenarios
4. Decide: proceed with known issues or block

### If FAIL:

1. Stop expansion work immediately
2. Prioritize bug fixes
3. Re-run verification after fixes
4. Do not proceed until PASS

---

## Appendix: Useful Commands

### Logcat Filtering

```bash
# AI-related logs
adb logcat | grep -i "gemini\|ai\|suggestion"

# Crash logs
adb logcat | grep -i "fatal\|exception\|crash"

# Network logs
adb logcat | grep -i "http\|network\|connect"

# Save to file
adb logcat -d > full_logcat.txt
```

### Performance Profiling

```bash
# CPU usage
adb shell top -m 10 | grep birthday

# Memory info
adb shell dumpsys meminfo com.yashsomani.birthdayautopilot

# Battery stats
adb shell dumpsys batterystats --checkin
```

### Build Commands

```bash
# Clean build
./gradlew clean assembleLabDebug

# Install
adb install -r app/build/outputs/apk/lab/debug/app-lab-debug.apk

# Uninstall
adb uninstall com.yashsomani.birthdayautopilot
```

---

**Document Created:** 2026-08-29  
**Template Version:** 1.0  
**Usage:** Copy this checklist for each runtime verification session
