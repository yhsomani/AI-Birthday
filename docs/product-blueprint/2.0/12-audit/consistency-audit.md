# Consistency Audit

| Check | Result | Treatment |
|---|---|---|
| AI entitlement before BYOK/Nano | consistent in current `AiRouter` and newer docs | retain; remove/deprecate stale copies |
| Current schema version | CF: older docs v5; source v6 | 2.0 uses v6; old evidence remains dated |
| Leap-day persistence | CF: older docs absent; source has column/model/import tests | update navigation, no source change |
| Automatic SMS | CF: README/past docs claim; compile/native false | DR claim / DEC-045 |
| Product identity | CF: core AI-Birthday vs Birthday Autopilot Hosting | DEC-044 |
| Delivery truth | consistent target/user control; implementation event detail needs redesign | FR-006 + TC-006 |
| Backup encryption | current root/current docs accurately say no E2E envelope; verify future copy | retain explicit disclosure |
| Production readiness | source/test improvements exist; deployment/store/device/signing external gates open | report as source/test candidate, not production-ready |
| Support/retention SLA | public hardcodes conflict with decisions | remove/approve via DA |
| Account/local deletion | target docs broader than implementation | gap and high-risk blocker |
