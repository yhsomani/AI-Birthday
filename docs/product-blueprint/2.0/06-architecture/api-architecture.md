# API and Integration Architecture

Use versioned JSON/typed callable contracts over HTTPS. Generate clients/codecs from one schema. Common envelope: `contractVersion`, operation/request ID, validated subject from token, idempotency key where side effect exists, bounded payload and `{code,message,retryable,operationId,details?}` errors without internals/PII.

Required contracts:
- Purchase: exact request/response states, expiry/verification evidence, App Check/auth, rejection/replay/expiry tests.
- Backup: generation manifest with schema/base version, hashes, counts, collection boundaries, immutable publication and owner fencing.
- Deletion/reset: request/receipt state machine, recent auth, idempotent replay, in-progress/unknown/failed/completed distinct.
- AI: direct provider or local model behind allow-listed prompt contract; timeout/quota/safety classification; no raw prompt logging.
- Messaging: handoff output is content+channel+launch result, never transport status.
- Platform: versioned typed methods with unknown/unavailable; permission and OS-version normalization.

Rate/cardinality/quota controls, API deprecation, webhook/event contracts and deployed-envelope evidence are required before production. Detailed current/target catalog: [definitive/04](../../definitive/04-data-and-api-contracts.md).
