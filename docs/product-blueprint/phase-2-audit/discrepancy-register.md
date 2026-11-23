# Phase 2 — Documentation and Policy Discrepancy Register

**Reviewed revision:** `ea475e0135a99d5dbae9891340c4a554f2e5fe15`  
**Rule:** A discrepancy remains open until the owner-approved product policy and current source evidence are reconciled. Do not silently rewrite historical documents or delete conflicting records.

| ID | Severity | Conflicting claims | Evidence and conclusion | Required resolution |
|---|---|---|---|---|
| DOC-001 | High | Root `SSOT.md` says BYOK is checked before subscription and may run without Pro. `ai_router.dart` comment says the same. Executable router checks `entitlement.canUseAi` first and throws `lockedAi`; router tests assert free entitlement is denied. | Current code and test behavior agree with the later owner decision that entitlement gates every AI provider. The root SSOT/comment are stale relative to that decision and implementation. | Update the authoritative SSOT and stale comments only after recording the policy decision in the decision register. Keep BYOK as a provider choice after entitlement, not an entitlement bypass. |
| DOC-002 | High | `docs/knowledge/architecture/README.md` says the router checks `hasGeminiApiKey` before applying the Pro gate to non-BYOK. The current executable method applies the entitlement gate before provider routing. | The prose describes an earlier routing model. The method and `test/ai/ai_router_test.dart` provide current implementation evidence. | Correct the architecture narrative and route diagram; link the relevant test assertion. |
| DOC-003 | High | `PROJECT.md` describes the Birthdays table with `version` and `deletedAt` fields and calls the cloud backup an encrypted envelope in its M3 contract. The current Drift `Birthdays` table has neither field; the database comment says only Persons has the full sync envelope. | Direct schema comparison confirms the Birthdays field mismatch. Existing current data-model docs correctly distinguish owner-scoped backup from end-to-end encryption. | Rewrite PROJECT.md as historical planning/status or clearly mark those fields/encryption as deferred/not implemented. Do not infer an encryption guarantee from the old plan. |
| DOC-004 | Medium | `docs/knowledge/feature-registry.json` and knowledge README reference snapshot SHA `75b9d91aadcc1c4136fdcb2c06340d3744bc50a4`, review date 2026-10-14, and 50 feature nodes. Current main is `ea475e0135a99d5dbae9891340c4a554f2e5fe15`. | The registry itself labels its policy as a static source-review sample, not exhaustive runtime verification. Its snapshot is not current. | Refresh registry snapshot and per-feature evidence in a dedicated pass. Do not simply update the SHA without rechecking entries and tests. |
| DOC-005 | Medium | Knowledge README reports an older CI run as the latest observed status. PROJECT.md marks final milestone and 100% verification as DONE. The current run failed. | Current main CI run 45228379958 failed: 459 Flutter passed/1 failed, backend dependency install failed, Hosting passed. | Replace current-status claims with commit-specific links. Keep old reports dated and explicitly historical. |
| DOC-006 | Medium | `ai_router.dart` comments say BYOK is allowed without Pro, while method logic and tests require entitlement. | Comment is internally inconsistent with the executable method. | Correct comments when policy decision is formally reconciled. |
| DOC-007 | Medium | Existing architecture diagrams include mobile app, purchase verifier, control plane and public Hosting as a single system view. Some edges may imply operational integration/deployment. | Source contains these components; static inspection does not prove all edges are wired or deployed. Existing knowledge architecture warns that control plane is separate and deployment is unverified. | Label source presence vs live integration explicitly; verify client callsites, function deployment and IAM before asserting runtime connectivity. |
| DOC-008 | Low | Several documents use `implemented`, `DONE`, `FINAL` or `production-ready` labels with different meanings. | The feature registry defines `implemented` as source present, not runtime proof. Historical reports use stronger status labels. | Standardize status vocabulary: source-present, partially implemented, test-covered, CI-passed, device-verified, deployed, production-observed. |

## Canonical policy conflict: AI entitlement

The source behavior at the reviewed commit is unambiguous:

- The router reads whether a Gemini key exists.
- It then blocks if the app entitlement is not active.
- Only after that check does it choose Nano or the user Gemini provider.
- The router test includes a free-entitlement denial assertion and a Pro-entitlement BYOK route assertion.

The prior product decision carried forward from project history is: **subscription entitlement is required before any AI provider can be used; a personal Gemini API key does not bypass the app entitlement.** This is the policy that should govern the next SSOT revision unless the owner explicitly changes it.

## Historical preservation rule

Do not delete or overwrite `PROJECT.md`, old audit reports, archived specifications or the 2026-10 knowledge snapshot as a cleanup shortcut. Mark them historical, migrate unique facts, retain provenance, and only then decide whether consolidation is appropriate.
