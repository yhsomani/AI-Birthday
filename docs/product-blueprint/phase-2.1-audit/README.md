# Phase 2.1 — Source-to-Test Audit Baseline

**Branch:** `main`  
**Repository HEAD reviewed:** `4380220014d88a6b05f61466043bc7e319c8dbef`  
**Review date:** 2026-11-23  
**Scope:** Documentation only  
**Status:** **IN PROGRESS — evidence baseline created; exhaustive source audit not yet complete**

## Purpose

Build a reliable map from product capabilities to current implementation files, tests, CI execution and outstanding verification. This phase must precede major redesign or roadmap commitments.

## Deliverables

- [Feature implementation matrix](feature-implementation-matrix.md)
- [Feature source-to-test traceability](feature-source-test-traceability.md)
- [Database data dictionary](database-data-dictionary.md)
- [API contract catalog](api-contract-catalog.md)
- [Data lifecycle and sync](data-lifecycle-and-sync.md)
- [Platform integration matrix](platform-integration-matrix.md)
- [Security control evidence](security-control-evidence.md)
- [Test coverage and gaps](test-coverage-and-gaps.md)
- [Documentation migration register](documentation-migration-register.md)

## Evidence rules

1. Source present means only that implementation code exists.
2. A test path means an assertion is mapped; it does not prove every behavior in a feature is covered.
3. A CI pass is tied to the exact workflow run and commit.
4. A skipped test/build is not a pass.
5. A deployed function, ruleset or website requires deployment evidence; source configuration is insufficient.
6. Unread source files and unmapped behavior stay OPEN. Do not infer completion from the legacy registry's `implemented` label.

## Audited baseline

- Registry contains 50 feature nodes and points to source snapshot `75b9d91aadcc1c4136fdcb2c06340d3744bc50a4`, dated 2026-10-14. It is a static sample, not a current exhaustive audit.
- The latest inspected CI run is [45230577466](https://github.com/yhsomani/AI-Birthday/actions/runs/45230577466).
- Flutter job: formatting and analysis passed; 459 tests passed, one failed (Calendar at 320px; 0.667px bottom overflow). Android release APK and manifest checks skipped.
- Backend job: dependency installation failed because package manifest and lockfile are inconsistent; backend audit/lint/build/unit/emulator tests skipped.
- Hosting job: dependency install, typecheck, tests and production build passed. Deployment remains unverified.

## Critical source/document conflict

`lib/features/ai/domain/ai_router.dart` checks entitlement before selecting a provider. `test/ai/ai_router_test.dart` asserts a free entitlement is blocked even when a user Gemini key exists. The router comment and `lib/app/providers.dart` provider comment still describe BYOK as bypassing the app subscription. Current executable behavior and the recorded product decision agree: entitlement is required for all AI routes. Documentation/comments need reconciliation in a later approved documentation change.

## Limitations

This deliverable records the verified baseline and mappings. It does not claim every feature source file, every assertion, every backend API implementation, every native manifest/receiver, or every historical document has been exhaustively inspected. The next pass must close those gaps before Phase 2.1 can be marked complete.
