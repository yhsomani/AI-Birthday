# Birthday Autopilot

Birthday Autopilot is a React Native mobile app for Android and iPhone.

- **Android Automation Edition** can submit a pre-approved birthday SMS from the phone's SIM without birthday-day interaction, but only after every policy, permission, device, account, background, test, and duplicate-safety gate passes.
- **iOS Companion Edition** shares birthday planning, contacts, templates, privacy controls, and reminders. Apple requires the user to review and tap **Send** in the system Messages composer; the app never describes this as unattended.

The authoritative product and safety contract is [`PROJECT_ABOUT.md`](PROJECT_ABOUT.md). Generated Stitch work is a visual input only; [`stitch/SCREEN_MANIFEST.md`](stitch/SCREEN_MANIFEST.md) maps the required surfaces and states.

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
separately when working on cloud coordination:

```zsh
npm --prefix backend/functions ci
npm run backend:check
```

The public Firebase Hosting site is a second isolated package. It owns the
deterministic `/`, `/delete/`, `/privacy/`, `/terms/`, and `/support/` routes and
the signed-out content-free deletion-receipt lookup:

```zsh
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
for the exact release contract.

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
`gemini-3.5-flash` Vertex model in `global`; no Gemini provider API key is embedded. The stable
Android SDK currently brings a beta-labeled on-device interop module as a vendor-internal runtime
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

`devDebug` contains fixture providers and never merges `SEND_SMS`. The restricted permission exists only in the `labRelease` and `prodRelease` variants; every non-release `lab`/`prod` variant is removed from the Gradle graph.

Restricted packaging remains fail-closed until all signing variables, `BIRTHDAY_SIGNING_CERT_SHA256`, the requested tier's Firebase configuration, and `BIRTHDAY_DISTRIBUTION_EVIDENCE_FILE` are present. Evidence must satisfy [`tools/distribution-evidence.schema.json`](tools/distribution-evidence.schema.json), bind the exact tier/package/version/signing certificate, remain unexpired, and record policy, installer allowlisting, App Check, privacy, and physical carrier/SIM approval. Do not create placeholder approval evidence. A restricted APK must also be passed to `tools/verify-android-apk.sh` with `--restricted-evidence <json> <tier>` so its actual signing certificate and manifest are checked.

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

If Xcode reports a different Node version, remove the ignored `ios/.xcode.env.local`, ensure
Node 24.18.0 is active, and rerun the pod installation. The Xcode bundle phase independently
rejects any Node binary that is not exactly 24.18.0.

This machine currently has only Apple Command Line Tools, so iOS source can be reviewed and JavaScript-tested here but cannot be compiled or simulator-tested until full Xcode is installed.

## Quality checks

```zsh
npm run check
npm run security:secrets
npm run codegen:check
npm run bundle:check
npm run android:test
npm run android:lint
npm audit
npm run backend:check
npm run hosting:check
npm audit --prefix backend/functions --audit-level=high
npm audit --prefix backend/hosting --audit-level=high
```

`npm run check` already creates production-mode Android and iOS JavaScript bundles, so Metro
syntax or dependency-transform failures are caught before native packaging. The checked-in CI
workflow repeats the exact Node/npm checks, mobile and backend tests, backend emulator tests,
Android unit/lint/build verification, and an unsigned iOS simulator build using the locked Ruby,
Bundler, CocoaPods, and Xcode toolchain. CI needs no signing identity or provider configuration;
those remain separate release gates.

`npm run backend:test:emulator` additionally exercises deny-all Firestore rules
and server-only transactions against the safe `demo-birthday-autopilot` project.
It requires Java 21 and the Firebase emulator download. The mobile Jest and
ESLint graphs deliberately exclude `backend/`; the backend owns its separate
Vitest and ESLint configuration.

The Android build also exports Room schemas, verifies native unit tests, and keeps release signing outside the repository. Never add a service-account key, OAuth client secret, signing key, database passphrase, access token, or provider API key.

## Architecture

React Native owns screens, accessibility, navigation, transient drafts, and localized copy. Native code owns credentials and private provider results on both platforms. Android additionally owns encrypted durable state, recurrence authority, readiness, WorkManager, claim/arm coordination, SIM/SMS submission, callbacks, and recovery. JavaScript exposes no send, claim, arm, retry, scheduler, or delivery-transition API.

The historical root-level Firebase file belonged to another Android package and has been removed so it cannot be selected or copied accidentally. New tier-specific Firebase apps/configuration are required before real authentication or cloud coordination can be enabled.
