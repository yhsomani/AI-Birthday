# Deployment and Operations

Production-readiness is a set of evidence gates, not “code exists.” Release artifacts must identify commit, dependencies, schema version, environment, region, signing identity, capability flags and approvers. Operations owns on-call/escalation, SLO/error-budget policy after approval, dashboards, privacy-safe logs, alerts, stuck-job/queue/deletion-fence indicators, support linkage, incident records, backup/restore and rollback drills.

A release is blocked if: unapproved product variant is present; destructive migration lacks inventory/rehearsal; entitlement/store/App Check is unverified; backup retention/delete is undefined; release signing or Play channel is absent; critical device/accessibility tests absent; hosting/legal/support copy exceeds approvals; or any high risk lacks accepted mitigation/owner.
