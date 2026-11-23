# Phase 2 — Evidence-Based Repository Audit

**Reviewed branch:** `main`  
**Reviewed commit:** `ea475e0135a99d5dbae9891340c4a554f2e5fe15`  
**Review date:** 2026-11-23  
**Audit mode:** Parallel workstreams with a central evidence review  
**Change boundary:** Documentation only

## Purpose

Establish what the repository contains, what can be supported by source, what tests and CI have actually demonstrated, and where existing product documents conflict with implementation or each other.

This is an **initial evidence-backed audit snapshot**, not a completed line-by-line source audit. The repository is larger than can be truthfully certified from this pass. Findings are classified so future work does not turn source presence into a false claim of production readiness.

## Audit workstreams

| Workstream | Inspected evidence | Current outcome |
|---|---|---|
| Flutter product and local data | App entry, providers, router, Drift schema/repositories, AI, subscription, sync, reminders, jobs, delivery and feature test inventory | Architecture and module inventory established; exhaustive per-feature source trace remains open |
| Android/native integration | MainActivity, repository tree, Android source and manifest-related inventory | Native channels and Android responsibilities identified; device behavior not verified |
| Firebase/backend/security | Functions entry point, domain/persistence/service/schema files, package manifests, Firestore rules and backend tests | Purchase verification, control-plane surface and owner-scoped rules identified; backend CI is blocked before tests |
| Hosting and release pipeline | Firebase config, hosting package, workflow and latest run | Hosting job passed in latest run; deployment state not verified |
| Documentation consistency | Root README/SSOT/PROJECT/ARCHITECTURE, knowledge portal, registry, audit/archive docs and product blueprint | Material policy and freshness discrepancies recorded |

The workstreams were reviewed concurrently through separate source groups, then reconciled centrally. This does not imply that external specialist agents or a local build environment were available.

## Evidence hierarchy

1. Current source at the reviewed commit.
2. Tests and their actual assertions.
3. CI job/step outcomes and logs for the exact commit.
4. Current product decisions explicitly recorded by the owner.
5. Existing documentation, comments and historical audit reports.

When these disagree, the discrepancy is recorded; historical labels such as `DONE`, `FINAL` or `implemented` are not treated as proof.

## Documents in this audit

- [Repository inventory](repository-inventory.md) — measured repository topology and source/test areas.
- [CI verification](ci-verification.md) — exact workflow run, pass/fail results and observed blockers.
- [Implementation evidence map](implementation-evidence.md) — source-backed module, data and backend topology.
- [Documentation discrepancy register](discrepancy-register.md) — contradictions and stale claims requiring reconciliation.
- [Coverage and next work](coverage-and-next-work.md) — what is verified, what remains open and recommended audit sequence.

## What this audit does not claim

- It does not claim all 425 files were read line by line.
- It does not claim the full 459 passing Flutter tests were independently run in this session; the count is from GitHub Actions logs.
- It does not claim backend tests ran; the backend job stopped at dependency installation.
- It does not claim Android APK builds ran in the latest workflow; those steps were skipped after the Flutter test failure.
- It does not prove Play Billing, Gemini API, AICore, notifications, SMS, WhatsApp, Firebase deployment or Hosting production behavior on a live device/account.
- It does not authorize application-code changes.

## Current audit verdict

**PARTIAL.** Repository topology, critical CI failures, core architectural boundaries and several high-impact documentation conflicts are verified. Exhaustive feature contracts, full API/database lineage, complete historical-document migration and runtime/deployment verification remain open.
