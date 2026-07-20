# Plan

## Overview

Use a stage-gated, Codex-only audit-to-delivery workflow that preserves the repository's native safety boundaries and converts findings into independently verifiable implementation tasks. Separate code completeness from physical-device, cloud, store, legal, carrier, and signed-artifact qualification.

## Scope

- Bootstrap repository identity, instruction hierarchy, dirty-tree ownership, product source of truth, manifests, architecture, tests, and release gates.
- Answer optional business/user intake from repository evidence with confidence and assumptions.
- Build requirement-to-code-to-test traceability across React Native, TypeScript domain/application, TurboModules, Kotlin, Swift, protected storage, Firebase Functions/Firestore/Hosting, and release tooling.
- Lock one judged plan before implementation.
- Run a baseline without erasing existing failures, then implement one atomic behavior at a time.
- Exercise real native projection, background mutation, process death, offline recovery, concurrency, permission denial, duplicate prevention, and iOS foreground composer semantics.

## Priority Evidence Leads

- Foreground Android projections may remain stale when WorkManager, SMS callbacks, or People sync mutate Room without publishing the bridge invalidation observed by useLiveProjection.
- One Android bridge executor also runs blocking network operations, which can queue projections behind slow work.
- Very large native coordinator files concentrate maintenance risk and require characterization/property tests before bounded decomposition.
- Rejected bridge calls collapse to a generic unavailable error, obscuring timeout, cancellation, serialization, and linkage faults.
- Fixture E2E does not prove native coordination, persistence, permissions, background execution, or SMS.
- iOS bridge work starts on the main queue and needs measured large-contact/projection performance.

## Atomic Task Rules

Each task contains one invariant or user-visible outcome, exact owned files, preconditions, dependencies, risk, a reproducer or failing test where feasible, minimal steps, deterministic acceptance, exact validation, rollback, and recorded evidence. Split unrelated platforms, independent state transitions, and broad refactors. Stop propagation when validation fails.

## Anti-Shortcuts

Do not replace real behavior with mocks, fixtures, TODOs, stubs, or documentation claims. Do not weaken schemas, permission/App Check/encryption/idempotency/release gates. Keep Android send/claim/Arm/retry authority native. Never automate iOS SMS, treat MessageUI Sent as delivery, or blindly retry unknown SMS outcomes. Never upload private material, add exact-alarm/default-SMS permissions as convenience fixes, or claim production readiness without genuine external evidence.

## Completion

All in-repository requirements are implemented and traced to passing evidence, critical/high defects are closed, real native/background paths are tested, privacy/security scans pass, and unsupported claims are absent. External credentials, authority, signing, deployment, carrier/device, store, and legal work becomes an EXTERNAL_AUTHORITY_REQUIRED record with exact owner, action, and evidence.
