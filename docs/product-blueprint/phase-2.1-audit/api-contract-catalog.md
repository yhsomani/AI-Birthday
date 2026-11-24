# API Contract Catalog

**Primary export source:** `backend/functions/src/functions/index.ts`  
**Region constant:** `asia-south1`  
**Common callable options:** App Check enforcement and token consumption are configured in the shared options object.  
**Status:** Export inventory verified; full request/response/error contracts remain OPEN pending handler/schema/service tracing.

## Export inventory

| Export | Trigger |
|---|---|
| `registerAndroidInstallation` | Callable |
| `renewSenderLease` | Callable |
| `changeAccountMode` | Callable |
| `claimOccurrence` | Callable |
| `claimTest` | Callable |
| `armAttempt` | Callable |
| `getArmStatus` | Callable |
| `reportTestOutcome` | Callable |
| `authorizeSafeRetry` | Callable |
| `beginSenderTransfer` | Callable |
| `completeSenderTransfer` | Callable |
| `requestAccountDeletion` | Callable |
| `accountDeletionReceipt` | Callable |
| `resetContactDerivedState` | Callable |
| `releaseAndroidSender` | Callable |
| `coordinationLifecycleStatus` | Callable |
| `companionStatus` | Callable |
| `verifyPurchase` | Callable |
| `sweepDeletionDrains` | Scheduled |
| `sweepCoordinationOperations` | Scheduled |

## Contract evidence

The index imports Zod transport schemas from `backend/functions/src/transport/schemas.ts`, including schemas for registration, lease, account mode, birthday/test claims, arm, companion status, contact-derived reset, coordination lifecycle, deletion, deletion receipt, retry, sender release, transfer and purchase verification.

The index defines two distinct guards:
- `requireAuthenticated`: requires both Firebase Auth and App Check context.
- `requireAuthOnly`: requires Firebase Auth context.
- `requireAppChecked`: requires App Check context.

Do not assume every export uses the same guard. The exact guard, schema, response shape, side effects and error mapping must be recorded per handler in the next contract pass.

## Purchase verification

`SubscriptionVerificationService` defines product ID `ai_birthday_pro_monthly` and expected Android package `com.yashsomani.ai_birthday`. It rejects package/product mismatch and queries Google Play through its verifier. This source presence does not prove Play Console configuration or live purchase verification.

## CI verification update

The backend CI failure recorded in the earlier Phase 2.1 snapshot is historical. The latest main run [45245907678](https://github.com/yhsomani/AI-Birthday/actions/runs/45245907678) on commit 644571f68be024423376749fe8e50e1db02ff018 passed dependency installation, production dependency audit, lint, TypeScript compilation, 11 Vitest files and 2 Firestore-emulator test files. This verifies the executed CI suites, not every endpoint contract, live deployment or production configuration.

## Required per-endpoint fields for completion

For each export, capture: request schema, required fields, authentication guard, App Check guard, region, secrets, Firestore paths, transaction boundaries, response schema, error codes, idempotency key, rate/abuse controls, caller(s), direct tests, emulator tests and deployment evidence.

## Per-export dispatch evidence

The current index body was inspected. This table records the request schema and authorization path at the exported handler boundary. It is not a substitute for validating the imported schema definition, service transaction and response contract.

| Export | Request schema | Handler guard / extra condition |
|---|---|---|
| `registerAndroidInstallation` | `registrationSchema` | `requireAuthenticated`; `commonOptions` |
| `renewSenderLease` | `leaseSchema` | `requireAuthenticated`; `commonOptions` |
| `changeAccountMode` | `accountModeSchema` | `requireAuthenticated`; `commonOptions` |
| `claimOccurrence` | `birthdayClaimSchema` | `requireAuthenticated`; `secretOptions` |
| `claimTest` | `testClaimSchema` | `requireAuthenticated`; `secretOptions` |
| `armAttempt` | `armSchema` | `requireAuthenticated`; `commonOptions` |
| `getArmStatus` | `armSchema` | `requireAuthenticated`; `commonOptions` |
| `reportTestOutcome` | `testReportSchema` | `requireAuthenticated`; `commonOptions` |
| `authorizeSafeRetry` | `retrySchema` | `requireAuthenticated`; `commonOptions` |
| `beginSenderTransfer` | `transferSchema` | `requireAuthenticated` + recent Google authentication |
| `completeSenderTransfer` | `transferSchema` | `requireAuthenticated` + recent Google authentication |
| `requestAccountDeletion` | `deletionSchema` | `requireAuthenticated` + recent Google authentication |
| `accountDeletionReceipt` | `deletionReceiptSchema` | `requireSignedOutAppChecked` |
| `resetContactDerivedState` | `contactDerivedResetSchema` | `requireAuthenticated` + recent Google authentication |
| `releaseAndroidSender` | `senderReleaseSchema` | `requireAuthenticated` + recent Google authentication |
| `coordinationLifecycleStatus` | `coordinationLifecycleStatusSchema` | `requireAuthenticated`; `commonOptions` |
| `companionStatus` | `companionStatusSchema` | `requireAuthenticated`; `commonOptions` |
| `verifyPurchase` | `verifyPurchaseSchema` | Firebase Auth required; App Check conditional on `ENFORCE_APP_CHECK_ON_PURCHASE=true` |
| `sweepDeletionDrains` | No request payload | Scheduled every 1 minute; region `asia-south1`; maxInstances 1 |
| `sweepCoordinationOperations` | No request payload | Scheduled every 1 minute; region `asia-south1`; maxInstances 1 |

## Shared transport validation

`backend/functions/src/transport/schemas.ts` uses Zod and strict object schemas. Shared bindings include contract version, ledger generation, installation ID, build/policy versions, distribution channel, sender epoch and reset generation. Request-specific schemas include UUID/request IDs, bounded alias arrays and SHA-256 prehashes. The purchase schema requires contractVersion, purchaseToken, productId, packageName and accountBinding.

The exported purchase response type is `{ status: 'active' | 'expired' | 'none'; productId; expiryDateMs; canUseAi; isAutoRenewing }`.

## Security nuance

The shared `commonOptions` object enables App Check, but `verifyPurchase` deliberately overrides App Check enforcement based on `ENFORCE_APP_CHECK_ON_PURCHASE`. Therefore “all exports always enforce App Check” is not an accurate blanket statement. The deletion receipt endpoint uses a signed-out App Check path. These exceptions must remain explicit in threat modelling.

## Test mappings from the registry

- `CTRL`: `backend/functions/test/controlPlane.emulator.test.ts`
- `CTRL-CLAIM`: `backend/functions/test/decisions.test.ts`
- `CTRL-DELETE`: `backend/functions/test/deletionReceipt.test.ts`
- `CTRL-PURCHASE`: `backend/functions/test/subscriptionVerification.test.ts`

These mapped backend tests ran in the latest CI workflow and passed (11 Vitest files and 2 Firestore-emulator test files). Exact per-handler assertion mapping and deployment evidence remain open.
