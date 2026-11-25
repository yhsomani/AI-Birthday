# Product Domain Catalog

| ID | Domain/purpose | Modules/features | Owner/users | Inputs/outputs, key rules, boundary |
|---|---|---|---|---|
| DOM-01 | Entry/shell: reach value safely | MOD-01 onboarding/navigation/config recovery; F-001–003 | local owner | config/DB/routes → initialized shell; never silent memory fallback |
| DOM-02 | Relationship memory | MOD-02 People CRUD, import, export, context | organizer | Person aggregate; validation, duplicates, privacy; no CRM/social |
| DOM-03 | Birthday engine | MOD-03 recurrence/occurrence/lifecycle | organizer/platform | Gregorian date/zone/leap rule → occurrence; one identity per intended cycle |
| DOM-04 | Planning | MOD-04 dashboard/calendar/search | organizer | canonical occurrence → priorities/actions; no independent date truth |
| DOM-05 | Reminders | MOD-05 preferences/permission/schedule/notify | organizer/OS | occurrence + preferences → platform intent; quiet hours/revocation/reboot |
| DOM-06 | Message Studio | MOD-06 editor/draft/revisions/review | organizer | facts + context → durable revision; manual always available |
| DOM-07 | AI assistance | MOD-07 entitlement/router/prompt/providers | organizer/provider | entitlement + facts → draft/failure; no invented facts or send authority |
| DOM-08 | Delivery/history | MOD-08 handoff/confirm/events | organizer/external apps | reviewed revision → handoff/confirmation; no inferred transport |
| DOM-09 | Account/entitlement | MOD-09 auth/session/store verification | account owner/store | identity/token/purchase → expiring entitlement; server/store truth |
| DOM-10 | Continuity/privacy | MOD-10 export/backup/restore/delete/retention | owner/privacy/ops | aggregates → generation/export/receipt; no encrypted/cloud claims without evidence |
| DOM-11 | Reliability/automation | MOD-11 durable jobs/reconciliation/conditional SMS | client/operator | intent → bounded job/effect; idempotent, cancel/fence/recover |
| DOM-12 | Platform operations | MOD-12 observability/releases/support/incidents | release/support | evidence/telemetry/config → safe operation; no private content |
| DOM-13 | Public support/legal | MOD-13 hosting/privacy/terms/delete/support | visitor/account owner | public help/legal input → truthful receipt | conditional/separate |
| DOM-14 | Autopilot control plane | MOD-14 lease/claim/arm/fence/transfer | restricted service actor | opaque operation metadata only | in AI-Birthday scope; integration/deployment gated |

Module dependency rule: DOM-02→03; 03→04/05/06; 06/09→07; 06→08; 09/10→server; 11 supports 05/07/10; 12 governs release. Detailed feature fields remain in [definitive/02](../../definitive/02-feature-specifications.md).
