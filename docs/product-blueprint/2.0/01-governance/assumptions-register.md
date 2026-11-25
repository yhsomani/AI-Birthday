# Assumptions Register

| ID | Assumption | Classification | Confidence | Risk | Validation / owner | Status |
|---|---|---|---:|---|---|---|
| ASM-001 | Android is the initial supported platform | EPD | High | iOS/site expectations conflict | User confirmed Android-first scope; review platform matrix before adding clients / Product | Approved scope |
| ASM-002 | Users value an AI-assisted paid birthday workflow | UA | Low | Commercial model may not convert | interviews, concierge study, funnel evidence / Product | Open |
| ASM-003 | English is the launch language; Hindi is not yet approved | UA | Medium | localized copy/expectations | approve locales/geography / Product-Legal | DA |
| ASM-004 | Contacts, phone numbers, drafts, and SMS content remain local; backup is explicit and user initiated | EPD | High | account switching/deletion/retention expectations remain open | User confirmed local-first boundary; approve lifecycle details / Product-Privacy | Boundary approved; workflows pending |
| ASM-005 | Core must remain useful without cloud and AI | EPD | High | architecture coupling | retain offline acceptance tests | Approved intent |
| ASM-006 | Android automatic SMS is in product scope but remains fail-closed until safety, policy, and device gates pass | EPD/IR | High | carrier/store permission and delivery expectations | User confirmed per-person opt-in and exact-message approval; verify policy and device behavior / Product-Legal-Platform | Scope approved; activation gated |
| ASM-007 | Cloud backup is optional, owner-scoped snapshot transfer, not bidirectional truth | UA/IR | Medium | sync expectations/data loss | define backup/restore contract / Data-Product | DA |
| ASM-008 | A single app release target can serve the personal/local workflow without Autopilot control plane | UA | Medium | accidental deployment coupling | approve DEC-044 and environment separation / Product-Platform | DA |
