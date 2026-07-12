# Native dependency advisory gate

Status: required candidate and release evidence for Android and iOS. A passing
scan means that the exact mapped dependency identities returned no active,
indexed OSV advisory at scan time. It is not proof that a package is free of
vulnerabilities and does not replace source review, artifact verification,
platform policy review, or penetration testing.

## Enforced scopes

The gate keeps runtime and tooling evidence separate. Combining them into one
number would make a build plugin look like shipped application code and could
hide an incomplete production-runtime graph.

| Dependency set           | Exact scope                                                                                                        | Release interpretation                                                             |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| `android-prod-runtime`   | `prodReleaseRuntimeClasspath` entries in `android/app/gradle.lockfile`                                             | Android code and libraries packaged or reachable in the production release runtime |
| `android-complete-graph` | Every locked app configuration, including build, JVM test, lint, instrumentation, E2E, staging, and release graphs | Broad engineering and candidate exposure; never label this set as runtime-only     |
| `android-build-plugins`  | `android/buildscript-gradle.lockfile`                                                                              | Gradle/build-time execution exposure; not packaged app runtime                     |
| `ios-cocoapods`          | Canonical root pods from `ios/Podfile.lock`                                                                        | CocoaPods graph used by the iOS application                                        |

Every CycloneDX 1.6 SBOM is regenerated from the corresponding lock. The gate
recomputes the lock SHA-256, component count, exact component set, and Gradle
configuration marker. A stale, truncated, duplicated, empty, or wrong-scope
SBOM fails before any advisory result can pass.

## Package identity and source verification

Android coordinates map directly to canonical Maven package URLs. The same
Maven identity is used in the SBOM and the OSV query.

