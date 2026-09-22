# AI Expansion Implementation Plan

## Executive Summary

**Goal:** Expand AI usage from single message suggestions to "AI everywhere" throughout the app workflow.

**Current State:** 
- ✅ Native Android Gemini integration implemented (device-only)
- ✅ Dead JavaScript AI code removed (1,164 lines)
- ✅ 387 tests passing
- ⚠️ NOT_RUNTIME_VERIFIED — needs real device testing
- 🚫 NOT_DEPLOYED — Firebase project not configured

**Architecture:** React Native → BirthdayNativeAdapter → AndroidGeminiSuggestionGateway → Firebase AI SDK → Gemini API

---

## Phase 0: Documentation & Verification (P0 - Before Any Expansion)

### 0.1 Update All Documentation ✅ COMPLETED

- [x] SSOT.md updated with:
  - Dead code removal details (file names)
  - AI architecture clarification (native-only)
  - AI expansion roadmap added to Principal Gaps
  - Status labels: NOT_RUNTIME_VERIFIED, NOT_DEPLOYED

- [x] AI_ARCHITECTURE_SUMMARY.md created/updated
- [x] NEXT_STEPS_AI_EXPANSION.md created

### 0.2 Runtime Verification Checklist

**Prerequisites:**
- [ ] Firebase project created with Vertex AI enabled
- [ ] `google-services.json` configured for test device
- [ ] API key configured in `local.properties` or Secrets Manager
- [ ] Test device: Android 10+, Google Play Services available

**Verification Steps:**

```bash
# 1. Build lab flavor (testing configuration)
cd /workspace/android
./gradlew assembleLabDebug

# 2. Install on physical device
adb install -r app/build/outputs/apk/lab/debug/app-lab-debug.apk

# 3. Manual test flow
- Launch app
- Complete Google sign-in
- Sync contacts (mock or real)
- Navigate to message creation screen
- Trigger AI suggestion
- Verify response within 15 seconds
- Verify fallback if network unavailable
```

**Expected Results:**
- ✅ Suggestion appears within 5-10 seconds
- ✅ No crashes or ANR
- ✅ Fallback templates show if Gemini unavailable
- ✅ No PII in logs (verify via logcat)
- ✅ Rate limiting works (test 8+ rapid requests)

**Documentation:**
- [ ] Create RUNTIME_VERIFICATION_REPORT.md with:
  - Device specs (model, Android version)
  - Network conditions
  - Response times (avg, p95, p99)
  - Error cases encountered
  - Log excerpts (sanitized)
  - Screenshots

---

## Phase 1: Core AI Enhancements (P1 - Post-Launch)

### Feature 1: Auto-Generate Multiple Variations

**User Story:** As a user, I want to see 3 tone variations instantly when I open the message screen so I can choose the best fit without multiple requests.

**Implementation:**

#### Backend Changes (Kotlin)

File: `AndroidGeminiSuggestionGateway.kt`

```kotlin
// Current: returns single string
// New: returns list of 3 variations

data class GeminiSuggestionResult(
    val candidates: List<String>,  // Changed from single String
    val provenance: ProvenanceRecord,
    val generatedAt: Long
)

suspend fun generateSuggestions(
    relationship: RelationshipType,
    tone: Tone,
    milestone: Milestone?,
    language: Language
): GeminiSuggestionResult {
    // Single prompt requesting 3 variations
    val prompt = buildPrompt(relationship, tone, milestone, language)
    val response = generativeModel.generateContent(prompt)
    
    // Parse 3 variations from response
    val candidates = parseCandidates(response.text, count = 3)
    
    return GeminiSuggestionResult(
        candidates = candidates,
        provenance = registry.record(...),
        generatedAt = System.currentTimeMillis()
    )
}
```

#### Bridge Changes (TypeScript)

File: `src/infrastructure/native/BirthdayNativeAdapter.ts`

```typescript
// Current interface
export interface NativeBirthdayInterface {
  generateSuggestions(params: {
    relationship: RelationshipType;
    tone: Tone;
    milestone?: Milestone;
  }): Promise<string[]>;  // Already returns array!
}

// Implementation already supports multiple candidates
// Just need to ensure UI displays all 3
```

