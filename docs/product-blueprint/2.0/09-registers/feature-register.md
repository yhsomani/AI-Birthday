# Central Feature Register

The full feature authority is [definitive/02](../../definitive/02-feature-specifications.md). 2.0 adds disposition, because product readiness changed after 2026-11-24.

| ID/range | Owner | Status/evidence | Priority/phase | Disposition |
|---|---|---|---|---|
| F-001–003 | Entry | IR; v1 route/init | P0/core | retain; harden init/migration recovery |
| F-010–019 | People | IR/partial; split writes/import bypass/delete scope | P0/core | redesign command ownership and lifecycle |
| F-020–024 | Birthday | IR/CF target | P0/core | redesign per-cycle identity/date integrity |
| F-030–038 | Planning/reminders | IR; platform evidence incomplete | P0/core | retain and verify device/zone |
| F-040–056 | Studio/AI | IR; entitlement established, prompt/revision gaps | P0/P1 | retain with hardening |
| F-060–064 | Delivery/history | IR partial | P0 | retain manual; redesign event semantics |
| F-070–082 | Account/entitlement | IR; deployed store evidence missing | P0 | retain, approve commercial and evidence |
| F-090–095 | Continuity/privacy | IR partial / DA | P0/P1 if enabled | redesign retention/integrity/delete |
| F-096–098 | Jobs/automation | IR/disabled capability | P1/conditional | retain job core; exclude auto paths |
| F-100–109 | Ops/public/control | server source / UA deployment | P1/conditional | separate/block pending DEC-044 |
