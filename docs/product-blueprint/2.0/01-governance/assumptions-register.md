# Assumptions Register

| ID | Assumption | Classification | Confidence | Risk | Validation / owner | Status |
|---|---|---|---:|---|---|---|
| ASM-001 | Android is the initial supported platform | UA | Medium | iOS/site expectations conflict | Product owner approves platform matrix | DA |
| ASM-002 | Users value an AI-assisted paid birthday workflow | UA | Low | Commercial model may not convert | interviews, concierge study, funnel evidence / Product | Open |
| ASM-003 | English is the launch language; Hindi is not yet approved | UA | Medium | localized copy/expectations | approve locales/geography / Product-Legal | DA |
| ASM-004 | Local records are device-private rather than account-private | UA | Low | account switching leaks/duplicates expectations | shared-device/user research + decision / Product-Privacy | Open |
| ASM-005 | Core must remain useful without cloud and AI | EPD | High | architecture coupling | retain offline acceptance tests | Approved intent |
| ASM-006 | Personal-flavor automatic SMS remains excluded in v1 | EPD/IR | High | docs still describe it | publish release matrix / Product-Legal | Reconfirm |
| ASM-007 | Cloud backup is optional, owner-scoped snapshot transfer, not bidirectional truth | UA/IR | Medium | sync expectations/data loss | define backup/restore contract / Data-Product | DA |
| ASM-008 | A single app release target can serve the personal/local workflow without Autopilot control plane | UA | Medium | accidental deployment coupling | approve DEC-044 and environment separation / Product-Platform | DA |
