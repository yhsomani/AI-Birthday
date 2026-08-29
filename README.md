# WishWell

WishWell is a React Native mobile app for Android.

- **Android Automation Edition** can submit a pre-approved birthday SMS from the phone's SIM without birthday-day interaction, but only after every policy, permission, device, account, background, test, and duplicate-safety gate passes.

The former iOS Companion Edition has been fully removed from the repository; Android is the only supported platform.

The authoritative product and safety contract is [`PROJECT_ABOUT.md`](PROJECT_ABOUT.md). Generated Stitch work is a visual input only; [`stitch/SCREEN_MANIFEST.md`](stitch/SCREEN_MANIFEST.md) maps the required surfaces and states.

This remains a React Native application even though the repository contains Kotlin. TypeScript/React Native owns the shared UI and product workflows. The native folder is the required TurboModule/OS boundary: Kotlin owns Android background work, SIM SMS, permissions, encrypted Room state, and callbacks. A JavaScript-only build cannot provide those operating-system capabilities or the requested unattended Android behavior.

## Getting Started

Please see our dedicated guides for setting up the project:

- [Quickstart Guide](QUICKSTART.md): Minimal steps to get the app running locally.
- [Developer Guide](DEVELOPER_GUIDE.md): Detailed onboarding, prerequisites, and troubleshooting.

The Firebase control plane and public Firebase Hosting site are intentionally isolated Node packages. See their respective READMEs for deployment details.

Note: The Android SDK currently brings a beta-labeled on-device interop module as a vendor-internal runtime dependency. App code does not call or expose it, and it must not be manually excluded because the stable Firebase registrar references it. Treat it as an explicit SBOM/release-review item and recheck the upstream stable dependency graph before release. Direct preview APIs remain forbidden.

### Play and App Store submission gate

Store metadata is a separate fail-closed release input. The committed
[store template](tools/store-submission-evidence.template.json) contains truthful EN/HI candidate
copy but deliberately contains no developer identity, domain, support email, launch country,
screenshot, console answer, artifact digest, policy decision, or approval. `draft`, `submission`, and
`release` validation are distinct; a submission package may record pending store review, while the
hard release hook requires accepted Play SMS and App Review/login decisions, exact AAB/IPA and
screenshot digests, the approved Hosting identity/URLs, current privacy declarations, accessibility
evidence, and eight scope-bound approvals.

Run `npm run store:template:check` in ordinary development. Release operators follow the complete
[store submission evidence runbook](docs/STORE_SUBMISSION_EVIDENCE.md) and run
`npm run store:release:check` with the protected out-of-repository evidence package. Missing or
placeholder values cannot be promoted to approval. The store gate never replaces the Android
restricted-distribution gate.

## Quality checks

```zsh
npm run check:portable
npm run check
npm run security:secrets
npm run security:licenses
npm run security:native:android
npm run codegen:check
npm run bundle:check
npm run android:test
npm run android:lint
npm audit
npm run backend:check
npm run hosting:check
npm audit --prefix backend/functions --audit-level=high
npm audit --prefix backend/functions --omit=dev --audit-level=moderate
npm audit --prefix backend/hosting --audit-level=high
```

`check:portable` is the complete host-independent workspace gate: shared mobile code, the
production JavaScript bundle, Firebase Functions, and the public Hosting site. Native Android
build/lint tasks remain explicit platform checks and are enforced separately by CI so the portable
command never implies that an Android SDK was exercised when it was not.

When an Android Maven or build-plugin dependency intentionally changes, regenerate the complete
multi-flavor lock and artifact-checksum evidence with the pinned Node 24/JDK 21/Android SDK:

```zsh
tools/refresh-android-dependency-evidence.sh
```

The script covers dev, staging, lab, production, the isolated E2E fixture, JVM tests, lint, every
corresponding instrumentation APK graph, and the separately installed Android Test Orchestrator. Review
`android/app/gradle.lockfile`, `android/buildscript-gradle.lockfile`,
`android/settings-gradle.lockfile`, and `android/gradle/verification-metadata.xml` together. Because
the Android build runs on macOS locally and Linux in CI, retain the independently verified official
Google Maven `aapt2` checksum for both host classifiers when refreshing on only one host.

The JavaScript license gate validates the exact reviewed npm lockfile identities, package counts,
integrity records, registry origin, license allowlist, and pinned hashes for packages whose lockfile
metadata omits a license. Its optional `--output release-evidence/<set>/<file>.json` path is always
resolved from the repository root, rejects symbolic-link path segments, and uses create-only writes
so existing release evidence can never be overwritten.

The live [native dependency advisory gate](docs/NATIVE_DEPENDENCY_ADVISORY_GATE.md) scans four
truthfully labeled scopes: Android production runtime, the broader Android app/build/test graph,
Android build plugins, and iOS CocoaPods scaffolding (Phase 3). It verifies every SBOM against its lock, verifies trunk
podspec checksum/source mappings before SwiftURL queries, and requires Maven, npm, and Swift
ecosystem canaries. A service outage, incomplete mapping, active finding, or unauthorized exception fails
closed. Ordinary CI permits zero exceptions; a reported zero means no active mapped OSV advisory at
scan time, not proof that a dependency has no vulnerability.

`npm run check` already creates the production-mode Android JavaScript bundle, so Metro syntax or
dependency-transform failures are caught before native packaging. The checked-in CI workflow
repeats the exact mobile Node 24 and Functions Node 22 checks, coverage-enforced backend tests,
backend emulator tests, Android API 29/API 36/API 37-16 KB instrumentation, production-flavor
JVM/lint compilation, Debug and minified unsigned dev-Release builds, isolated UI E2E plus
production-path smoke on API 29, and the adversarial cloud evidence validators. It retains
short-lived candidate APK/app artifacts, native test results, reports, coverage, licenses,
Gradle locks and artifact-verification metadata, JavaScript/Gradle CycloneDX SBOMs, and native OSV
reports behind deterministic, mode-aware manifests that hash every retained backend and Android
candidate file, including executable modes. These 14-day CI artifacts are diagnostic candidate
evidence, not durable signed release provenance. CI needs no signing identity or provider
configuration; those remain separate release gates.

`npm run backend:test:emulator` additionally exercises deny-all Firestore rules
and server-only transactions against the safe `demo-birthday-autopilot` project.
It requires Java 21 and the Firebase emulator download. The mobile Jest and
ESLint graphs deliberately exclude `backend/`; the backend owns its separate
Vitest and ESLint configuration.

The Android build also exports Room schemas, verifies native unit tests, and keeps release signing outside the repository. Never add a service-account key, OAuth client secret, signing key, database passphrase, access token, or provider API key.

`npm run cloud:evidence:source` prints the production cloud source coordinates
only from a clean checkout. `npm run cloud:evidence:validate -- ...` verifies an
out-of-repository authority-signed evidence package; it never deploys or changes
Firebase/Google Cloud state. Ordinary CI runs the adversarial validator and
read-only workflow boundary tests without credentials.

## Architecture

React Native owns screens, accessibility, navigation, transient drafts, and localized copy. Native code owns credentials and private provider results. Android additionally owns encrypted durable state, recurrence authority, readiness, WorkManager, claim/arm coordination, SIM/SMS submission, callbacks, and recovery. JavaScript exposes no send, claim, arm, retry, scheduler, or delivery-transition API.

The historical root-level Firebase file belonged to another Android package and has been removed so it cannot be selected or copied accidentally. New tier-specific Firebase apps/configuration are required before real authentication or cloud coordination can be enabled.
