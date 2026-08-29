# Play Store submission evidence (Android-only)

Status: **required fail-closed release procedure subordinate to `PROJECT_ABOUT.md`; no store,
policy, legal, developer-account, domain, screenshot, or release approval is represented here**

WishWell has one combined, source-bound evidence contract for the English and Hindi
Google Play package. This document covers Android-only launch; iOS companion remains a Phase 3 future opportunity. A valid store package cannot make an unsigned artifact
releasable, authorize `SEND_SMS`, or prove physical carrier behavior.

## Files and authority

- `tools/store-submission-evidence.schema.json` documents every accepted field and rejects unknown
  structure in schema-aware review tooling.
- `tools/store-submission-evidence.template.json` is a non-usable draft. Its product copy is a
  review candidate derived from current app/Hosting copy; its external identity, URLs, countries,
  screenshots, policy answers, evidence, and approvals are deliberately absent. The Hindi copy is
  not represented as human-reviewed.
- `tools/validate-store-submission-evidence.mjs` is the authoritative semantic and artifact
  validator. It has distinct `template`, `submission`, and `release` modes.
- `tools/check-store-release.mjs` is the production hook. It always invokes `release` mode and has
  no permissive fallback.

Do not edit the committed template into a release record. Copy it to an access-controlled release
workspace, retain console exports and reviewer material there, and keep credentials in the named
vault. The JSON stores only a vault reference; a username, password, OAuth secret, access token,
refresh token, API key, signing key, or real user identity is forbidden.

## Three states

| State        | What can pass                                                                 | What remains blocked                                                                             |
| ------------ | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `draft`      | Candidate EN/HI copy and a complete inventory of missing inputs               | Submission, upload, policy claims, and release                                                   |
| `submission` | Real artifacts/assets/console exports plus all internal approvals             | Public release; Play SMS decision may still be pending                                           |
| `release`    | Everything in submission plus accepted Play SMS outcome                       | Nothing in this file waives Android distribution, signing, device/carrier, or Firebase gates     |

A Play rejection of the SMS permission blocks unattended Play distribution; it never disguises the app as a default SMS handler.

## Required package contents

The record binds all of the following to one 40-character source revision and the exact Android AAB SHA-256 digest:

- Android application ID `com.yashsomani.birthdayautopilot`, the approved positive version code and bounded version name, AAB name/digest, and Play signing-certificate digest;
- named launch countries and the approved public developer identity;
- provisioned HTTPS public base, store support, privacy, terms, account-deletion, and separately identity-verified support URLs, plus a public support email;
- exact EN/HI Play title, short/full description, release notes, each with human-copy-review evidence;
- a real 1024×500 opaque Play feature graphic; five EN and five HI Play phone screenshots at the recommended 9:16/1080px-or-greater size;
- every image's stable file path, byte count, dimensions, SHA-256, source artifact SHA-256, synthetic data declaration, system-UI boundary declaration, store approval, screen ID, variant, locale, and localized alt text;
- current Play Data Safety console exports, current taxonomy review time, exact relevant data-type answers, SDK inventory, privacy-policy consistency, and an assertion that every current console question—not merely the rows duplicated in JSON—was answered;
- the Play SMS Permissions Declaration, restricted permission list, exact device-automation use, prominent disclosure, recipient/content preapproval, carrier-cost disclosure, downloadable video evidence, reviewer instructions, test-account vault reference, and final policy decision;
- the Google-only login rationale: Google Contacts is the specific third-party service, the exact `contacts.readonly` scope, one visible Google account choice, SDK-managed credentials, no user-managed token, no second primary login, and the review decision;
- age/content/target-audience/export decisions, reviewer-access instructions, accessibility evidence, privacy/UI/Stitch inventories, provenance, legal-country review, support/operations review, and screenshot capture record; and
- dated product, engineering, security, privacy, legal/policy, accessibility/UX, operations/support, and release approvals. Every approval repeats the computed scope digest and points to an immutable evidence object with its own SHA-256.

The validator parses the current Stitch screen IDs and requires store coverage for setup, Home, People, and final Android approval. It rejects a screenshot that claims real personal data is present, imitates a provider/system surface, is not bound to the artifact, has a bad checksum or dimensions, or is missing its localized accessible description. Stitch prototypes are inventory only and never count as screenshots or release evidence.

