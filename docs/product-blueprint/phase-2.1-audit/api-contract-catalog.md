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

## CI limitation

Latest backend workflow run failed during `npm ci`. Backend dependency audit, lint, TypeScript compile, Vitest and Firestore emulator tests were skipped. Therefore there is no fresh CI test evidence for these exports in run 45230577466.

## Required per-endpoint fields for completion

For each export, capture: request schema, required fields, authentication guard, App Check guard, region, secrets, Firestore paths, transaction boundaries, response schema, error codes, idempotency key, rate/abuse controls, caller(s), direct tests, emulator tests and deployment evidence.