#### UI Changes

File: `src/features/live/messages/MessageCreationScreen.tsx`

```tsx
// Current: shows single suggestion
// New: shows 3 cards with tone labels

const MessageCreationScreen = () => {
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  
  useEffect(() => {
    // Auto-trigger on screen mount
    setLoading(true);
    try {
      const result = await BirthdayNative.executeUserIntent('generate-suggestions', {
        relationship: selectedRelationship,
        tone: selectedTone,
        milestone: selectedMilestone
      });
      setSuggestions(result.candidates || []); // Expect 3 items
    } finally {
      setLoading(false);
    }
  }, []);
  
  return (
    <View>
      {loading && <LoadingSpinner />}
      {!loading && suggestions.length === 0 && (
        <Text>No suggestions available. Try manual editing.</Text>
      )}
      {!loading && suggestions.map((text, index) => (
        <SuggestionCard
          key={index}
          text={text}
          toneLabel={getToneLabel(index)} // "Warm", "Simple", "Cheerful"
          onSelect={() => selectSuggestion(text)}
        />
      ))}
    </View>
  );
};
```

**Files to Modify:**
1. `android/app/src/main/java/.../gemini/AndroidGeminiSuggestionGateway.kt`
2. `android/app/src/main/java/.../gemini/GeminiPromptBuilder.kt` (if exists)
3. `src/features/live/messages/MessageCreationScreen.tsx`
4. `src/design-system/components/SuggestionCard.tsx` (new component)

**Time Estimate:** 4-6 hours

**Testing:**
- [ ] Unit test: prompt builder generates correct multi-variation request
- [ ] Unit test: parser extracts exactly 3 candidates
- [ ] Integration test: gateway returns 3 distinct variations
- [ ] E2E test: UI displays 3 cards, all selectable
- [ ] Performance test: response time < 10s for 3 variations

**Risk Assessment:** LOW
- Single API call (not 3x cost)
- Backward compatible (UI handles 1-3 items)
- Fallback to templates if parsing fails

---

### Feature 2: AI Status Indicator

**User Story:** As a user, I want to know if AI features are available before I try to use them, so I don't waste time waiting for timeouts.

**Implementation:**

#### Native Gateway Enhancement

File: `AndroidGeminiOperationalGate.kt`

```kotlin
data class AIStatus(
    val state: AIState,  // READY, LIMITED, UNAVAILABLE
    val reason: String?,
    val capabilities: List<AICapability>
)

enum class AIState {
    READY,       // 🟢 Full functionality
    LIMITED,     // 🟡 Degraded (slow network, rate limited)
    UNAVAILABLE  // 🔴 Offline, policy suspended, no API key
}

suspend fun checkAIStatus(): AIStatus {
    val checks = listOf(
        checkApiKeyPresent(),
        checkNetworkConnectivity(),
        checkPolicyNotSuspended(),
        checkRateLimitRemaining(),
        checkGeminiEndpointReachable()
    )
    
    return when {
        checks.all { it.ok } -> AIStatus(AIState.READY, null, [...])
        checks.any { it.critical } -> AIStatus(AIState.UNAVAILABLE, ...)
        else -> AIStatus(AIState.LIMITED, ...)
    }
}
```

#### Bridge Intent

File: `BirthdayNativeAdapter.ts`

```typescript
case 'check-ai-status': {
  const status = await geminiGateway.checkAIStatus();
  return status;
}
```

#### UI Component

File: `src/design-system/components/AIStatusBadge.tsx` (new)

