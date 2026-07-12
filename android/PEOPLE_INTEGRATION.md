# Android Google identity and People integration

The Android client keeps Google ID tokens, contacts access tokens, People sync tokens, raw phone
numbers, and provider resource names behind native boundaries. Access tokens are memory-only and
cleared after each request. Sync tokens and normalized private contact material are stored only in
the SQLCipher Room database; React Native projections receive email/display text, opaque local
IDs, and masked phone values only.

People pages are written to dedicated generation-scoped staging tables. A terminal sync token is
required before one Room transaction applies a full or incremental generation. Failed, cancelled,
expired-token, and partial attempts delete staging rows while retaining the prior active contacts.
Full sync omissions and incremental deletion deltas become tombstones. Any approval-sensitive
name, birthday, phone, readiness, or deletion change increments material revisions, invalidates
active approvals, and moves a non-excluded recipient to review/blocked state.

Staging generations expire after 15 minutes (the network coordinator itself has a two-minute
limit). Overlapping generations for one account are rejected. All state changes use monotonic CAS
revisions; material changes also advance the shared blocker revision.

Credential Manager sign-in is wired to the currently resumed `MainActivity` through a weak
lifecycle registry. Incremental contacts consent uses a single-flight Activity Result owner
registered before the Activity starts. It launches only while the Activity is resumed, clears its
continuation on cancellation/destruction, ignores late callbacks, and passes only the native result
`Intent` back to AuthorizationClient. Physical Google consent testing still requires real tier
Firebase/OAuth configuration and a signed device build.
