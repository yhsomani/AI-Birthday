# AI-Birthday Production Readiness Audit

Date: 2026-10-05

This report reflects repository inspection and the changes made on the production-readiness branch. A claim of test success is made only when the test command has actually executed.

## 1. Executive verdict

**PARTIAL**

Current maturity: functional prototype with a substantial architecture and broad test coverage, but important production contracts were previously inconsistent.

Biggest weakness: critical workflows had UI/backend/documentation contracts that did not match the real implementation, especially subscriptions and cloud backup.

Biggest opportunity: make the core loop — remember, prepare, personalize, review, send, confirm, complete, return — the only path that must be rock-solid.

Production readiness: **not yet verified for shipment**. The codebase is materially safer and more truthful after this pass, but runtime/CI evidence is still required.

## 2. Before state

The audit found:

- Annual birthday occurrences could remain on the previous cycle after the date passed.
- People screens contained hardcoded 2026 date formatting.
- Dashboard behavior could silently treat unavailable people data as an empty list.
- Failed birthday states were not surfaced as action-needed.
- The people list used a trailing countdown + menu layout that could become cramped.
- CSV export reported success without checking the share result.
- Editing a birthday could retain a draft/status from a different date.
- A dead demo-data seeding method contained fake people/drafts.
- Reminder refresh exceptions were swallowed in one provider path.
- The app exposed duplicate logger-provider definitions.
- Cloud backup omitted drafts and reminder settings and used incomplete restore semantics.
- Cloud backup status storage was not account-scoped.
- Firestore rules denied the exact backup collections used by the mobile client.
- Subscription verification used fake token/prefix logic and hardcoded expiry instead of Google Play verification.
- Release signing could fall back to the debug key.
- CI used Node 20 while backend package metadata requires Node 22+.
- Some documentation described an older architecture and unsupported privacy claims.

## 3. Implemented

### Birthday lifecycle

Added BirthdayLifecycleService and wired it into the birthday stream bootstrap.

Behavior:
- Creates a missing active occurrence.
- Rolls stale occurrences into the correct next calendar year.
- Resets an old draft when the birthday date/cycle changes.
- Preserves the current cycle's completed state.
- Uses the existing BirthdayEngine, including Feb 29 handling.

### Dashboard

- Loads people explicitly instead of treating a missing people value as an empty list.
- Surfaces people-load errors.
- Includes failed birthdays in action-needed items.
- Makes action-card controls stack at narrow widths or larger text scale.
- Removes one-off grey text colors.

### People

- Removes hardcoded 2026 display dates.
- Moves the countdown out of the trailing control row.
- Makes CSV export reporting depend on the share result.
- Removes the extra trailing collision risk in person rows.

### Data integrity

- Birthday edits invalidate stale drafts/status when month/day changes.
- Dead demo seed data was removed.
- Reminder-refresh failures are logged instead of silently swallowed.

### Cloud backup

- Uploads people, birthdays, drafts and reminder settings.
- Includes additional person lifecycle/version fields.
- Uses account-scoped last-sync timestamps.
- Paginates Firestore restores.
- Uses updatedAt to avoid overwriting newer local data with older remote data.
- Preserves soft-deletion data when restoring people.
- Reports partial backup failures truthfully.
- Removes the fake development Firebase API-key fallback.
- Firestore rules now permit authenticated owners to access only their own backup collections.

**Important:** the current implementation stores backup records as normal Firestore fields. It is not an encrypted zero-PII backup envelope.

### Subscription verification

Replaced fake verification with:

Google Play purchase token
→ authenticated callable
→ Google Play Developer API subscriptionsv2
→ product/package/state/expiry validation
→ server-side purchase-token ownership binding
→ entitlement write
→ AI unlock.

The purchase token itself is never stored; only a SHA-256 hash is stored for ownership binding.

### Subscription client

- No longer unlocks Pro because a token is non-empty.
- Sends purchase verification to the server.
- Restores purchases through server verification.
- Prevents expired entitlement from remaining AI-usable.
- Adds stable account-binding metadata.
- Fixes restore-completion races.
- Uses secure-store cleanup without ignoring the returned Future.

### Release safety

Release signing no longer silently falls back to the debug signing key.

### CI