## Hosting and source binding

Submission and release mode require the real out-of-repository Hosting config. The validator runs
the same `parseReleaseConfig` gate used by Firebase Hosting, then requires:

- the exact approved `developerDisplayName`;
- `publicSiteBaseUrl` equal to the configured Hosting origin;
- `/support/`, `/privacy/`, `/terms/`, and `/delete/` on that origin; and
- `identityVerifiedSupportUrl` equal to the separately provisioned support workflow.

The release CLI also requires a clean tracked checkout and makes `sourceRevision` equal the current
HEAD. Every referenced file must be a stable, non-empty regular file inside the selected evidence or
asset root. Absolute paths in the record, `..`, symlinks, digest mismatches, malformed/truncated image
data, expired evidence, placeholders, embedded credentials, and mutable approval scopes fail closed.

## Preparing and validating a package

First validate that the committed draft remains deliberately non-usable:

```zsh
npm run store:template:check
```

For an upload package, copy the template outside the repository, set `packageStage` to `submission`,
fill only verified values, and run:

```zsh
node tools/validate-store-submission-evidence.mjs \
  --mode submission \
  --file /secure/store/submission.json \
  --android-artifact /secure/artifacts/birthday-autopilot.aab \
  --asset-root /secure/store/assets \
  --evidence-root /secure/store/evidence \
  --hosting-config /secure/hosting/release-config.json \
  --print-digests
```

`--print-digests` prints the exact EN/HI copy digests and approval-scope digest. Record those values,
obtain approvals over that exact scope, and rerun without accepting any validation error. Changing
copy, countries, an answer, URL, screenshot, artifact, evidence reference, or review disposition
changes the scope and invalidates every prior approval.

After Play Store returns the required accepted SMS decision, change `packageStage` to `release`, bind
the decision exports, refresh approvals, and use the hard release hook:

```zsh
export BIRTHDAY_STORE_SUBMISSION_FILE=/secure/store/release.json
export BIRTHDAY_STORE_ANDROID_AAB=/secure/artifacts/birthday-autopilot.aab
export BIRTHDAY_STORE_ASSET_ROOT=/secure/store/assets
export BIRTHDAY_STORE_EVIDENCE_ROOT=/secure/store/evidence
export BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH=/secure/hosting/release-config.json
export BIRTHDAY_STORE_EVIDENCE_SIGNATURE=/secure/store/release.json.ed25519
export BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY=/secure/authority/release-public.pem
npm run store:release:check
```

The detached signature is exactly 64 raw Ed25519 bytes over the final JSON file's exact bytes. The
public key must match the tracked `tools/distribution-authority-pin.json` SPKI SHA-256 pin. A typed
approver name or an approval-document digest is not cryptographic authority: a nonempty, unsigned,
modified, symlinked, or unpinned release record fails before semantic validation. The committed pin
is deliberately `UNPROVISIONED`, so no store release can pass until independent release-authority
key ownership and rotation are established outside the repository.

Retain the final JSON, assets, evidence files, AAB, Android authority-signed distribution evidence, store decision exports, and validator output in the same immutable release package. Use `tools/create-evidence-manifest.mjs` when staging that package under `release-evidence/` so the existing source/toolchain provenance format covers its files. A generated manifest is evidence preparation, not self-approval.

## Current official requirements reviewed

The schema records `taxonomyReviewedAt` because console fields and asset rules can change. Each
Google Play Data Safety taxonomy review must be completed no later than the
package's `generatedAt` and validation time. The package's `validUntil` may be at most seven
24-hour days after that review; the exact seven-day boundary is accepted. A package that would
outlive the taxonomy review must retain a new console export, update `taxonomyReviewedAt`, and
receive new scope-bound approvals. Review the current primary documentation immediately before
each submission:

- [Google Play preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Google Play Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Google Play SMS/Call Log policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)
- [Google Play Permissions Declaration](https://support.google.com/googleplay/android-developer/answer/9214102?hl=en-EN)

These URLs are policy sources, not approval references. The retained console exports and written
store decisions are the release evidence.
