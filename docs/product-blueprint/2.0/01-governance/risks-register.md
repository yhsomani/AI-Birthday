# Risks Register

| ID | Risk | Severity/confidence | Evidence | Mitigation / owner | Status |
|---|---|---|---|---|---|
| RSK-001 | Unbounded cloud backup generations and partial generations | High/High | `cloud_sync_service.dart`, `firestore.rules`, no TTL/prune | DEC-039/040; lifecycle cleanup and abuse tests / Security-Data | Open |
| RSK-002 | Person/local deletion leaves drafts, delivery, PII and jobs | High/High | soft-delete/aggregate code; no whole-device purge | Define retention and purge; deletion inventory/tests / Privacy | Open |
| RSK-003 | Existing public Hosting identity and in-app Autopilot scope may be confused | High/High | backend Hosting vs app scope; deployment unverified | DEC-044; approve public identity, legal/support owner and release separately from in-app capability / Product-Legal | Open |
| RSK-004 | Import paths bypass invariant/application service | High/High | People UI/repository writes | DEC-041; unified command boundary / Architecture | Open |
| RSK-005 | Birthday rollover reuses identity and loses history | High/High | stable `birthday-{personId}`, schema no uniqueness/FK | DEC-042 and staged migration / Data | Open |
| RSK-006 | Timezone/calendar date persisted ambiguously | High/Medium | `TZDateTime` through Drift `DateTime` | Date/zone snapshot and cross-zone tests / Data-Mobile | Open |
| RSK-007 | Prompt delimiters can be closed by user content | Medium/High | XML-like prompt wrappers, unbounded instructions | structured provider payload/escaping/length/adversarial tests / AI-Security | Open |
| RSK-008 | Unsupported SLO/retention/support claims on Hosting | High/High | hardcoded public copy vs open decisions | remove/approve only backed commitments / Legal-Ops | Open |
| RSK-009 | Direct owner backup writes lack schema/rate/cardinality controls | Medium-High/High | Firestore rules/client writes | service boundary/App Check/quota/abuse controls / Security-Backend | Open |
| RSK-010 | Release claims exceed deployment/device evidence | High/High | docs vs backend/physical evidence gaps | production evidence pack / Release | Open |
| RSK-011 | Reminder schedules are not renewed at annual/start/resume/timezone boundaries | High/High | planner/scheduler only reconciles on data mutation; reboot restores existing triggers | startup/resume/zone/year reconciliation and OEM tests / Mobile | Open |
| RSK-012 | Crash recovery can retry a durable job beyond its intended attempt bound | High/High | startup requeues every running job; max-attempt check not enforced on claim | bounded crash/replay policy and operation-specific dedupe / Platform | Open |
| RSK-013 | Active-job dedupe ignores payload/operation kind | Medium-High/High | `(type, subjectId)` may collapse changed AI or backup/restore jobs | stable operation/payload key and contract tests / Platform | Open |
| RSK-014 | Native automatic-SMS cancellation/reboot races could claim stale work or duplicate a send | Critical/High | `BirthdaySmsReceiver.kt` persisted status, alarm recovery, and claim paths | Serialize cancellation/claim/recovery, bind alarms to current schedule generation, and verify on Android devices; both SMS gates remain disabled / Mobile-Security | Remediation in progress; runtime evidence pending |
| RSK-015 | Automatic-SMS approval is not yet a durable approval of one exact occurrence, message revision, destination, and send time | Critical/High | Dart approval status and persisted native job payload are not a revision-bound consent record | Invalidate on relevant edits/revocation and re-check current consent and exact payload before dispatch; do not activate until tested / Product-Security | Open |