- Backend job now uses Node 22.
- Firestore emulator tests are included in CI.
- Android artifact is explicitly described as an unsigned CI release artifact.

### Documentation

- Rewrote ARCHITECTURE.md to match the current runtime wiring.
- Corrected README privacy/backup/AI/release claims.
- Added this production-readiness report.

## 4. Completed previously partial/unimplemented areas

The strongest completed area is subscription verification: the prior fake validation has been replaced by a real Google Play Developer API integration.

Birthday annual-cycle refresh is also now implemented rather than relying on a permanently stored occurrence.

Cloud backup now has a fuller record set and account-scoped restore semantics.

## 5. Removed

- Dead demo birthday/person/message seed implementation.
- Debug signing fallback for release builds.
- Fake subscription-token acceptance logic.
- Hardcoded subscription expiry behavior.
- Silent reminder-refresh exception swallowing.

## 6. UI/UX redesign status

Implemented changes focused on correctness and responsive behavior:

- narrow-width dashboard action layout
- less collision-prone people rows
- truthful export and subscription messaging
- more semantic text colors
- no fake/demo content

**Not fully completed:** a ground-up visual redesign of every screen has not been independently implemented and validated with rendered-device screenshots in this environment. The current branch should therefore not be described as a finished visual redesign.

## 7. User journey — before

Install
→ onboarding
→ add person
→ save
→ birthday record can become stale across years
→ dashboard may hide a people-data failure
→ reminder
→ message studio
→ AI / subscription checks
→ WhatsApp
→ history

Critical trust problems were possible at the subscription, backup, and success-status boundaries.

## 8. User journey — after

Install
→ onboarding
→ add person
→ local persistence
→ current birthday-cycle reconciliation
→ actionable dashboard
→ reminder
→ message studio
→ verified entitlement / truthful AI failure
→ generated or edited draft
→ WhatsApp handoff
→ user sends
→ explicit user confirmation
→ completed state
→ history
→ next annual cycle.

## 9. Business improvements

Activation:
- The dashboard now exposes data-loading failure instead of silently showing an empty experience.

Retention:
- Birthday cycles now advance automatically instead of requiring the user to recreate past events.

Trust:
- Subscription and backup states are no longer allowed to claim success without the corresponding backend/platform result.
- WhatsApp handoff remains user-controlled.

Usability:
- Narrow layouts receive responsive action controls.
- People rows are less crowded.

Conversion:
- The subscription message explains that purchase verification happens before AI unlock.
- No unsupported price is shown in the revised settings copy.

Operational risk:
- Debug-signed release fallback removed.
- CI runtime aligned with the backend's declared Node requirement.
- Firestore rule coverage expanded for the actual backup paths.

No numerical business uplift is claimed because no controlled user experiment was run.

## 10. Remaining issues

1. Firebase App Check is disabled on verifyPurchase. Firebase authentication remains required, but this endpoint does not have the same App Check protection as the other callable functions.
2. Firebase ID-token refresh is incomplete on the client. The subscription/cloud flows depend on a currently usable stored ID token.
3. Play Console operational setup is still required. The backend runtime service account must have the permissions required to call the Google Play Developer API for the application.
4. Production Android signing credentials are not committed by design. CI must inject the real release keystore for a Play-ready signed artifact.
5. Visual redesign is incomplete. This pass fixed high-value correctness/responsiveness issues but did not finish a complete rendered-device redesign of every screen.
6. Runtime test evidence is missing from this environment. Repository inspection and source edits are not a substitute for executing Flutter, Android, Node, and emulator tests.
7. Cloud backup is not encrypted at the application layer. The current Firestore backup contains structured user data under the user's account.

## 11. Test results

### Executed in this environment

No Flutter, Dart, Node, Gradle, Android emulator, or Firestore-emulator command was successfully executed in the repository environment. The available repository connector allowed code inspection and commits but did not provide a working local checkout/runtime.

Therefore:

- Flutter tests passed: **not executed**
- Flutter tests failed: **not executed**
- flutter analyze: **not executed**
- Backend Vitest: **not executed**
- Firestore emulator tests: **not executed**
- Android release build: **not executed**
- UI screenshot/runtime validation: **not executed**

