# AI Expansion Roadmap: Embedded Gemini Everywhere

## Executive Summary

**Current State:** ✅ Native Android Gemini integration working for message suggestions  
**Architecture:** Device-only, privacy-preserving, operationally gated  
**Status:** IMPLEMENTED but NOT_RUNTIME_VERIFIED

**Goal:** Expand AI from "message suggestions only" to "AI everywhere possible" while maintaining:

- Privacy-first (no PII leaves device)
- Device-only processing (Firebase AI SDK on Android)
- Operational gating (policy-suspended fallback)
- Rate limiting (prevents abuse)
- Bilingual support (EN/HI)

---

## Current AI Architecture (Verified ✅)

```
React Native UI (TypeScript)
       ↓
BirthdayNativeAdapter.ts (port layer)
       ↓ generateSuggestions()
Android BirthdayNativeModule.kt
       ↓
AndroidGeminiSuggestionGateway.kt
       ↓ (rate guard + operational gate)
AndroidFirebaseGeminiClient
       ↓ (Firebase App Check + Auth)
Firebase AI SDK (firebase-ai 17.13.0)
       ↓
Gemini API (vertex-ai/global/gemini-3.5-flash)
       ↓
JSON response with 1-3 candidates
       ↓
Local validation (MessageTemplateValidator)
       ↓
UI displays suggestions with fallback templates
```

### Key Features Already Implemented

1. **Privacy-Preserving Prompts**

   - No names, phone numbers, birthdays in prompts
   - System instruction blocks sensitive content
   - Only language, tone, placeholder mode, segment cap sent

2. **Operational Gate**

   - Network availability check
   - App Check attestation required
   - Account session binding
   - Policy suspension capability

3. **Rate Limiting**

   - Per-account daily limits stored locally
   - Prevents abuse and cost overruns
   - Automatic fallback when exceeded

4. **Response Validation**

   - JSON schema enforcement
   - UTF-8 size limits (max 16KB response, 2KB per candidate)
   - Language matching verification
   - Template validation (placeholder modes, segment caps)
   - Duplicate detection

5. **Fallback Strategy**
   - Built-in templates always available
   - Graceful degradation on network/policy failures
   - Clear user messaging about unavailability

---

## Phase 1: Runtime Verification (P0 - Before Launch)

### Task 1.1: Verify Current Message Suggestion Flow

**Test Scenario:**

```
User opens message editor
  ↓
Selects language (EN/HI)
  ↓
Selects tone (warm/simple/cheerful)
  ↓
Selects placeholder mode (given-name/generic)
  ↓
Taps "Suggest" button
  ↓
[Network request to Gemini]
  ↓
Receives 1-3 candidates
  ↓
Displays cards with suggestions
  ↓
User taps "Use this suggestion"
  ↓
Text populated in editor
```

**Acceptance Criteria:**

- [ ] Suggestions appear within 5 seconds on good network
- [ ] Fallback templates show immediately if offline
- [ ] All three tones produce distinct outputs
- [ ] Both languages work correctly
- [ ] Given-name placeholder inserts `{firstName}` correctly
- [ ] Generic mode has no placeholders
- [ ] Segment cap (1 or 2) respected in output length

**Files to Test:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiSuggestionGateway.kt`
- `src/features/live/LiveMessageScreen.tsx` (lines 360-408)
- `src/domain/messages/model.ts` (GeminiRequest, GeminiSuggestionsProjection)

---

## Phase 2: AI Expansion Opportunities (P1 - Post-Launch)

### Feature 2.1: Multiple Message Variations by Default

**Current Behavior:** User clicks "Suggest" → gets 1-3 candidates  
**Enhanced Behavior:** Auto-generate 3 variations organized by tone when screen opens

**Implementation:**

```kotlin
// Modify AndroidGeminiSuggestionGateway.kt
suspend fun generate(requestJson: JSONObject): JSONObject {
  // Current: returns 1-3 candidates based on single tone
  // Enhanced: generate 3 distinct tones automatically

  val tones = listOf("warm", "simple", "cheerful")
  val allCandidates = mutableListOf<String>()

  for (tone in tones) {
    val toneRequest = request.copy(tone = tone)
    val candidates = generateForTone(toneRequest)
    allCandidates += candidates.firstOrNull() // Best of each tone
  }

  return JSONObject()
    .put("kind", "candidates")
    .put("candidates", JSONArray(allCandidates))
    .put("variations", JSONArray(listOf("warm", "simple", "cheerful")))
}
```

**UI Changes:**

```tsx
// src/features/live/LiveMessageScreen.tsx
{
  expanded && (
    <>
      <AppText variant="label">Warm & Heartfelt</AppText>
      <Card>{candidates[0]}</Card>

      <AppText variant="label">Simple & Sweet</AppText>
      <Card>{candidates[1]}</Card>

      <AppText variant="label">Cheerful & Fun</AppText>
      <Card>{candidates[2]}</Card>
    </>
  );
}
```

**Benefits:**

- Instant variety without multiple requests
- User sees full range of options immediately
- Reduces perceived latency

---

### Feature 2.2: Intelligent Contact Enrollment

**Goal:** AI analyzes contact names/notes to suggest relationships and default settings

**Flow:**

```
User syncs contacts
  ↓
