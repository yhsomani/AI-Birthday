# Source Register

| SRC | Evidence | Classification | Confidence | Authority/use | Limitation |
|---|---|---|---|---|---|
| SRC-001 | `SSOT.md` | EPD/VF | High | Intended product principles | Current implementation/runtime not implied |
| SRC-002 | `README.md` | VF/CF | High for source claims; CF where auto-SMS stated | Product overview | Claims personal auto-SMS although gates are false |
| SRC-003 | `lib/**` at `593f7800` | IR/VF | High | Flutter implementation | Static source; deployment unproven |
| SRC-004 | `android/**` at `593f7800` | IR/VF | High | Android platform boundary | Device behavior not proven here |
| SRC-005 | `backend/functions/**` | IR/VF | High | Server control/verification design | Backend not verified deployed |
| SRC-006 | `backend/hosting/**` | IR/CF | High | Public-site implementation | Public identity/release remains separate from in-app Autopilot scope; do not claim deployment or unsupported operations |
| SRC-007 | `test/**`, integration tests, CI definition | VF | High | Test assertions and gates | Files do not prove a current run; device evidence separate |
| SRC-008 | Existing `definitive/**` | ER/EPD/UA | Medium-high | Rich product and architecture baseline | Dated 2026-11-24; current conflicts captured in 2.0 |
| SRC-009 | Git history `f72bec80`, `60d90d31`, `593f7800` | VF | High | Evolution and evidence baseline | Commit metadata cannot prove stakeholder approval |
| SRC-010 | External standards/prior links in repository | UA/ER | Medium | Candidate guidance | No live external verification performed in this update |