The CI workflow has been updated to execute these checks, but until a CI run is observed, the result remains **UNVERIFIED**.

## 12. Production readiness matrix

| Area | Status | Evidence | Remaining risk |
|---|---|---|---|
| Core workflow | PARTIAL | Birthday lifecycle + dashboard fixes committed | End-to-end runtime validation not executed |
| UI/UX | PARTIAL | Responsive and truthfulness fixes | Full visual redesign not finished |
| Navigation | PARTIAL | Existing routed flows inspected | Full runtime navigation sweep not executed |
| Data integrity | IMPROVED | Cycle reset, account-scoped backup, timestamp conflict checks | Restore/runtime tests still needed |
| AI | PARTIAL | Verified entitlement gate and existing provider routing | Native Gemini Nano runtime not verified |
| Gemini setup | PARTIAL | Existing API-key UX retained; secure storage wording corrected | Runtime failure-path validation needed |
| Notifications | PARTIAL | Existing reminder stack inspected; errors no longer silently swallowed | Device restart/permission/real alarm test not executed |
| WhatsApp | PARTIAL | Existing truthful handoff model retained | Real device handoff/confirmation not executed |
| Privacy | PARTIAL | Documentation corrected; backup boundary made explicit | Cloud backup is plaintext Firestore data |
| Security | IMPROVED | Server Play verification, ownership binding, owner-scoped rules, no debug signing | App Check and token refresh remain |
| Accessibility | PARTIAL | Narrow/text-scale dashboard protection | Full device/accessibility audit not executed |
| Performance | UNVERIFIED | No runtime profiling executed | Needs real-device profile |
| Subscription | IMPROVED | Play API verification + token ownership binding | Play Console integration must be configured and tested |
| Backup/restore | IMPROVED | Complete record set, pagination, timestamp conflict handling | Full disaster-recovery run not executed |
| Testing | PARTIAL | Added lifecycle/subscription/rules coverage and fixed stale tests | CI execution evidence missing |

## 13. Critical questions

1. Can a new non-technical user install and understand the app without external help? **PARTIALLY.** Core onboarding exists, but final rendered-device UX validation is still missing.
2. Can a user add a person and trust the birthday is remembered? **PARTIALLY.** Local persistence and annual-cycle reconciliation are now implemented, but runtime verification is pending.
3. Can the user receive a useful reminder and act? **PARTIALLY.** The reminder stack exists and errors are better surfaced, but actual device-level verification was not executed.
4. Can the user generate a useful personalized message? **PARTIALLY.** AI routing and existing prompt/domain logic are present; runtime AI provider validation remains.
5. Can a non-technical user configure Gemini? **PARTIALLY.** The settings flow exists, but the full failure-path UX still needs device validation.
6. Can the user send through WhatsApp without false success? **YES at the state-model/code-contract level.** Opening WhatsApp is not treated as sending; explicit confirmation is required.
7. Does the app remain useful when AI is unavailable? **YES at the architecture/state level.** Birthday management does not depend on AI.
8. Can the user trust data is preserved? **PARTIALLY.** Local-first persistence and safer backup/restore exist, but full disaster-recovery execution is not verified and cloud backup remains plaintext.
9. Does it look and behave like a professional production app? **PARTIALLY.** Several UI defects were fixed; a complete visual redesign and device validation remain.
10. Would I ship it today? **NO.** Runtime/CI evidence, Play Console setup, App Check, token-refresh lifecycle, and full visual/device validation are still outstanding.

## 14. Top 10 highest-value changes

1. Replace fake subscription verification with Google Play verification.
2. Bind verified Play purchase tokens to the first authenticated account.
3. Remove debug signing fallback.
4. Reconcile annual birthday cycles automatically.
5. Prevent stale drafts/status after birthday-date edits.
6. Fix cloud backup authorization mismatch between client paths and Firestore rules.
7. Expand cloud backup to drafts/reminder settings and improve restore conflict handling.
8. Remove fake demo seed data.
9. Fix dashboard people-data error handling and narrow-width actions.
10. Make success/status messaging depend on real operation results.

## 15. Final ship decision

# SHIP AFTER FIXES

The repository is materially safer and more coherent after this implementation pass, but it still lacks execution evidence and has known production risks that must be closed before real-user release.
