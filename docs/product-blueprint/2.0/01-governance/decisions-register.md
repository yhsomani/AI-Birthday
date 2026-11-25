# Decision Register

| ID | Consequential decision | Recommendation | Classification | Owner / approval | Related |
|---|---|---|---|---|---|
| DEC-036 | Adopt AI-Birthday 2.0 product identity/non-goals | Approve local-first product with reviewed manual handoff and gated, explicitly approved Android Autopilot | EPD/DA | Product scope approved by user; detailed gates remain pending | PRD, DOM-01–14 |
| DEC-037 | Initial platform/geography/languages | Android + English unless evidence supports more | ER/DA | Product/Legal | ASM-001/003 |
| DEC-038 | AI commercial model including BYOK/Nano | Validate willingness and avoid accidental double payment | ER/DA | Product/Business | FR-050–056 |
| DEC-039 | Backup semantics | Preserve local-first storage and explicit user-initiated backup; still decide merge/replace restore semantics and retention | EPD/DA | Local-first boundary approved by user; Product/Data/Security pending | DOM-10 |
| DEC-040 | Draft/delivery/backup retention | Set count/time purge and deletion evidence | ER/DA | Privacy/Product | RSK-001–003 |
| DEC-041 | Person aggregate write authority and transaction | One application service; all imports through it | ER/DA | Architecture | DEC-019/020/025 |
| DEC-042 | Birthday identity and migration | Per-cycle durable identity; no destructive migration before inventory | ER/DA | Data/Architecture | DEC-026/032 |
| DEC-043 | Account switch/local ownership | Keep contact/message data local and make backup explicit; define account-switch, deletion, and retention behavior as separate user workflows before implementing destructive operations | EPD/DA | Local-first boundary approved by user; detailed behavior pending Product/Privacy | BR-046 |
| DEC-044 | Autopilot product scope and Hosting identity | Include Birthday Autopilot in AI-Birthday; review the existing public Hosting identity and deployment boundary separately | EPD/DA | Product approved; Legal/Platform pending | DOM-13/14 |
| DEC-045 | Automatic SMS and auto-prepare | Automatic SMS is in product scope for Android with explicit per-person opt-in and exact-message review; use manual SMS handoff in Play if policy disallows automation. Keep activation fail-closed pending policy and device evidence. Auto-prepare remains a separate decision. | EPD/DA | Product scope approved by user; Legal/Platform/release approval pending | DR-001/002 |
| DEC-046 | Success metrics and analytics privacy | Define privacy-minimal outcome events before collection | ER/DA | Product/Privacy | NFR-OBS |
| DEC-047 | Stable job and reminder recovery contract | approve attempts, deadlines, operation/payload identity, heartbeat and annual/start/foreground/zone recovery | ER/DA | Architecture/Platform | RSK-011–013 |
