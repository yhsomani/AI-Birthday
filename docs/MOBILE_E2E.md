# Mobile device E2E evidence

The repository has two complementary deterministic black-box UI lanes for
Android emulators and iOS simulators. The fixture lane exercises an isolated
synthetic app and its guided/localized journeys. The production-path smoke lane
launches the real `index.js`, providers, native adapter, schema decoders, and
live navigation against a separate read-only native host. Neither lane can
reach restricted SMS, production identity, or a production service.

## Production-path smoke lane

This is the narrow integration check between component tests and real-service
evidence. It proves that both mobile shells can load the production React tree,
resolve a platform native module, decode every reviewed projection through the
real `BirthdayNativeAdapter`, and navigate Home, People, and Settings.

The lane is deliberately fail-closed:

- Android uses only `smokeDebug`, package
  `com.yashsomani.birthdayautopilot.smoke`, a distinct label/icon, and the real
  `index.js`. Its application registers the synthetic `BirthdayNative` package
  but never creates `AppGraph`, Firebase, WorkManager, a scheduler, a permission
  owner, or an SMS gateway. Its merged manifest has only `INTERNET`, its one
  Activity, and localhost-only cleartext for Metro.
- iOS uses the `Smoke` configuration and
  `BirthdayAutopilotProductionSmoke` scheme with the same distinct `.smoke`
  bundle ID. It is unsigned and simulator-only, loads the real production
  component, compiles product native bridges out, and skips Google/Firebase,
  background refresh, notifications, MessageUI, reminders, and Gemini startup.
  `Info-Smoke.plist` has no URL scheme, background mode, Firebase key, or Google
  key, and its entitlements file is empty.
- Both hosts consume
  `e2e/production-smoke/production-smoke-projections.json`. The checked-in
  document contains zero contacts, activity, templates, approvals, or storage
  bytes and no phone/message/token fields. Read calls are restricted to an
  explicit projection map. Every user intent, including OAuth, permissions,
  messaging, scheduling, and deletion, returns the same schema-valid
  `distribution-channel-unapproved` unsupported problem without dispatch.
- The fixture is included only by the Android `smoke` source set and the iOS
  Smoke copy phase. E2E continues to load `e2e/index.js`; every normal product
  tier continues to load `index.js` and its product bridge.

### Android production-path smoke

Start Metro, install the debug-only smoke host, verify its generated capability
surface, and run the navigation flow:

```sh
npm run smoke:fixture:verify
npm start
cd android
ANDROID_HOME="$HOME/Library/Android/sdk" \
  JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
  ./gradlew :app:installSmokeDebug --no-configuration-cache
cd ..
adb reverse tcp:8081 tcp:8081
npm run smoke:manifest:verify
npm run smoke:android
```

### iOS production-path smoke

Install locked Pods, start Metro, then build and install the unsigned
simulator-only scheme:

```sh
npm run smoke:fixture:verify
npm run ios:pods
npm start
xcodebuild \
  -workspace ios/BirthdayAutopilot.xcworkspace \
  -scheme BirthdayAutopilotProductionSmoke \
  -configuration Smoke \
  -destination 'platform=iOS Simulator,id=<SIMULATOR_UDID>' \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
xcrun simctl install <SIMULATOR_UDID> \
  <DERIVED_DATA>/Build/Products/Smoke-iphonesimulator/BirthdayAutopilot.app
npm run smoke:ios
```

The production-path smoke is integration evidence only. It does not authorize
or prove OAuth, Contacts, Firebase/App Check, Gemini, notifications, MessageUI,
SMS, background scheduling, carrier delivery, account deletion, release
signing, or store distribution. Those remain covered by their platform tests
and signed/reviewed release evidence.

## Security boundary

The fixture cannot be selected with a production launch argument, URL, stored
preference, or remote flag:

- Android uses only `e2eDebug`, package
  `com.yashsomani.birthdayautopilot.e2e`, a visibly different label/icon, and
  `e2e/index.js`. The flavor has no release variant. Its manifest contains only
  Internet access for Metro, its own Activity, and an application host that
  registers only autolinked React Native UI packages. Cleartext is denied by
  default and allowed only for the literal `localhost` host used through
  `adb reverse`; the fixture pins React Native's dev-server preference to
  `localhost:8081`, while emulator gateways and remote hosts remain denied.