```tsx
export const AIStatusBadge: React.FC = () => {
  const [status, setStatus] = useState<AIStatus | null>(null);
  
  useEffect(() => {
    // Check on app foreground
    const unsubscribe = AppState.addEventListener('change', async (state) => {
      if (state === 'active') {
        const s = await BirthdayNative.executeUserIntent('check-ai-status');
        setStatus(s);
      }
    });
    
    // Initial check
    BirthdayNative.executeUserIntent('check-ai-status').then(setStatus);
    
    return () => unsubscribe.remove();
  }, []);
  
  if (!status) return null;
  
  const config = {
    READY: { icon: '🟢', color: '#22c55e', label: 'AI Ready' },
    LIMITED: { icon: '🟡', color: '#eab308', label: 'AI Limited' },
    UNAVAILABLE: { icon: '🔴', color: '#ef4444', label: 'AI Unavailable' }
  }[status.state];
  
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', padding: 8 }}>
      <Text style={{ fontSize: 12 }}>{config.icon}</Text>
      <Text style={{ fontSize: 12, color: config.color, marginLeft: 4 }}>
        {config.label}
      </Text>
      {status.reason && (
        <Text style={{ fontSize: 10, color: '#666', marginLeft: 8 }}>
          {status.reason}
        </Text>
      )}
    </View>
  );
};
```

**Integration Points:**
- Add to `MessageCreationScreen` header
- Add to `SettingsScreen` AI section
- Add to onboarding flow (if AI-first setup)

**Files to Create/Modify:**
1. `android/app/src/main/java/.../gemini/AndroidGeminiOperationalGate.kt` (add `checkAIStatus()`)
2. `src/design-system/components/AIStatusBadge.tsx` (new)
3. `src/features/live/messages/MessageCreationScreen.tsx` (integrate badge)
4. `src/features/live/settings/SettingsScreen.tsx` (show status in AI section)

**Time Estimate:** 3-4 hours

**Testing:**
- [ ] Unit test: status checks return correct states
- [ ] Integration test: network toggle changes status
- [ ] UI test: badge updates in real-time
- [ ] Accessibility test: screen reader announces status

**Risk Assessment:** LOW
- Read-only operation
- Graceful degradation
- No breaking changes

---

## Phase 2: Advanced AI Features (P2 - Future)

### Feature 3: Smart Contact Enrollment

**User Story:** As a user, I want the app to suggest relationships and tones based on contact names, so I can enroll people faster.

**Flow:**
1. User syncs contacts
2. AI scans names (e.g., "Mom", "Dr. Smith", "Gym Buddy")
3. AI suggests: Relationship="Parent", Tone="Warm" for "Mom"
4. User confirms with one tap

**Implementation Considerations:**
- Privacy: Process names locally, never send to server
- Batch processing: Analyze all contacts in single request
- Confidence scores: Only show suggestions above threshold
- Override: Always allow manual correction

**Time Estimate:** 8-12 hours

**Risk Assessment:** MEDIUM
- Privacy concerns (names are PII)
- Accuracy expectations
- Cultural name variations

---

### Feature 4: Approval Prioritization

**User Story:** As a user with 10 pending birthday messages, I want to know which ones need attention first, so I can focus on what matters.

**Flow:**
1. User opens "Pending Approvals" screen
2. AI analyzes: relationship + draft quality + days until birthday
3. AI flags: "⚠️ Boss message too casual — review recommended"
4. AI sorts: High priority (family, close friends, <3 days) first

**Implementation:**
- Scoring algorithm: urgency × relationship × quality
- Explainable: show why each is prioritized
- Override: allow manual reordering

**Time Estimate:** 6-8 hours

**Risk Assessment:** LOW-MEDIUM
- Subjective prioritization
- User trust in AI sorting

---

## Testing Strategy

### Unit Tests (Jest + JUnit)

```typescript
// TypeScript
describe('AIStatusBadge', () => {
  it('shows green when ready', () => {...});
  it('shows yellow when rate limited', () => {...});
  it('shows red when offline', () => {...});
});
```

```kotlin
// Kotlin
@Test
fun `generateSuggestions returns 3 distinct candidates`() {
  val result = gateway.generateSuggestions(...)
  assertEquals(3, result.candidates.size)
  assertTrue(result.candidates.distinct().size == 3)
}
```

### Integration Tests (Firebase Emulator)

```bash
# Run in CI
npm run test:integration -- --grep "AI"
```

### E2E Tests (Maestro)

File: `.maestro/ai-features.yaml`

```yaml
appId: com.yashsomani.birthdayautopilot
---
- launchApp
- assertVisible: "AI Ready"  # Status badge
- tapOn: "Create Message"
- assertVisible: "Warm"  # Tone variation 1
- assertVisible: "Simple"  # Tone variation 2
- assertVisible: "Cheerful"  # Tone variation 3
- tapOn: "Simple"
- assertVisible: "Message saved"
```

