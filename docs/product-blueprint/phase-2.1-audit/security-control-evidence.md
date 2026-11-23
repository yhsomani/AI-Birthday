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


## Phase 2.1.1 Flutter finding — Stripe secret and entitlement authority

<badge color="danger">P0 architecture blocker</badge>

**Source files:** `lib/core/config/stripe_config.dart`, `lib/features/subscription/application/stripe_gateway_service.dart`, `lib/features/settings/presentation/settings_screen.dart`, `lib/features/subscription/application/subscription_service.dart`.

Observed source behavior:
1. `StripeConfig.secretKey` reads `STRIPE_SECRET_KEY` with `String.fromEnvironment`.
2. `StripeGatewayService` puts that value into `Authorization: Bearer ...` and calls Stripe Checkout, customer search, subscription lookup and billing portal APIs directly from Flutter.
3. Settings performs an email-based active-subscription lookup after browser checkout and then calls `setDevSandboxEntitlement`.
4. `setDevSandboxEntitlement` is documented in `SubscriptionNotifier` as a test hook and directly assigns local entitlement state.
5. No Stripe-specific backend source or Function exists in the repository tree.
6. The checked-in default secret is empty. CI's Play and Personal APK build commands only pass `FIREBASE_WEB_API_KEY`; `build-and-install-android.bat` passes no Stripe define. This means known build scripts do not configure a working secret-key path. Passing a real secret through a Dart define would expose it in the client artifact.

**Security conclusion:** A production Stripe integration must not put a Stripe secret in a Flutter app or grant paid entitlement through a client-side test hook. Move secret operations to a trusted backend; verify checkout ownership and subscription state server-side; use signed webhooks; return a server-verified entitlement. Rotate any secret that may already have been included in a shipped artifact. This is an audit finding only; no source code was changed.

**Related tests:** `test/features/subscription/stripe_gateway_service_test.dart` uses `MockClient` and an injected test secret. It verifies request construction and mocked responses, not live Stripe security or entitlement authority.