- iOS uses the `E2E` configuration and the same distinct `.e2e` bundle ID. It
  is unsigned, simulator-only, visibly named `Birthday Autopilot E2E`, uses an
  entitlement-free file, intentionally uses the simulator's generic icon so it
  cannot resemble the production icon, and loads `e2e/index.js`. Product native
  module bridges, Firebase/Google startup, MessageUI/reminder startup,
  background refresh, notification routing, and Gemini refresh are compiled or
  branched out.
- Lab, production, staging, and normal development entries remain `index.js`.
  The production bundle verifier rejects every fixture marker. The E2E bundle
  verifier requires fixture markers and rejects native product bridge markers.
- The merged-manifest verifier rejects every E2E service, provider, receiver,
  query, telephony feature, restricted permission, and permission other than
  `INTERNET`. It separately proves the production manifest has not acquired an
  E2E identity.

Synthetic launch values are bounded to `en`/`hi`, Android/iOS, and a Boolean
setup state. They never contain a name, phone number, birthday, token, account,
message credential, or service endpoint. Every screen carries a fixture banner
that says no account, reminder, or message action is performed.

## Executable coverage

The shared Maestro flows cover:

- cold launch and fresh in-memory setup;
- visibility and reachability of the fresh-setup primary action;
- Home, People, and Settings tab navigation;
- English and Hindi rendering through stable semantic identifiers;
- dark appearance, help/legal/about, and the privacy boundary;
- background/foreground restoration while the native recents privacy boundary
  executes;
- a separate large-text run where the primary actions must remain scrollable
  and actionable.

Maestro 2.6.1 is downloaded only from its official GitHub release URL and is
verified against SHA-256
`3440825f514f537c6a96bcf5de995780c2a4a7f83a43208fdc95d4f1fecfad3b`
before extraction. Reused installations must also match the reviewed full-tree
digest `a133cb76b324bfcb6d018eb320174da0ed9ff03c7a7fa2c32eede0010dc069a9`;
symlinks and non-regular entries are rejected. The installer never pipes
network content into a shell. See
the official [React Native integration](https://docs.maestro.dev/platform-support/react-native),
[launch arguments](https://docs.maestro.dev/api-reference/commands/launchapp),
and [JUnit artifact](https://docs.maestro.dev/troubleshooting/debug-output)
documentation.

### Android API 29

Start Metro, then install the isolated host and run both suites:

```sh
npm start
cd android && ./gradlew :app:installE2eDebug --no-configuration-cache
adb reverse tcp:8081 tcp:8081
cd ..
tools/run-mobile-e2e.sh android smoke
adb shell settings put system font_scale 1.3
tools/run-mobile-e2e.sh android large-text
```

CI uses a clean Pixel-class API 29 emulator and retains JUnit, screenshots,
command traces, and debug logs.

### iOS simulator

Install locked Pods, start Metro, build the unsigned fixture configuration,
install it on a booted simulator, and run both suites:

```sh
npm run ios:pods
npm start
xcodebuild \
  -workspace ios/BirthdayAutopilot.xcworkspace \
  -scheme BirthdayAutopilotE2E \
  -configuration E2E \
  -destination 'platform=iOS Simulator,id=<SIMULATOR_UDID>' \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  BIRTHDAY_IOS_SIMULATOR_COMPILE_SMOKE=YES build
xcrun simctl install <SIMULATOR_UDID> <DERIVED_DATA>/Build/Products/E2E-iphonesimulator/BirthdayAutopilot.app
tools/run-mobile-e2e.sh ios smoke
xcrun simctl ui <SIMULATOR_UDID> content_size accessibility-extra-extra-large
tools/run-mobile-e2e.sh ios large-text
```

The checked-in workflow pins the Xcode/runtime pair and retains the same
evidence as Android. No Apple signing credential is used.

## What this lane does not prove

This synthetic lane must never be cited as evidence for:

- Android SMS submission, sent/delivery callbacks, SIM selection, carrier
  behavior, Play installer/signing identity, OEM background behavior, or a
  physical-device soak;
- Google OAuth, People API freshness, Firebase, App Check/App Attest, Functions,
  Remote Config, Gemini, or production network/IAM behavior;
- iOS notification delivery, a real MessageUI composer, a user Send action,
  TestFlight/App Store behavior, or physical iPhone lifecycle behavior;
- TalkBack/VoiceOver focus order or pixel-level app-switcher redaction. Route
  focus is covered by component contracts, and both assistive technologies plus
  privacy snapshots still require retained physical-device evidence.

Those remain fail-closed release gates documented in the platform release
evidence and performance/accessibility checklists. A green simulator lane is UI
integration evidence, not production-service or message-delivery evidence.
