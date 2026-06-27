# Dependency Review

Last reviewed: 2026-06-27

RelateAI depends on Android, Firebase, Google, SQLCipher, Hilt, Compose, Kotlin, Gradle, and test libraries. Dependency changes must be reviewed before they enter the release branch.

## CI Gate

The Android CI workflow runs GitHub Dependency Review on pull requests:

- Action: `actions/dependency-review-action@v4`.
- Vulnerability threshold: fail on `moderate` or higher severity.
- License denylist: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`.
- Token permissions: read-only `contents` and `pull-requests`.

This gate reviews dependency changes introduced by a pull request. It does not replace a full release audit of the existing dependency graph.

## Release Requirements

Before production release:

- Confirm the latest target branch has no unresolved Dependabot or dependency graph alerts.
- Confirm every dependency change since the last release passed Dependency Review.
- Confirm new licenses are compatible with the distribution channel and app license obligations.
- Confirm native libraries, including SQLCipher, are represented in release notes or license notices where required.
- Confirm dependency update risk is reviewed for auth, backup encryption, SQLCipher, dispatch, Accessibility, SMS, and AI-provider paths.

## Exception Handling

Any exception must be documented in the release record with:

- Dependency coordinate and version.
- Vulnerability or license identifier.
- Reason for accepting the risk.
- Mitigation or upgrade plan.
- Owner and review date.
