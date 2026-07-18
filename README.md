# Birthday Autopilot

Birthday Autopilot is a React Native mobile app for Android and iPhone.

- **Android Automation Edition** can submit a pre-approved birthday SMS from the phone's SIM without birthday-day interaction, but only after every policy, permission, device, account, background, test, and duplicate-safety gate passes.
- **iOS Companion Edition** shares birthday planning, contacts, templates, privacy controls, and reminders. Apple requires the user to review and tap **Send** in the system Messages composer; the app never describes this as unattended.

The authoritative product and safety contract is [`PROJECT_ABOUT.md`](PROJECT_ABOUT.md). Generated Stitch work is a visual input only; [`stitch/SCREEN_MANIFEST.md`](stitch/SCREEN_MANIFEST.md) maps the required surfaces and states.

This remains a React Native application even though the repository contains Kotlin and Swift. TypeScript/React Native owns the shared UI and product workflows. The native folders are the required TurboModule/OS boundary: Kotlin owns Android background work, SIM SMS, permissions, encrypted Room state, and callbacks; Swift owns iOS Google identity, protected storage, notifications, and Apple's foreground message composer. A JavaScript-only build cannot provide those operating-system capabilities or the requested unattended Android behavior.

## Toolchain

- Node `24.18.0`
- npm `11.6.0`
- React Native `0.86.0`, React `19.2.3`, Hermes, New Architecture
- Android: JDK 21, Java/Kotlin target 17, API 36, min API 29, arm64
- iOS: Xcode `26.5` with the iOS `26.5` SDK, iOS 15.1+ deployment target
- Ruby `3.4.10`, Bundler `4.0.15`, CocoaPods `1.16.2`

The exact Node/npm pair is enforced. Do not use `npm install --force` to bypass it.

