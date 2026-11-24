# Phase 2.2 — Current Baseline Revalidation and Product-Contract Gaps

**Repository:** yhsomani/AI-Birthday  
**Branch:** main  
**Reviewed commit:** 644571f68be024423376749fe8e50e1db02ff018  
**Tree:** 4e0c160ee71fc51ff3e1a98ccefddccae05cbdc7  
**Review date:** 2026-11-24  
**Status:** CI revalidation complete; product-definition and external-runtime verification remain PARTIAL  
**Change boundary:** documentation only

## Purpose

Phase 2.2 refreshes the evidence baseline after the Phase 2.1 source/test audit. It corrects stale CI status statements, reconciles the root source-of-truth documents with the executable AI entitlement policy, and records the remaining work required before this blueprint can be described as implementation-ready.

This phase does not imply that every feature contract is complete or that CI proves physical-device and production behavior.

## Start here

- [Current CI verification](current-ci-verification.md) — exact commit, workflow, job results, executed test suites and build artifacts.
- [Product-contract gap register](contract-gap-register.md) — open feature-specification, governance, security, platform and runtime-verification gaps.

## Current repository baseline

The recursive Git tree at the reviewed commit contains:

- 444 file blobs.
- 200 directory entries.
- 644 total tree entries.
- 108 files beneath docs/.

The product source includes a Flutter/Dart application, Android/Kotlin integrations, Firebase Cloud Functions, a Hosting web package and automated tests. The counts are repository inventory only; they are not a measure of product completeness.

## Revalidation results

| Area | Current evidence | Conclusion |
|---|---|---|
| Flutter | 460 tests passed; formatting and static analysis passed | CI pass |
| Backend | npm install, dependency audit, lint, TypeScript build, 11 Vitest files and 2 Firestore-emulator test files passed | CI pass |
| Android | Play and Personal release-flavor APK artifacts built; Play manifest excludes SEND_SMS | CI build pass; not device or store verification |
| Public Hosting package | typecheck, 12 Node tests and production build passed | CI pass; not deployment verification |
| AI entitlement rule | Router and tests require entitlement before BYOK or Nano; Settings copy agrees | Executable source/test evidence agrees with approved policy |
| Root documentation | SSOT and ARCHITECTURE routing statements corrected | Documentation consistency improved |
| Application source comments | AiRouter comments still describe BYOK as a Pro bypass | Open; source change prohibited in this documentation-only assignment |
| Stripe integration | Secret-key operations are wired in Flutter and a local entitlement setter is called from Settings | P0 production architecture blocker remains |
| Device/live services | No physical-device, live purchase, live AI provider or deployed environment verification in this run | Open |

## Important product decisions and boundaries

1. AI is subscription-gated across every provider. A configured personal Gemini API key selects a provider after entitlement succeeds; it does not bypass entitlement.
2. Manual drafting and local birthday management must remain usable when AI is locked or unavailable.
3. Opening WhatsApp or another messaging app is a handoff, not proof of send or delivery. The user remains in control of sending.
4. Personal-flavor automatic SMS remains a separate, conditional capability requiring distinct consent and release review.
5. Cloud backup remains opt-in and owner-scoped. Do not claim end-to-end encryption or zero-PII storage.
6. Birthday Autopilot is a separate control-plane surface, not proof of autonomous WhatsApp messaging in the mobile app.
7. CI APK artifacts are not proof of store signing, policy approval, installation or device behavior.

## Phase 2.2 exit criteria

- [x] Current main commit and repository tree identified.
- [x] Latest CI run and all three job outcomes inspected.
- [x] Flutter, backend, emulator and Hosting test/build outcomes captured.
- [x] Previous Phase 2.1 failure report explicitly superseded without deleting historical evidence.
- [x] SSOT and root architecture documents aligned to the all-provider entitlement policy.
- [x] Current Stripe architecture risk and remaining source-comment discrepancy retained.
- [ ] Atomic feature contracts complete for every domain and subfeature.
- [ ] Every requirement traced to acceptance criteria, source, exact test assertion and CI evidence.
- [ ] Business, platform, privacy, legal and commercial decisions approved by stakeholders.
- [ ] Real Android device, permissions, alarms, contacts, Nano, SMS and handoff journeys verified.
- [ ] Live Google OAuth, Play Billing, Gemini API, Firebase backup/restore and deletion flows verified in supported environments.
- [ ] Stripe integration redesigned and independently security-reviewed before production use.
- [ ] Firebase and Hosting deployment environments inspected and attested.
- [ ] Domain-specific mind maps and traceability diagrams complete and validated.

Phase 2.2 is therefore **PASS for current CI/documentation revalidation** and **PARTIAL for the complete product blueprint**.