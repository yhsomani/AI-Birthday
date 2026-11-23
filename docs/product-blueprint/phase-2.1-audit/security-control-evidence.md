# Security Control Evidence

**Scope:** Static source evidence reviewed in this Phase 2.1 tranche. This is not a penetration test or deployment attestation.

| Control | Source evidence | What source shows | What remains unverified |
|---|---|---|---|
| Authenticated backend calls | `backend/functions/src/functions/index.ts` | Auth/App Check guard helpers exist; common options enable App Check | Exact guard applied to every export; live enforcement |
| App Check | Same index | `enforceAppCheck: true`, `consumeAppCheckToken: true` in shared options | Runtime configuration and every callable's effective options |
| Owner-scoped Firestore backup | `backend/firestore.rules` | Reads/writes require authenticated UID to match path UID; deletion write fence | Deployed rules and emulator test pass |
| Entitlement writes | `backend/firestore.rules` | Client writes to entitlement documents denied | Deployed rules |
| Server-only coordination | `backend/firestore.rules` | Coordination state is not exposed through client allow rules; default deny behavior must be reviewed across full file | Complete rule evaluation and emulator tests |
| Local secret storage | `lib/app/providers.dart`, credential storage adapters | Gemini key and auth material use secure-storage abstraction | Platform keystore behavior, backup exclusion and device threat model |
| AI entitlement | `lib/features/ai/domain/ai_router.dart` | Router rejects requests without active entitlement before provider routing | Entitlement freshness, tamper resistance and server purchase lifecycle |
| Deletion flow | `backend/functions/src/services/deletionOrchestrator.ts`, hosting deletion client | Server orchestrator deletes Auth user and verifies absence; hosting requires reauthentication and persists receipt state | Deployed callable, end-to-end deletion completion and retention |
| Public web headers | `backend/firebase.json` | CSP and several security headers configured | Deployed response headers and CSP compatibility |
| Logging | `test/core/logging/app_logger_test.dart` | CI output shows sanitization tests ran and passed | Full PII inventory, native logs and release logging configuration |

## Security claims prohibited until verified

- “End-to-end encrypted backup” or “zero-PII cloud storage”.
- “Account deletion is complete” based only on callable source or a Hosting build.
- “SMS automation is safe/restricted to a particular flavor” without built artifact manifest inspection.
- “All Functions enforce App Check” until each export's effective options and guards are traced.
