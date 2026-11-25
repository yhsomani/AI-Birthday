# Gaps and Questions Register

| ID | Gap/question | Evidence risk | Validation method | Required owner/decision |
|---|---|---|---|---|
| GAP-001 | Who is the launch customer and what is the validated willingness to pay? | unsupported commercial model | interviews + prototype + controlled pricing test | Founder/Product/Business |
| GAP-002 | Android only or other platforms? | backend/public site implies iOS | Android-first confirmed; approve a new client platform separately | Product |
| GAP-003 | Is Pro entitled if user supplies BYOK or uses Nano? | double-payment UX | commercial/research decision | Product |
| GAP-004 | How should account switching behave while records remain local-first? | data isolation/privacy | local-first boundary approved; specify preserve/export/wipe choices before implementation | Product/Privacy |
| GAP-005 | Exact delete/export/backup/retention scope? | irreversible privacy behavior | PIA, data-flow inventory, UX testing | Privacy/Product |
| GAP-006 | How should the existing public Hosting identity relate to in-app Autopilot? | Hosting/control-plane release boundaries remain unverified | Product scope approved; decide public identity, legal/support owner, and deployment separately | Product/Legal/Platform |
| GAP-007 | Approved countries/languages/accessibility support? | no release matrix | market/legal/accessibility plan | Product/Legal |
| GAP-008 | Real DB exception inventory | destructive migration risk | aggregate-only authorized copy; never raw PII upload | Data owner |
| GAP-009 | Live Firebase/Play/Hosting configuration? | no deployment proof | signed deployment evidence/runbook | DevOps/Release |
| GAP-010 | SLO/throughput/data volume assumptions? | no measurable reliability baseline | load model and observability plan | Product/Engineering |
