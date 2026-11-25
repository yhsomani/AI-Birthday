# Security and Privacy Architecture

Use OWASP ASVS/Top 10/NIST as guidance, not a compliance claim. Define trust boundaries in the [system map](system-architecture.md).

## Required controls

- Identity: validated auth token/server UID; short-lived/refresh; recent reauth for destructive/high-risk operations.
- Authorization: UID ownership for backup, server-only entitlement, least-privilege operators, no client status/body authority.
- Secrets: OS secure storage/managed secret stores; never in SQLite, backup, logs, analytics or public config.
- Data: classification/minimization; explicit provider transmission; encryption in transit; local/cloud encryption decision; key recovery implications documented.
- Prompt: structured/escaped/length-bounded data, instruction/data separation, injection/fabrication tests; no secret/PII logging.
- Backup: schema/cardinality/size/quota/integrity controls, generation retention/purge, owner abuse protection, deletion fencing.
- Auditing: security-relevant events with operation ID and no names/numbers/messages/prompts/tokens.
- Abuse: entitlement replay, App Check failure, cross-user access, oversized import/prompt, duplicate delivery, compromised token, stale restore, concurrent deletion.
- Supply chain: secret scanning, dependency/manifest/Gradle/Dart/Node audits, signed artifact/provenance and production config validation.
- Privacy lifecycle: point-of-use consent, account switch, export, local/cloud/provider scopes, deletion, retention, incident notification and DSAR process as jurisdiction requires.

See [risk register](../01-governance/risks-register.md); unresolved decisions RSK-001/002/007/009 are release blockers for affected scope.