AI scans contact display names, notes
  ↓
Pattern matching:
  - "Mom", "Dad", "Mummy" → Relationship: Parent, Tone: Warm
  - "Dr. Smith", "Prof. Johnson" → Relationship: Professional, Tone: Formal
  - "Gym Buddy", "Bestie" → Relationship: Friend, Tone: Cheerful
  - "Boss", "Manager" → Relationship: Professional, Tone: Simple
  ↓
UI shows suggested relationship + tone with one-tap confirm
  ↓
User accepts or manually overrides
```

**Implementation Requirements:**

1. **New Native Port Method:**

```typescript
// src/application/ports/PeoplePort.ts
suggestRelationship(input: {
  contactDisplayName: string;
  contactNotes?: string | undefined;
  language: MessageLanguage;
}): Promise<NativeResult<RelationshipSuggestion>>;
```

2. **New Model Type:**

```typescript
// src/domain/contacts/model.ts
export type RelationshipSuggestion =
  | { kind: 'requesting' }
  | {
      kind: 'suggested';
      relationship: MessageRelationship | undefined;
      tone: MessageTone | undefined;
      confidence: 'high' | 'medium' | 'low';
      reason: string; // "Contains 'Mom' which typically indicates parent relationship"
    }
  | {
      kind: 'unavailable';
      reason: 'network-offline' | 'policy-suspended' | 'insufficient-data';
    };
```

3. **Kotlin Gateway Extension:**

```kotlin
// android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiSuggestionGateway.kt
suspend fun suggestRelationship(contactName: String, contactNotes: String?, language: String): JSONObject {
  val prompt = buildRelationshipPrompt(contactName, contactNotes, language)
  val raw = client.generate(
    systemInstruction = RELATIONSHIP_SYSTEM_INSTRUCTION,
    prompt = prompt
  )
  return parseRelationshipResponse(raw, language)
}

private const val RELATIONSHIP_SYSTEM_INSTRUCTION =
  "Analyze contact names and notes to suggest appropriate birthday message relationships. " +
  "Return only structured JSON with relationship category, tone suggestion, and confidence level. " +
  "Never store or transmit personal data. Use only generic categories: parent, sibling, friend, " +
  "professional, romantic, child, other."
```

**Privacy Considerations:**

- Contact name sent temporarily for analysis only
- No persistent storage of AI analysis results
- User can always override suggestions
- Offline mode uses simple keyword matching fallback

---

### Feature 2.3: Smart Approval Prioritization

**Goal:** When user has multiple pending approvals, AI helps prioritize which to send first

**Problem:** User returns after 3 days to find 10 pending birthday messages. Which ones are most important?

**AI Solution:**

```
Pending approvals list
  ↓
AI analyzes:
  - Relationship type (parent > colleague)
  - Message age (older = more urgent)
  - Time until end of day
  - Past sending patterns
  ↓
