# WishWell AI Architecture Summary

## Current State (2026-08-29)

### ✅ IMPLEMENTED — Native Android Gemini Integration

**Architecture:** Device-only AI via native Android bridge

```
React Native UI
       ↓
BirthdayNativeAdapter.ts (TypeScript port)
       ↓
AndroidGeminiSuggestionGateway.kt (Native Kotlin)
       ↓
Firebase AI SDK (firebase-ai 17.13.0)
       ↓
Gemini API (vertex-ai/global)
```

### Key Features

1. **Privacy-Preserving**
   - Prompts exclude PII (no contact names, phone numbers, birthdays)
   - Provider text never logged or persisted
   - Generation unreachable from send workers (operational gate)

2. **Safety Mechanisms**
   - Rate limiting: max 8 retained rate scopes
   - Timeout: 15 seconds per request
   - Operational gate with policy-suspended fallback
   - Deterministic local validation before returning results

3. **Fallback States**
   - Template-based suggestions when Gemini unavailable
   - Policy-suspended mode with clear user messaging
   - Network failure handling
   - Account session validation

4. **Capabilities**
   - Tones: warm, simple, cheerful
   - Relationships: friend, family, colleague, partner, casual
   - Milestones: new-job, graduation, moved, new-baby, milestone-age
   - 1-3 candidate suggestions per request
   - Bilingual support (EN/HI)

### Files in Production Implementation

**Native Android:**
- `android/app/src/main/java/.../gemini/AndroidGeminiSuggestionGateway.kt` — main gateway
- `android/app/src/main/java/.../gemini/AndroidGeminiOperationalGate.kt` — operational control
- `android/app/src/main/java/.../gemini/GeminiCandidateProvenanceRegistry.kt` — provenance tracking
- `android/app/src/test/java/.../gemini/` — unit tests

**Contracts:**
- `contracts/gemini-prompt-policy-v2.json` — prompt governance

**Bridge:**
- `src/infrastructure/native/BirthdayNativeAdapter.ts` — React Native port

### ❌ REMOVED — Dead JavaScript AI Code (2026-08-29)

Deleted 1,164 lines of unused code:
- `src/infrastructure/ai/AIGateway.ts` (745 lines)
- `src/infrastructure/ai/providers/GoogleAIProviderAdapter.ts` (419 lines)
- `src/application/ports/AIProviderPort.ts`

**Reason:** Superseded by superior native implementation. Zero external dependencies.

### Verification Status

| Component | Implementation | Unit Tests | Runtime Verified | Deployed |
|-----------|----------------|------------|------------------|----------|
| Native Gateway | ✅ | ✅ | ⚠️ NO | 🚫 NO |
| Operational Gate | ✅ | ✅ | ⚠️ NO | 🚫 NO |
| Provenance Registry | ✅ | ✅ | ⚠️ NO | 🚫 NO |
| Firebase AI SDK | ✅ | N/A | ⚠️ NO | 🚫 NO |
| Contract Validation | ✅ | ✅ | ⚠️ NO | 🚫 NO |

**Overall Status:** ✅ IMPLEMENTED, ⚠️ NOT_RUNTIME_VERIFIED, 🚫 NOT_DEPLOYED

---

## Next Steps for AI Features

### P0 — Runtime Verification (Required Before Launch)

1. **Test on Real Android Device**
   ```bash
   # Verify Gemini API key configuration
   # Test suggestion generation with real Gemini API
   # Validate fallback behavior when API unavailable
   # Confirm operational gate responses
   ```

2. **Test Scenarios**
   - Normal operation with valid API key
   - Network failure → template fallback
   - API quota exceeded → graceful degradation
   - Policy suspended → appropriate messaging
   - Rate limit hit → UX guidance
   - Invalid input → safe error handling

### P1 — AI Feature Expansion (Post-Verification)

Once native Gemini is runtime-verified, consider these expansions:

