# Plan

## Overview

Make release truth and verification provenance explicit. The prompt must audit, plan, implement, retest, and report against an exact source/artifact/environment while refusing to self-authorize external evidence.

## Current Evidence

- The current tree is heavily modified and has no immutable release candidate.
- Full-history secret scanning fails on a historical Google API key and remains a P0 until approved rotation, destructive history remediation, cache/artifact expiry, and stale-clone replacement are completed.
- The release authority pin is UNPROVISIONED; cloud/store/physical-device/signing evidence is intentionally absent.
- Local root, backend, emulator, hosting, Android JVM, lint/build, and dev artifact checks have broad passing evidence.
- Two Swift compilation boundary tests fail under concurrent tool-suite execution but pass serially; control compiler/cache contention and set a measured timeout rather than merely increasing it.
- Critical-path coverage is uneven despite passing global thresholds.
- Physical Android/iOS, carrier/SIM/OEM, accessibility, performance/soak, signed artifact, deployed cloud, and store review evidence remains absent.

## Finding Contract

Every finding needs ID, severity, domain, claim, path/line observation, reproducer, impact, confidence, source/environment/external/flaky classification, required fix, acceptance, owner, dependencies, and open/verified/blocked status.

## Release Truth

Use distinct states: Implemented, Locally verified, Externally evidenced, Release-authorized. Scope each pass to an exact revision, artifact digest, environment, and observation time. Historical evidence cannot promote the current tree. Simulator is not physical evidence; AAB is not a Play-installed APK; emulator is not deployed cloud; green CI is not production approval; MessageUI Sent is not carrier delivery; unknown external state is blocked or unverified.

## Safety and Failure Handling

No deployment, upload, history rewrite, credential rotation, signing, real SMS, production-data access, destructive action, or external disclosure without explicit approval. Classify failures before retrying, bound transient retries, treat flakes as failures until controlled, preserve redacted logs and digests, and never lower thresholds or relax timeouts without measured justification.

## Verification and Reporting

Use targeted tests per task, broader layer checks per wave, then portable, native, emulator/simulator, security, E2E, accessibility, performance, artifact, cloud/store/release-evidence gates as applicable. Report source identity, executive verdict, findings, audited/not-audited coverage, commands/environments/results/artifacts/expiry/release meaning, external gates and owners, changes/tests/rollback, disagreements, judge rationale, residual risk, and BLOCKED/CANDIDATE/AUTHORIZED release decision.

## Completion

Leave no routine local engineering action unperformed. Stop only at genuine authority/safety boundaries, and describe the smallest exact external action without fabricating success.
