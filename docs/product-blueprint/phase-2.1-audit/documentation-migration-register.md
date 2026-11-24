# Documentation Migration Register

## Governing approach

Preserve historical documents and their provenance. Do not delete or silently rewrite legacy documents during the audit. Migrate verified current facts into the product blueprint, then mark outdated sources as historical or superseded with links.

| Document/source | Known issue | Action | Status |
|---|---|---|---|
| `SSOT.md` | Describes product authority but current AI comments contradict the executable entitlement rule | Record entitlement decision; reconcile comments/docs only after reviewing full SSOT sections and owner approval | OPEN |
| `lib/features/ai/domain/ai_router.dart` comments | Comment says BYOK permits AI without Pro; implementation checks entitlement first | Correct comment in a separately authorized source change; no source edits in this phase | CONFIRMED DISCREPANCY |
| `lib/app/providers.dart` | Provider comment says personal key unlocks AI without subscription | Correct comment after owner confirms current policy; source change outside docs-only audit | CONFIRMED DISCREPANCY |
| `PROJECT.md` | Historical feature/milestone claims need mapping to current source and test | Preserve; migrate only reverified facts | OPEN |
| `docs/knowledge/feature-registry.json` | 50 nodes, review date 2026-10-14, snapshot SHA `75b9d91aadcc1c4136fdcb2c06340d3744bc50a4`; source-review sample only | Refresh after every node and test assertion is reverified | STALE BASELINE |
| `docs/product-blueprint/` | Phase 1 discovery baseline, Phase 2 repository audit | Keep as structured current product/audit reference; update index with Phase 2.1 links | IN PROGRESS |
| `backend/functions/README and historical audit reports` | Need complete docs tree review and provenance classification | Inventory all 89 docs and root docs; do not delete | OPEN |
| Existing architecture and security docs | Some claims about encryption, tombstones and runtime status conflict with source | Reconcile per evidence and deployment proof | OPEN |

## Verified discrepancy details

- AI routing: source and tests require entitlement before using either user Gemini API or Nano. Provider comments still state BYOK bypasses the subscription. The legacy SSOT and code comments need a consistent policy record.
- Local data: only Persons has both version and deletedAt. Do not apply a uniform sync-envelope claim to all tables.
- Cloud backup: Firestore rules show owner-scoped access. They do not establish end-to-end encryption.
- Status labels: “implemented” in the legacy feature registry means source present, not runtime proof.

## Migration acceptance criteria

- Every document has an owner/domain, source date, current/historical/superseded status and links to migrated facts.
- Unique decisions are preserved with provenance.
- No content is removed until its replacement is checked.
- All internal links, anchors, Mermaid and JSON validate.
- Registry snapshot SHA is updated only after source and test review, never merely because docs changed.


## Phase 2.2 consistency migration

| Document/source | Discrepancy | Action in revision 0.1.4 | Remaining work |
|---|---|---|---|
| Root SSOT.md, §5 and §11 | Described BYOK as an entitlement bypass | Corrected to require active application entitlement before any AI provider call | Keep synchronized with approved product policy |
| Root ARCHITECTURE.md, §8 | Listed BYOK before entitlement check | Corrected provider-routing order and all-provider entitlement requirement | Stale comments in lib/features/ai/domain/ai_router.dart still need a separately authorized source change |
| Phase 2.1 CI reports | Presented failed run 45230577466 as latest | Retained as historical evidence and linked the green current run 45245907678 | Refresh after each relevant main-branch CI result |
| API and platform verification notes | Claimed backend tests/Android build steps did not run | Added current backend and Android CI pass evidence | Real device, live provider and deployment verification remain open |