### Real Device Tests

**Matrix:**
| Device Model | Android Version | Network | Expected Result |
|-------------|----------------|---------|-----------------|
| Pixel 7 | 14 | WiFi | ✅ 3 variations < 5s |
| Samsung S21 | 13 | 4G | ✅ 3 variations < 8s |
| OnePlus 9 | 12 | 3G | 🟡 3 variations < 15s |
| Low-end device | 10 | WiFi | 🟡 May show fallback |

---

## Rollout Plan

### Week 1: Foundation
- [ ] Runtime verify current single-tone AI
- [ ] Document results in RUNTIME_VERIFICATION_REPORT.md
- [ ] Fix any issues discovered

### Week 2: Feature 1 (Multiple Variations)
- [ ] Implement Kotlin gateway changes
- [ ] Implement UI components
- [ ] Unit + integration tests
- [ ] QA on 2-3 devices

### Week 3: Feature 2 (Status Indicator)
- [ ] Implement status checks
- [ ] Add badge component
- [ ] Integrate into screens
- [ ] Test network transitions

### Week 4: Stabilization
- [ ] Bug fixes
- [ ] Performance optimization
- [ ] Documentation updates
- [ ] Prepare for release

### Month 2+: Advanced Features
- [ ] Contact enrollment (Feature 3)
- [ ] Approval prioritization (Feature 4)
- [ ] User feedback analysis
- [ ] Iteration based on usage data

---

## Success Metrics

| Metric | Baseline | Target | Measurement |
|--------|----------|--------|-------------|
| AI suggestion acceptance rate | TBD | >60% | Analytics event |
| Time to create message | TBD | <2 min | Session duration |
| User satisfaction (NPS) | TBD | >7 | In-app survey |
| AI availability uptime | N/A | >95% | Status badge logs |
| Response time (p95) | TBD | <10s | Performance monitoring |

---

## Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Gemini API cost overrun | Medium | High | Rate limits, batch requests, caching |
| Poor suggestion quality | Medium | Medium | User feedback loop, template fallback |
| Privacy concerns | Low | High | Keep PII local, audit prompts, document |
| Network dependency | High | Medium | Offline fallbacks, status indicator |
| Battery drain | Low | Medium | Background throttling, efficient polling |

---

## Architectural Decisions

### ✅ DO:
- Keep AI native-only (no backend gateway)
- Process PII locally (never send to Gemini)
- Provide clear fallback states
- Show AI status transparently
- Rate limit aggressively

### ❌ DON'T:
- Re-introduce JavaScript AI layer
- Send contact names/numbers to Gemini
- Block UI while waiting for AI
- Hide AI failures from users
- Build iOS AI before iOS app exists

### ⏸ DEFER:
- Backend AI orchestration
- Cross-device AI preferences
- Preference learning (on-device OK)
- Hinglish code-switching (post-launch)

---

## Appendix: File Inventory

### Files to Create
1. `src/design-system/components/AIStatusBadge.tsx`
2. `src/design-system/components/SuggestionCard.tsx`
3. `RUNTIME_VERIFICATION_REPORT.md` (template)
4. `.maestro/ai-features.yaml`

### Files to Modify
1. `android/app/src/main/java/.../gemini/AndroidGeminiSuggestionGateway.kt`
2. `android/app/src/main/java/.../gemini/AndroidGeminiOperationalGate.kt`
3. `src/infrastructure/native/BirthdayNativeAdapter.ts`
4. `src/features/live/messages/MessageCreationScreen.tsx`
5. `src/features/live/settings/SettingsScreen.tsx`

### Files Already Correct
1. `contracts/gemini-prompt-policy-v2.json` (supports multi-candidate)
2. `src/application/ports/BirthdayNativePort.ts` (already returns array)
3. Test infrastructure (ready for new tests)

---

**Document Created:** 2026-08-29  
**Next Review:** After runtime verification complete  
**Owner:** Engineering Team  
**Stakeholders:** Product, Design, QA