Firebase will stop publishing new Apple SDK versions to CocoaPods in October 2026, while existing
versions remain installable. This repository intentionally keeps one locked CocoaPods graph today
because React Native still owns native pod integration. Plan and validate a single-manager
migration before the next Firebase upgrade; do not mix an ad hoc Firebase SPM graph into this
target. See [Firebase's CocoaPods migration guidance](https://firebase.google.com/docs/ios/cocoapods-deprecation).

```zsh
source "$HOME/.nvm/nvm.sh"
nvm install 24.18.0
nvm use
node -v   # v24.18.0
npm -v    # 11.6.0
npm ci
npm run codegen:check
```

If `npm -v` is not exactly `11.6.0`, run `npm install --global npm@11.6.0` after `nvm use`, then
run `npm ci`. `--force` cannot repair a mismatched Node/npm runtime and is intentionally rejected.

`npm ci` applies a narrow React Native `0.86.0` compatibility patch that makes iOS codegen and
post-install file discovery safe when the checkout path contains spaces. The postinstall fails
closed if React Native changes, and `npm run codegen:check` verifies the installed source without
modifying it. Review or remove the patch as part of any React Native upgrade.

The Firebase control plane is an intentionally isolated Node package. Install it
separately and validate it on the same pinned Node 22 line used by the deployed
Functions runtime:

```zsh
cd backend/functions
source "$HOME/.nvm/nvm.sh"
nvm install
npm install --global npm@11.6.0
npm ci
npm run check
cd ../..
```

The root Node 24 toolchain can orchestrate the same checks, but Node 22.23.1 CI
is the binding runtime-compatibility proof and the backend compiles against
Node 22 type definitions.

The public Firebase Hosting site is a second isolated package. It owns the
deterministic `/`, `/delete/`, `/privacy/`, `/terms/`, and `/support/` routes and
the signed-out content-free deletion-receipt lookup:

```zsh
source "$HOME/.nvm/nvm.sh"
nvm install 24.18.0
nvm use 24.18.0
npm install --global npm@11.6.0
npm --prefix backend/hosting ci
npm run hosting:check
```

The normal Hosting build is intentionally fail-closed and does not create a
deployable runtime config. A real deploy requires an approved out-of-repository
config containing the provisioned HTTPS origin, public developer identity,
legal/privacy/Hindi review evidence, reCAPTCHA Enterprise App Check site key,
and a tested identity-verified support/admin deletion workflow. No domain,
developer identity, support address, Firebase web config, or approval is
invented in source. See [`backend/hosting/README.md`](backend/hosting/README.md)
for the exact release contract. Production Hosting is deployed only by the
protected keyless workflow from a deterministic package; its report binds the
package/manifest, source/config/tree digests, selected project/site, and exact
Firebase-created version/live release into the cloud and final closure gates.

Production Firebase/Google Cloud configuration has a separate executable release
gate; a successful emulator run or source review is not cloud evidence. The
tracked template contains no invented project, app, OAuth, billing, Hosting,
quota, SLO, or approver values and is intentionally incapable of approval. A
protected manual workflow can collect only read-only production observations
through keyless Workload Identity Federation. Final validation additionally
requires a clean exact source revision, 43 digest-bound external evidence files,
seven distinct expiring approvals, and an Ed25519 signature from the existing
pinned release authority. The authority pin remains `UNPROVISIONED`, and no
cloud project has been mutated or represented as ready. See the
[cloud release evidence runbook](docs/CLOUD_RELEASE_EVIDENCE.md).

### Account-deletion recovery

Remote deletion and erasing one phone are deliberately separate. If deletion
acceptance is offline or ambiguous, Privacy offers a second native review that
can erase app-owned data on that device immediately. Native code first writes a
backup-excluded recovery journal containing only the private random receipt and
salted equality digests for the original Firebase UID and Google subject. The
receipt and digests never cross React Native, logs, analytics, URLs, or support
exports.

After that erase, ordinary setup and automation remain blocked. An unavailable
or `NOT_FOUND` receipt can only open the official Google chooser for an
exact-account, idempotent replay of the original receipt; a different account
is signed out and rejected. Only the strict signed-out `COMPLETED` receipt with
matching readable native state can unblock setup; corrupt or mismatched recovery
evidence remains fail-closed and routes to verified support. The app never infers an internal deletion stage or claims that
carrier, recipient, Messages, SMS-provider, iCloud, or other backup copies were
removed. The complete contract is BA-18 in [`PROJECT_ABOUT.md`](PROJECT_ABOUT.md).

Native Gemini authoring is pinned to the stable Firebase AI Logic SDK and stable
`gemini-3.5-flash` Vertex model in `global`; no Gemini provider API key is embedded. Both native paths
are additionally fail-closed behind the Remote Config boolean
`gemini_suggestions_enabled`: the app default is Off, foreground authoring uses only an already
activated remote value, and bounded background refresh never exposes configuration or Firebase
Installation identifiers to JavaScript or logs. Built-in templates remain available while Off.
The Android SDK currently brings a beta-labeled on-device interop module as a vendor-internal runtime
dependency. App code does not call or expose it, and it must not be manually excluded because the
stable Firebase registrar references it. Treat it as an explicit SBOM/release-review item and
recheck the upstream stable dependency graph before release. Direct preview APIs remain forbidden.

## Run Android

```zsh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run doctor:android
npm start
```

In another terminal:

```zsh
source "$HOME/.nvm/nvm.sh"
nvm use
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run android
```

`devDebug` runs the real native projection boundary but never merges `SEND_SMS`; fixture screens exist only in automated preview/tests and are not mounted by the app entry point. The restricted permission exists only in the `labRelease` and `prodRelease` variants; every non-release `lab`/`prod` variant is removed from the Gradle graph.

Restricted packaging remains fail-closed until all signing variables, `BIRTHDAY_SIGNING_CERT_SHA256`, the requested tier's Firebase configuration, and the authority inputs are present: `BIRTHDAY_DISTRIBUTION_EVIDENCE_FILE`, `BIRTHDAY_DISTRIBUTION_EVIDENCE_SIGNATURE_FILE`, `BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY_FILE`, and `BIRTHDAY_DISTRIBUTION_EVIDENCE_ROOT`. The evidence root must contain exactly every referenced policy/device/carrier/performance/accessibility/supply-chain/legal object at its signed normalized relative path and exact digest, with no extra, escaping, symlinked, hard-linked, or mutable file. The repository's [authority digest pin](tools/distribution-authority-pin.json) is deliberately `UNPROVISIONED`; a release authority must provision and commit the SHA-256 of its Ed25519 SubjectPublicKeyInfo before any lab/prod package can build. No private authority key belongs in this repository, CI, or the mobile signing environment. For Google Play, the configured keystore and `BIRTHDAY_SIGNING_CERT_SHA256` identify the AAB **upload** key; for direct/managed APK release they identify the installed-app key.

The authority signs the evidence file's exact raw bytes. Schema-v4 evidence binds every included approval—not only the requested tier—to the clean Git revision, exact tier/package/version name/version code, installed-app certificate, validity period, and immutable SHA-256 digests for policy, device, carrier, performance, accessibility, native supply-chain, and legal evidence. Google Play additionally requires a distinct `uploadSigningCertificateSha256`: Gradle validates the AAB build keystore against it but embeds the authority-approved Play app-signing certificate in runtime readiness. Direct/managed channels forbid the upload field and require the packaged APK signer to equal the installed signer. Final evidence binds the exact direct APK, Play AAB, or Play-delivered base APK bytes as applicable. See the subordinate [restricted-release evidence runbook](docs/ANDROID_RESTRICTED_RELEASE_EVIDENCE.md), [performance protocol](docs/PERFORMANCE_RELEASE_EVIDENCE.md), and [non-usable template](tools/distribution-evidence.template.json). Channel-specific verification uses `tools/verify-android-aab.sh` for the upload-key-signed Play AAB, `tools/verify-android-apk.sh --play-delivered-evidence` for a physical Play installation, or `tools/verify-android-apk.sh --restricted-evidence` for direct/managed APKs. The AAB verifier decodes the protobuf base manifest with locked, checksum-verified bundletool `1.18.1` and independently enforces the exact package/version/SDK/release-flag and restricted/forbidden-permission contract; an operator-supplied package argument is not artifact proof. Internal App Sharing's separate test signer cannot qualify as production. Self-authored JSON or a signature from an unpinned key cannot authorize packaging, upload, or distribution.

The manual [Android signed artifact pipeline](.github/workflows/android-release-evidence.yml) mirrors the iOS two-operation trust separation. Its protected build operation creates a short-lived, explicitly non-release Play AAB or direct/managed APK from exact authority inputs and exact reviewed assets downloaded from an access-controlled release in the separately administered evidence repository. A separate protected operation downloads that exact candidate and the same evidence-release tag through a step-scoped read-only token, checks the candidate run/revision and byte bindings, and runs the channel-specific standalone verifier against final artifact-bound authority evidence. It never rebuilds or deploys the artifact. Play-delivered APK approval still requires the runbook's physical-device verification after Play processing; a hosted AAB check cannot replace installed-app proof.

## Run iOS

Install full Xcode 26.5 first and select it. Apple Command Line Tools alone are insufficient.

```zsh
sudo xcode-select --switch /Applications/Xcode_26.5.app/Contents/Developer
xcodebuild -version  # Xcode 26.5
```

Put Ruby 3.4.10 ahead of Apple's system Ruby. Homebrew is one supported option on Apple Silicon;
any Ruby version manager is fine when it honors [`.ruby-version`](.ruby-version) exactly.

```zsh
brew install ruby@3.4
export PATH="$(brew --prefix ruby@3.4)/bin:$PATH"
ruby -v          # ruby 3.4.10 ...
gem install bundler --version 4.0.15 --no-document
bundle --version # 4.0.15
```

Then install the locked JavaScript, Ruby, and CocoaPods graphs:

```zsh
source "$HOME/.nvm/nvm.sh"
nvm use
node -v
npm -v
npm ci
bundle install
npm run ios:pods
npm run doctor:ios
npm run ios
```

Before `npm run ios`, provision `ios/Config/dev/GoogleService-Info.plist`. It must match the dev bundle ID, Firebase project, OAuth client, and reversed-client-ID build settings; the copy gate rejects a missing, cross-tier, or malformed file. Developers without provisioned services can prove compilation only—never runtime readiness—with the same unsigned simulator smoke flags used in CI:

```zsh
xcodebuild \
  -workspace ios/BirthdayAutopilot.xcworkspace \
  -scheme BirthdayAutopilot \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  BIRTHDAY_IOS_SIMULATOR_COMPILE_SMOKE=YES \
  build
```

If Xcode reports a different Node version, remove the ignored `ios/.xcode.env.local`, ensure
Node 24.18.0 is active, and rerun the pod installation. The Xcode bundle phase independently
rejects any Node binary that is not exactly 24.18.0.

### iOS production artifact gate

Normal CI intentionally produces only unsigned simulator candidates. It is not iPhone release
evidence. The manual [iOS signed artifact pipeline](.github/workflows/ios-release-evidence.yml)
first builds a short-lived signed candidate inside a protected environment, then requires a
separate run to verify those exact archive/IPA bytes against authority-signed final evidence. This
two-step boundary is necessary because an authority cannot honestly approve an artifact digest
before the artifact exists.

The final gate binds the clean Git revision embedded in `Info.plist`, production bundle/version/build,
Firebase/OAuth identity, App Store team/certificates/profiles/export method, signed entitlements,
privacy manifest, arm64 application and frameworks, exact archive/IPA digests, SBOM/provenance,
protected-storage/backup tests, device/performance/accessibility results, privacy/store/login review,
deletion, and rollback evidence. Missing, stale, mismatched, symlinked, or self-authored evidence
fails closed. The template is intentionally unusable until every sentinel is replaced and its exact
bytes are signed by the separately held authority key:

- [iOS release evidence runbook](docs/IOS_RELEASE_EVIDENCE.md)
- [iOS evidence schema](tools/ios-release-evidence.schema.json)
- [non-usable evidence template](tools/ios-release-evidence.template.json)

Neither Apple signing credentials, production Firebase config, provisioning profiles, nor the
release authority's private key belong in the repository. A green candidate operation is explicitly
named `not release`; only `verify-authority-approved-artifacts` can retain the final
`ios-production-release-evidence` package.

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
restricted-distribution or signed iOS archive gates.

## Quality checks

```zsh
npm run check:portable
npm run check
npm run security:secrets
npm run security:licenses
npm run security:native:android
npm run security:native:ios
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

`check:portable` is the complete host-independent workspace gate: shared mobile code, both
production JavaScript bundles, Firebase Functions, and the public Hosting site. Native Android
build/lint tasks and hosted iOS XCTest remain explicit platform checks and are enforced separately
by CI so the portable command never implies that Xcode or an Android SDK was exercised when it was
not.

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
Android build plugins, and iOS CocoaPods. It verifies every SBOM against its lock, verifies trunk
podspec checksum/source mappings before SwiftURL queries, and requires Maven, npm, and Swift
ecosystem canaries. A service outage, incomplete mapping, active finding, or unauthorized exception fails
closed. Ordinary CI permits zero exceptions; a reported zero means no active mapped OSV advisory at
scan time, not proof that a dependency has no vulnerability.

`npm run check` already creates production-mode Android and iOS JavaScript bundles, so Metro
syntax or dependency-transform failures are caught before native packaging. The checked-in CI
workflow repeats the exact mobile Node 24 and Functions Node 22 checks, coverage-enforced backend
tests, backend emulator tests, Android API 29/API 36/API 37-16 KB instrumentation,
production-flavor JVM/lint compilation, Debug and minified unsigned dev-Release builds, hosted iOS
XCTest, and unsigned iOS Debug and Release simulator builds using the locked Ruby, Bundler,
CocoaPods, and Xcode toolchain. It retains short-lived candidate APK/app, native test results,
reports, coverage, licenses, Gradle/CocoaPods locks, Gradle artifact-verification metadata,
JavaScript/Gradle/CocoaPods CycloneDX SBOMs, and native OSV reports. Deterministic, mode-aware
manifests hash every retained backend, Hosting, Android, and
iOS candidate file and describe every regular file and safe in-bundle symlink in the app-bundle
trees, including executable modes. The iOS app bundles are retained inside tar archives so
artifact transport does not erase executable permissions. These 14-day CI artifacts are
diagnostic candidate evidence, not durable signed release provenance. CI needs no signing identity
or provider configuration; those remain separate release gates.

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

React Native owns screens, accessibility, navigation, transient drafts, and localized copy. Native code owns credentials and private provider results on both platforms. Android additionally owns encrypted durable state, recurrence authority, readiness, WorkManager, claim/arm coordination, SIM/SMS submission, callbacks, and recovery. JavaScript exposes no send, claim, arm, retry, scheduler, or delivery-transition API.

The historical root-level Firebase file belonged to another Android package and has been removed so it cannot be selected or copied accidentally. New tier-specific Firebase apps/configuration are required before real authentication or cloud coordination can be enabled.
