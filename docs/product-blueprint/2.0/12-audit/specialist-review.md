# Independent Specialist Review Consolidation

Actual specialized agents were used read-only on 2026-11-25 across Flutter discovery, backend discovery, documentation/history discovery, then independent product/UX, architecture/API/data, security/privacy, quality/performance/DevOps, and documentation-quality reviews. Findings below are consolidated; agent output is not itself stakeholder approval.

## Product/business (`P0`)

1. `CF` two product identities: core AI-Birthday reviewed handoff vs public “Birthday Autopilot”. Separate/deprecate via DEC-044.
2. `CF` blueprint status: proposed baseline conflicts with “production decision/readiness” wording. Add owner/date approval before normative claim.
3. `IR` privacy journey incomplete: export is People CSV; no whole-device wipe/account deletion UI; Hosting saga undeployed.
4. `UA/DA` paid model gates both BYOK and Nano despite user/provider cost and unvalidated willingness/packaging.

## UX/accessibility

Selected contrast/responsive/semantics tests exist (`IR`), but comprehensive TalkBack/manual/public-site and critical-journey evidence is missing (`UA`). Account switch/local ownership, permission revocation, capability-disabled states and send/deletion terminology require redesign approval.

## Architecture/API/data

- Split Person write authorities and import bypass (`IR`/high confidence).
- Birthday stable ID/rollover and no uniqueness/FK (`IR`/high).
- Schema v6 and leap persistence are current; old blueprint v5 statements stale (`CF`).
- Date/time zone and restore integrity/history are incomplete (`IR`/high).
- Purchase callable diverges from target contract; generated shared codecs needed (`CF`).
- Unintegrated control-plane endpoints are exported and scheduled despite blocked product scope (`IR/CF`).

## Security/privacy

High risks: unbounded/partial cloud backup generations, incomplete local/person deletion, direct owner backup abuse/cardinality, prompt delimiter injection, native SMS PII storage behind disabled capability, exact AI-provider disclosure, incomplete sink-level redaction, and missing supply-chain/provenance gates. Backup is not E2E encrypted. No compliance claim.

## QA/performance/reliability

Source has substantial unit/widget/E2E/backend/emulator/CI definitions, but no exact-current-commit CI run, signed Play AAB, production deployment, supported device matrix, real OAuth/store/App Check/restore/Nano/WhatsApp evidence, migration inventory, load/capacity evidence or operations drill is available. Numerical SLO/RPO/RTO/volume is `UA/DA`.

Critical reliability findings: reminders are not renewed across annual/start/resume/timezone boundaries; repeated process death can requeue jobs beyond intended attempt bounds; v3 migration swallows all errors and advances schema state; active-job deduplication ignores operation payload and can collapse backup/restore or changed AI requests; full-table streams plus 1,000-row CSV input lack a declared performance profile; backend capacities have no approved forecast/quota/load result. See RSK-011–013.

## Documentation

Preserve legacy, but do not let it compete as current authority. Material contradictions recorded: schema v5/v6, auto-SMS, Product/Autopilot identity, production readiness vs approvals, public retention/support commitments, complete deletion claim vs implementation, and parent/definitive `DOM/JRN` ID collisions. Mark old evidence by commit/date, add the current-head source/evidence register, namespace historical IDs, and use reproducible ID/link/diagram/field validation before claiming completeness.