OSV does not define a CocoaPods ecosystem mapping in its
[package-URL helper](https://github.com/google/osv.dev/blob/master/osv/purl_helpers.py).
The iOS gate therefore does not treat an empty CocoaPods query as a clean
result. It resolves the graph as follows:

1. CocoaPods subspecs are canonicalized to their root pod and version.
2. Every trunk pod must have an exact entry in
   `tools/cocoapods-osv-source-map.json` matching its `SPEC CHECKSUM`.
3. The gate retrieves that exact public podspec through CocoaPods' official CDN
   route. CocoaPods currently redirects to jsDelivr; only the exact
   `https://cdn.jsdelivr.net/cocoa/Specs/...` mirror path is accepted, and no
   second or arbitrary redirect is followed.
4. The downloaded bytes must match the lock's podspec SHA-1. The pod name,
   version, GitHub repository, and tag must match the reviewed source-map entry.
5. That checksum-bound repository/tag is queried as an OSV `SwiftURL` package
   identity. Local React Native pods are instead mapped to their exact owning
   npm package and version in `package-lock.json`.

An unknown pod, changed checksum, changed source repository/tag, missing npm
owner, malformed package URL, or incomplete source verification blocks the
scan. Source-map updates must be reviewed together with `Podfile.lock` and the
actual podspec bytes; guessing a repository from a pod name is forbidden.

The implementation calls the public
[OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/) directly
over HTTPS. It sends only public package identities and page tokens—never
contacts, birthdays, phone numbers, messages, prompts, user identifiers,
credentials, repository source, or artifact contents. Requests have bounded
batch sizes, response sizes, retries, pagination, and timeouts.

## Fail-closed result policy

Before scanning the project, the adapter must find known advisories for Maven,
npm, and SwiftURL canaries. This prevents an empty, incompatible, or
wrong-ecosystem backend response from appearing clean. Withdrawn OSV records
are excluded by the OSV query API; every active returned advisory is a finding.

The scan fails when:

- OSV or CocoaPods source verification is unavailable or malformed;
- any ecosystem canary is missing;
- any query, page, component, source, lock, or SBOM is omitted or inconsistent;
- a returned active advisory has no valid exact exception; or
- an exception is malformed, stale, expired, unmatched, unsigned, or approved
  by an untrusted authority.

`0 findings` means only “no active advisory returned for the exact mapped
identities at this time.” In particular, an empty iOS result is not evidence
that OSV recognizes every CocoaPod; the source mapping, npm/Swift canaries, exact
query identities, and verified podspec-source count must be retained with the
report.

## Ordinary developer scans

Use the pinned Node 24/npm 11 toolchain and current locks:

```zsh
npm run security:native:android
npm run security:native:ios
```

Each command creates an immutable report under `release-evidence/` and prints
its path. Temporary SBOMs are created outside the checkout and removed after
the report is written. A repeated run creates a new report rather than
overwriting prior evidence. These are live network gates; an unavailable
advisory or podspec service correctly returns a nonzero exit.

On an enterprise network that intercepts TLS, point `NODE_EXTRA_CA_CERTS` at the
organization-approved CA bundle supplied outside the repository. Never set
`NODE_TLS_REJECT_UNAUTHORIZED=0`, bypass certificate validation, or commit a
private enterprise trust bundle. A release environment must attest its trust
store with the rest of the toolchain.

CI retains explicit SBOMs and reports:

- Android complete graph, production runtime, build plugins, OSV report, locks,
  Gradle artifact-verification metadata, and the exception-policy bytes; and
- iOS CocoaPods SBOM, OSV report, `Podfile.lock`, npm lock, exact CocoaPods
  source map, exception-policy bytes, and checksum manifest.

The report and its inputs are candidate evidence. A release authority must
bind their immutable digests into the platform's signed release evidence; a
developer-authored passing JSON report is not release approval.

## Exception governance

`tools/native-advisory-exceptions.json` is intentionally empty. Ordinary CI
accepts exactly zero exceptions and has no exception-signing key. Do not add a
temporary waiver merely to restore a green build; update or remove the affected
dependency whenever a compatible fixed version exists.

If no fixed or replaceable dependency exists and the business explicitly
accepts the residual risk, each exception must bind one vulnerability ID, one
dependency-set label, one component package URL, and one query package URL. It
also requires a named owner, a distinct approver, a substantive rationale, an
immutable tracking reference, the SHA-256 of independent approval evidence,
and approval/expiry instants no more than 30 days apart. Placeholder, zero,
future-dated, self-approved, overlong, expired, duplicate, or unmatched entries
fail.

A nonempty exception file is unusable unless an independent authority signs
the file's exact raw bytes with Ed25519 and the supplied public key matches
`tools/distribution-authority-pin.json`. The current `UNPROVISIONED` pin means
that no exception can be accepted. Provisioning that public-key digest is a
separate protected release-governance change; the private key must never enter
the repository, developer workstation, or ordinary CI. Forged signatures and
valid signatures over different bytes fail.

## Dependency update procedure

For an Android Maven or plugin change:

1. Prefer the narrowest compatible fixed release and confirm it in the
   dependency owner's official release information.
2. Apply an explicit Gradle constraint when an upstream plugin still selects a
   known-vulnerable transitive version.
3. Run `tools/refresh-android-dependency-evidence.sh` with the pinned JDK,
   Android SDK/NDK, and Gradle wrapper. Review all three Gradle locks and
   `android/gradle/verification-metadata.xml` together.
4. Run unit, lint, instrumentation compile/assemble, isolated E2E, and affected
   release variants, then repeat both the production-runtime and complete-graph
   scans.

For a CocoaPods change:

1. Update pods through the locked Bundler/CocoaPods toolchain and review the
   complete `Podfile.lock` diff.
2. For each changed trunk pod, verify the exact CDN podspec checksum and source
   GitHub repository/tag before updating the source map.
3. For each changed local pod, verify the owning npm package and exact lock
   version.
4. Run hosted native tests/builds and the iOS advisory gate.

Never hand-edit a lock to silence a result, downgrade a secure constraint,
remove a query identity, disable a canary, or describe a complete build/test
graph as the production runtime.

## Finding or service incident

Retain any content-free blocked report that was produced and the exact
lock/SBOM/source-map digests. If validation failed before a report could be
created, retain the sanitized error category, tool revision, input digests, and
UTC attempt time. Do not place private application or user data in an advisory
ticket.

- For a finding, stop the candidate, identify whether it affects production
  runtime or only build/test tooling, update/remove the dependency, regenerate
  lock and checksum evidence, run the affected cross-platform tests, and scan
  again. A tooling-only finding still blocks the candidate until remediated or
  independently accepted under the exception process.
- For OSV or CocoaPods CDN unavailability, retry only after service recovery.
  Do not reuse an old passing report as current evidence or convert a transport
  failure into zero findings.
- For an expiring exception, remediation must land before expiry. Renewal is a
  new independently signed risk decision over new exact bytes, not an edited
  timestamp.
- If the source map or canary becomes inconsistent with upstream data, treat it
  as a gate defect, preserve the failure, correct the mapping with primary
  source evidence, and repeat the entire scan.