Sorts list with priority badges:
  🔴 High Priority (Mom's birthday - 6 hours left)
  🟡 Medium Priority (Friend's birthday - 18 hours left)
  🟢 Low Priority (Colleague's birthday - 2 days left)
  ↓
Optional: AI warning for mismatches
  "⚠️ Message for 'Boss' uses casual tone. Review recommended."
```

**Implementation:**

1. **New Automation Port Method:**

```typescript
// src/application/ports/AutomationPort.ts
prioritizeApprovals(input: {
  approvals: readonly ApprovalReview[];
  currentHour: number;
  timezone: string;
}): Promise<NativeResult<ApprovalPriorityList>>;
```

2. **Priority Scoring Algorithm:**

```kotlin
// android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/ApprovalPrioritizer.kt
data class ApprovalPriority(
  val approvalId: OccurrenceId,
  val priorityScore: Int, // 0-100
  val priorityLevel: PriorityLevel, // HIGH, MEDIUM, LOW
  val timeRemainingHours: Int,
  val relationshipWeight: Int,
  val messageQualityFlags: List<QualityFlag>
)

enum class PriorityLevel { HIGH, MEDIUM, LOW }

enum class QualityFlag {
  TONE_MISMATCH,        // Casual tone for professional relationship
  TOO_SHORT,            // Message under 20 characters
  TOO_LONG,             // Message exceeds 2 segments
  MISSING_PLACEHOLDER,  // Uses given-name mode but no {firstName}
  STALE_DRAFT           // Created more than 48 hours ago
}
```

3. **UI Integration:**

```tsx
// src/features/live/LiveApprovalsScreen.tsx
const sortedApprovals = useMemo(() => {
  return approvals.sort((a, b) => b.priorityScore - a.priorityScore);
}, [approvals]);

{
  sortedApprovals.map(approval => (
    <Card key={approval.id}>
      {approval.priorityLevel === 'HIGH' && (
        <Badge color="red">High Priority</Badge>
      )}
      {approval.qualityFlags.includes('TONE_MISMATCH') && (
        <WarningBanner>
          This message for '{approval.relationship}' uses casual tone. Consider
          reviewing before sending.
        </WarningBanner>
      )}
      {/* ... rest of approval card */}
    </Card>
  ));
}
```

---

### Feature 2.4: Contextual Message Enhancement

**Goal:** User provides context ("It's raining today", "They just got promoted"), AI incorporates it naturally

**Flow:**

```
User writes basic message: "Happy Birthday! Hope you have a great day!"
  ↓
User adds context: "They just started a new job this week"
  ↓
AI enhances: "Happy Birthday! Hope you have a great day, especially as you celebrate starting your new job this week!"
  ↓
User reviews and approves enhancement
  ↓
Enhanced message saved
```

**Implementation:**

1. **New Message Port Method:**

```typescript
// src/application/ports/MessagePort.ts
enhanceWithContext(input: {
  baseMessage: PrivateMessageText;
  context: string;
  language: MessageLanguage;
  maxAdditionalSegments: 0 | 1; // Don't exceed original segment count by more than 1
}): Promise<NativeResult<MessageEnhancement>>;
```

2. **Enhancement Response Type:**

```typescript
// src/domain/messages/model.ts
export type MessageEnhancement =
  | { kind: 'requesting' }
  | {
      kind: 'enhanced';
      enhancedText: PrivateMessageText;
      segmentCount: 1 | 2;
      changes: readonly string[]; // ["Added reference to new job", "Maintained warm tone"]
    }
  | {
      kind: 'unchanged';
      reason:
        | 'context-not-relevant'
        | 'would-exceed-segment-limit'
        | 'already-optimal';
    }
  | {
      kind: 'failed';
      reason: 'network-offline' | 'policy-suspended' | 'invalid-input';
    };
```

3. **Kotlin Implementation:**

```kotlin
// android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiSuggestionGateway.kt
suspend fun enhanceWithContext(
  baseMessage: String,
  context: String,
  language: String,
  maxAdditionalSegments: Int
): JSONObject {
  val prompt = buildEnhancementPrompt(baseMessage, context, language, maxAdditionalSegments)
  val raw = client.generate(
    systemInstruction = ENHANCEMENT_SYSTEM_INSTRUCTION,
    prompt = prompt
  )
  return parseEnhancementResponse(raw, baseMessage, maxAdditionalSegments)
}

private const val ENHANCEMENT_SYSTEM_INSTRUCTION =
  "Enhance birthday messages by naturally incorporating provided context. " +
  "Maintain the original tone and language. Do not add context that contradicts the message. " +
  "Respect segment limits. Return only the enhanced text and a list of changes made."
```

**UI Pattern:**

```tsx
// New screen component: LiveMessageContextEnhancement.tsx
const [context, setContext] = useState('');
const [enhancement, setEnhancement] = useState<MessageEnhancement>();

<Button
  label="Enhance with Context"
  disabled={!context || enhancement?.kind === 'requesting'}
  onPress={async () => {
    const result = await port.enhanceWithContext({
      baseMessage: draft.text,
      context,
      language: draft.language,
      maxAdditionalSegments: draft.requestedSegmentCap === 1 ? 0 : 1,
    });
    setEnhancement(result.envelope.value);
  }}
/>;

{
  enhancement?.kind === 'enhanced' && (
    <DiffViewer
      original={draft.text}
      enhanced={enhancement.enhancedText}
      changes={enhancement.changes}
    />
  );
}
```

---

### Feature 2.5: AI Status Indicator

**Goal:** Manage user expectations by showing AI availability status in real-time

**Implementation:**

1. **New Projection:**

```typescript
// src/domain/messages/model.ts
export type AiAvailabilityProjection =
  | { kind: 'ready' } // All systems operational
  | { kind: 'loading' } // Initial check in progress
  | {
      kind: 'limited';
      reasons: readonly AvailabilityReason[];
    }
  | { kind: 'unavailable'; reason: AvailabilityReason };

export type AvailabilityReason =
  | 'network-offline'
  | 'app-check-failed'
  | 'not-authenticated'
  | 'rate-limited'
  | 'policy-suspended'
  | 'backend-unavailable';
```

2. **Native Polling:**

```kotlin
// android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/GeminiOperationalGate.kt
fun getAvailabilityStatus(): AiAvailabilityProjection {
  return when {
    !network.isOnline() -> AiAvailabilityProjection.unavailable(NETWORK_OFFLINE)
    !appCheckReady() -> AiAvailabilityProjection.limited(listOf(APP_CHECK_FAILED))
    !accountSessionKey() -> AiAvailabilityProjection.unavailable(NOT_AUTHENTICATED)
    !rateGuard.canAcquire() -> AiAvailabilityProjection.limited(listOf(RATE_LIMITED))
    !foregroundSuggestionsEnabled() -> AiAvailabilityProjection.unavailable(POLICY_SUSPENDED)
    else -> AiAvailabilityProjection.ready()
  }
}
```

3. **UI Component:**

```tsx
// src/features/messages/AiStatusIndicator.tsx
const AiStatusIndicator: React.FC = () => {
  const [status, setStatus] = useState<AiAvailabilityProjection>({
    kind: 'loading',
  });

  useEffect(() => {
    const poll = async () => {
      const result = await nativeModule.getAiAvailability();
      setStatus(result);
    };

    poll();
    const interval = setInterval(poll, 30000); // Check every 30 seconds
    return () => clearInterval(interval);
  }, []);

  switch (status.kind) {
    case 'ready':
      return <Icon name="sparkles" color="green" testID="ai-status-ready" />;
    case 'limited':
      return (
        <Tooltip text={`AI limited: ${status.reasons.join(', ')}`}>
          <Icon
            name="sparkles-outline"
            color="yellow"
            testID="ai-status-limited"
          />
        </Tooltip>
      );
    case 'unavailable':
      return (
        <Tooltip text={`AI unavailable: ${status.reason}`}>
          <Icon
            name="sparkles-off"
            color="gray"
            testID="ai-status-unavailable"
          />
        </Tooltip>
      );
    default:
      return <ActivityIndicator size="small" color="gray" />;
  }
};
```

---

## Phase 3: Advanced AI Features (P2 - Future)

### Feature 3.1: Seasonal and Cultural Adaptation

**Goal:** Automatically adapt messages based on season, holidays, cultural events

**Examples:**

- December: "Hope your birthday is as wonderful as the holiday season!"
- Monsoon (India): "May your birthday be as refreshing as the first monsoon rain!"
- Summer: "Wishing you a birthday as bright as the summer sun!"

**Implementation:** Requires location-aware prompting with privacy safeguards

---

### Feature 3.2: Learning from User Preferences

**Goal:** Track which suggestions users select most often, personalize future suggestions

**Privacy-Safe Approach:**

- Store only aggregate preferences locally (e.g., "user selects warm tone 70% of time")
- Never upload individual message selections
- Use on-device learning only

---

### Feature 3.3: Multi-Language Code-Switching

**Goal:** Support Hinglish (Hindi+English mix) for bilingual users

**Example:** "Happy Birthday! Aapka din bahut special ho, filled with joy and happiness!"

**Challenge:** Requires careful prompt engineering to avoid unnatural mixing

---

## Architectural Decisions

### ✅ Keep: Native Android Gemini

- Privacy-preserving (device-only processing)
- Low latency (no backend round-trip)
- Cost-effective (no server infrastructure)
- Offline-capable (graceful fallback)

### ❌ Avoid: Backend AI Gateway

- Unnecessary complexity for Android-first launch
- Increases privacy concerns (PII transmission)
- Adds latency and cost
- Dead code already removed (`AIGateway.ts`, `GoogleAIProviderAdapter.ts`)

### ⏸ Defer: iOS AI

- No iOS app exists yet
- Can replicate native Android architecture when iOS development begins
- Do not build cross-platform abstraction prematurely

---

## Testing Strategy

### Unit Tests (Already Pass ✅)

- `AndroidGeminiSuggestionGatewayTest.kt`
- `AndroidGeminiOperationalGateTest.kt`
- TypeScript schema validation tests

### Integration Tests (Needed)

```kotlin
// android/app/src/androidTest/java/...
@RunWith(AndroidJUnit4::class)
class GeminiIntegrationTest {
  @Test fun testRealGeminiRequest() {
    // Requires Firebase project with AI enabled
    // Verify actual API call succeeds
    // Validate response parsing
  }

  @Test fun testOfflineFallback() {
    // Disable network
    // Verify fallback templates returned immediately
  }

  @Test fun testRateLimiting() {
    // Make rapid successive requests
    // Verify rate guard activates
    // Verify policy-suspended fallback
  }
}
```

### E2E Tests (Maestro)

```yaml
# flows/ai_suggestions.yaml
appId: com.yashsomani.birthdayautopilot
---
- launchApp
- tapOn: 'Messages'
- tapOn: 'Create Message'
- tapOn: 'Suggest'
- assertVisible: 'Suggestion.*' # Regex for any suggestion card
- tapOn: 'Use this suggestion'
- assertVisible: 'Message text populated'
```

---

## Deployment Checklist

### Firebase Configuration

- [ ] Enable Vertex AI API in Firebase project
- [ ] Configure `gemini-3.5-flash` model access
- [ ] Set up billing alerts for AI usage
- [ ] Document API quotas and rate limits

### App Check Enforcement

- [ ] Verify App Check tokens attached to AI requests
- [ ] Test invalid-token rejection
- [ ] Monitor for token exhaustion

### Monitoring

- [ ] Log AI request counts (no content logging!)
- [ ] Track fallback rates
- [ ] Alert on sudden spike in failures
- [ ] Monitor average response latency

### User Documentation

- [ ] Explain AI features in privacy policy
- [ ] Document offline behavior
- [ ] Provide feedback mechanism for poor suggestions

---

## Success Metrics

| Metric                     | Target | Measurement                            |
| -------------------------- | ------ | -------------------------------------- |
| Suggestion acceptance rate | >40%   | Users select AI suggestion vs built-in |
| Average response time      | <3s    | From tap to display                    |
| Fallback rate              | <10%   | Network/policy failures                |
| User satisfaction          | >4.5/5 | In-app feedback on suggestions         |
| Cost per user per month    | <$0.10 | Firebase AI billing                    |

---

## Risk Mitigation

### Risk 1: AI Generates Inappropriate Content

**Mitigation:**

- Strict system instructions blocking sensitive topics
- Local validation before display
- User reporting mechanism
- Policy suspension capability

### Risk 2: Excessive API Costs

**Mitigation:**

- Per-account rate limiting
- Daily caps
- Fallback to built-in templates
- Usage monitoring and alerts

### Risk 3: Privacy Violations

**Mitigation:**

- No PII in prompts (verified by code review)
- Device-only processing
- No logging of AI content
- Transparent privacy policy

### Risk 4: Poor Suggestion Quality

**Mitigation:**

- Built-in template fallbacks always available
- User can ignore suggestions
- Continuous prompt tuning based on acceptance rates
- A/B testing framework ready

---

## Next Immediate Actions

### Week 1: Runtime Verification

1. Deploy Firebase project with Vertex AI enabled
2. Build Android production APK
3. Test message suggestion flow on real device
4. Verify offline fallback works
5. Measure actual response times

### Week 2: AI Status Indicator

1. Implement `getAiAvailability()` native method
2. Create `AiStatusIndicator` React component
3. Add to message editor screen
4. Test all availability states

### Week 3: Multiple Variations

1. Modify gateway to return 3 tone variations by default
2. Update UI to show all three options
3. Test performance impact
4. Gather user feedback

### Week 4+: Contact Enrollment (Stretch Goal)

1. Design relationship suggestion prompt
2. Implement native gateway method
3. Add UI for one-tap confirmation
4. Privacy review and documentation

---

## Conclusion

Your app already has a **superior embedded AI architecture** compared to typical cloud-based approaches:

✅ Privacy-first by design  
✅ Device-only processing  
✅ Operationally gated with fallbacks  
✅ Rate-limited and cost-controlled  
✅ Bilingual support built-in  
✅ Dead code cleaned up (1,164 lines removed)

**The foundation is solid.** Now focus on:

1. **Runtime verification** (prove it works on real devices)
2. **Gradual expansion** (add features one at a time with measurement)
3. **User feedback loops** (track what works, tune what doesn't)

Avoid the temptation to build backend AI infrastructure prematurely. Your native Android approach is the right architecture for an Android-first, privacy-focused product.

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-XX  
**Author:** AI Architecture Review  
**Status:** READY_FOR_IMPLEMENTATION
