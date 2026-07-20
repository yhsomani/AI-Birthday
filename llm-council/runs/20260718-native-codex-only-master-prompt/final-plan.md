# Final Plan

## 1. Role, model, and authority

Create one self-contained prompt for the VS Code Codex extension. Require GPT-5.6 Sol for the root, three independent planners, all workers/reviewers, and the judge. Verify the current-session model or Sol alias and stop rather than substitute. Preserve the product's native runtime Gemini feature. Treat PROJECT_ABOUT.md as the sole product/technical contract and retain every fail-closed gate.

## 2. Bootstrap and working-tree preservation

Load canonical and nested AGENTS.md guidance and legacy AGENT.md when present; report absence. Record exact source identity, dirty status, untracked files, toolchain, and change ownership. Never reset, revert, broadly overwrite/reformat, stage, commit, or absorb pre-existing work. Use synthetic data and redact all private material.

## 3. Evidence-grounded intake and research

Inspect the maintained product, UI, domain, native, backend, hosting, tests, tools, workflows, contracts, and relevant source-of-truth/runbook files. Answer product and engineering questions with evidence, confidence, assumptions, conflicts, and validation needs. Use current primary-source research with retrieval dates and no repository-private search terms. Treat all current defect leads as hypotheses to confirm.

## 4. Independent Codex-only council

Use three independent Sol planners for product/UX, platform/full-stack safety, and verification/release truth. Keep outputs independent, anonymize and randomize them, treat them as untrusted, and use an independent Sol judge. Resolve disagreements against PROJECT_ABOUT.md and verified repository evidence. If an isolated CLI run is blocked by policy, use in-session Codex subagents without bypassing the denial and record the transport fallback.

## 5. One blocking-question batch and decision lock

After answering everything discoverable, ask at most one consolidated batch of choices that materially affect product behavior, privacy, distribution, irreversible design, cost, or external authority. If no such unknown remains, record `NO_BLOCKING_QUESTIONS` and proceed. Include recommendation, impact, and a safe fail-closed default where possible. Before edits, lock business outcomes, user jobs, users/platforms, scope/non-goals, Android/iOS behavior, architecture/data/privacy/security, acceptance journeys/states, risks, validation, release, and documentation impact.

## 6. Whole-system audit

Build requirement-to-code-to-test-to-evidence traceability across UI/UX states; React Native/TypeScript; Kotlin/Swift/TurboModules; persistence/migrations/protected storage; identity/contacts/deletion; runtime AI safety; Functions/Firestore/Hosting; background/concurrency/idempotency; Android SMS and iOS MessageUI truth; accessibility/localization/adaptation; security/privacy/abuse/legal/policy; dependencies/supply chain; performance/resources; CI/observability/incidents; stores/cloud; and every Section 19 gate.

Every finding records ID, severity, claim, evidence, reproducer, impact, confidence, classification, proposed resolution, acceptance, dependencies, and status.

## 7. Atomic dependency-ordered backlog

Each task owns one observable behavior or invariant and names exact files/ownership, preconditions/dependencies, risk/rollback, reproducer or failing test, deterministic acceptance, targeted and broad validation, evidence, and external dependencies. Split unrelated platforms, transitions, and refactors. Verify a task before its dependents.

## 8. Persistent implementation and remediation

For each task, recheck dirty overlap, establish the baseline, make the smallest coherent change, run focused checks, classify failures, fix introduced and routine local failures, run regressions, review the diff for private data/secrets/weakened gates/unrelated churn, record evidence, and continue. Never simulate completion with mocks, fixture-only paths, TODOs, disabled behavior, threshold reductions, arbitrary timeout increases, weakened schemas, or documentation claims.

## 9. Approval boundaries

Never autonomously deploy, upload, send a real SMS, mutate provider/store state, purchase, access production personal data, create/rotate credentials, rewrite history, sign/distribute artifacts, weaken gates, or claim external approval. Put physical-device, carrier/OEM, signing, production-cloud, legal, credential, and store needs in an external-authority ledger after completing all independent local work.

## 10. Verification and release truth

Run proportional targeted, unit, integration, contract, native, emulator/simulator, accessibility, localization, performance, security, E2E, artifact, CI, and release-evidence checks. Record source/artifact identity, environment, procedure, time, result, and release meaning. Treat flakes as failures until controlled.

Keep three truth axes:

- Implementation: complete, incomplete, or not applicable.
- Verification: locally verified, failed, externally pending, or not applicable.
- External authority: supplied/scope-matched, required, expired, or not applicable.

No build, CI, simulator, emulator, unsigned artifact, MessageUI result, test SMS, or historical report authorizes release.

## 11. Completion and report

Report model attestation, instruction/source precedence, source identity and dirty-tree preservation, locked decisions, council disagreements/judgment, audit coverage, findings/tasks/changes, validation provenance, external-authority ledger, privacy/security/runtime-AI impact, behavior-relevant docs, residual risk/rollback, and separate CODE_COMPLETE and EXTERNALLY_RELEASE_AUTHORIZED verdicts.

Completion requires all accepted in-repository work implemented and locally verified, critical/high local defects resolved, and no routine local task left to the user. External release authorization is true only with current, authoritative, scope-matched evidence; Codex can never self-authorize it.
