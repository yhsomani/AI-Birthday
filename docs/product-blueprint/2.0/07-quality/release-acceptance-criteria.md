# Release Acceptance Criteria

The affected product version can be accepted only when:
1. DEC-036–046 have approved disposition (or explicit exclusion, with matching code/docs/build).
2. Requirements, rules, journeys and critical TCs map through the traceability matrix.
3. High risks RSK-001–010 are mitigated, accepted with owner, or the affected scope is excluded.
4. Supported platform/language/accessibility and device matrix are named and evidenced.
5. Exact commit passes applicable CI; artifacts are signed and provenance recorded.
6. Store purchase, entitlement, App Check, sign-in and production Firebase configuration are exercised in the named release environment.
7. Privacy export/delete/retention/deletion receipts and Hosting claims have approved scope/runbooks, or are unavailable.
8. Backup/migration/restore and destructive changes have inventory, rehearsal and recovery evidence.
9. Operations, alerts, support escalation, incident/rollback/restore runbooks are owned and drilled.

Otherwise release is `blocked`; “source/test ready” is the correct highest claim.