#### 1. Smart Contact Enrollment
- AI-powered birthday message readiness scoring
- Suggest which contacts to enroll based on relationship strength
- Identify missing phone numbers or ambiguous contacts

#### 2. Approval Prioritization
- Rank pending approvals by importance/urgency
- Suggest batch approval groupings
- Flag potentially problematic messages

#### 3. Relationship Insights
- Analyze message tone history per contact
- Suggest tone adjustments based on past interactions
- Track communication patterns over time

#### 4. Template Optimization
- A/B test template effectiveness (with privacy preservation)
- Suggest template improvements based on user edits
- Auto-generate personalized templates from user writing style

#### 5. Contextual Suggestions
- Time-of-day optimization
- Day-of-week patterns
- Seasonal/holiday variations

### P2 — Backend AI (Future Consideration)

**Only implement if:**
- Cross-platform AI consistency required
- Server-side policy enforcement needed
- Centralized auditing/compliance mandated
- Cost optimization at scale justified

**Architecture would be:**
```
React Native UI
       ↓
Cloud Function (authorize + rate limit)
       ↓
Gemini API (server-side)
       ↓
Response validation
       ↓
Client display
```

---

## Architectural Decisions

### ✅ Keep Native Android Gemini for Launch
- Privacy-preserving (device-only processing)
- Lower latency (no server round-trip)
- Reduced infrastructure complexity
- Already implemented and tested (unit level)

### ❌ Do NOT Build Backend AI Yet
- No current product requirement
- Adds infrastructure complexity
- Increases cost without clear benefit
- Delays launch verification

### ❌ Remove Dead JavaScript Code
-已完成 (2026-08-29)
- Prevents future confusion
- Reduces maintenance burden
- Clarifies architectural intent

### ⏸ Defer iOS AI Until iOS Implementation
- No iOS app exists yet
- Native Android approach proven
- Can replicate pattern for iOS later

---

## Evidence Chain

```
Implementation Proof:
  android/app/src/main/java/.../AndroidGeminiSuggestionGateway.kt ✅
  android/app/src/main/java/.../AndroidGeminiOperationalGate.kt ✅
  android/app/src/main/java/.../GeminiCandidateProvenanceRegistry.kt ✅
  contracts/gemini-prompt-policy-v2.json ✅
  src/infrastructure/native/BirthdayNativeAdapter.ts ✅

Unit Test Proof:
  android/app/src/test/java/.../AndroidGeminiSuggestionGatewayTest.kt ✅
  android/app/src/test/java/.../AndroidGeminiOperationalGateTest.kt ✅

Dead Code Removal Proof:
  git diff showing deletion of AIGateway.ts ✅
  git diff showing deletion of GoogleAIProviderAdapter.ts ✅
  git diff showing deletion of AIProviderPort.ts ✅
  grep confirming zero references ✅

Runtime Verification:
  ⚠️ PENDING — requires real Android device with Gemini API enabled

Deployment Proof:
  🚫 PENDING — requires Firebase project deployment
```

---

## Recommendations

### Immediate (Before Launch)
1. ✅ Complete dead code removal (DONE 2026-08-29)
2. ✅ Update SSOT.md with accurate AI status (DONE 2026-08-29)
3. ⏳ Runtime verify native Gemini on real device
4. ⏳ Deploy Firebase project with AI configuration
5. ⏳ Test full AI flow: request → generation → validation → display

### Post-Launch (Phase 2+)
1. Gather user feedback on AI suggestion quality
2. Monitor Gemini API usage and costs
3. Evaluate expansion opportunities based on actual usage
4. Consider backend AI only if clear product need emerges

### Never (Unless Requirements Change)
1. Re-introduce JavaScript AI layer
2. Build redundant AI systems without clear justification
3. Implement iOS AI before iOS app exists

---

**Document Created:** 2026-08-29  
**Status:** AI architecture clarified, dead code removed, SSOT updated  
**Next Action:** Runtime verification on real Android device
