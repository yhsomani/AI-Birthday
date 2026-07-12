# Phase 0 evidence register

Evidence through: 2026-07-12

Status: active historical evidence register; gated implementation is now in progress, while activation, restricted SMS packaging/distribution, iOS production activation, store submission, and production-ready claims remain blocked by the unresolved gates below

Authority: this file records evidence only. It is subordinate to [PROJECT_ABOUT.md](../PROJECT_ABOUT.md) and cannot change its product, safety, privacy, or release contracts.

Historical audited blueprint SHA-256: `acf9457cb40ef3131686e7e3db39d667962f959777fb6f4d1dac3738e0f917f4`

The exact source artifact for that historical digest is not present in current tracked history or elsewhere in this repository and is therefore unavailable for reproduction. The digest is preserved append-only as provenance, not represented as a reproducible audit object, and must never be replaced by a later hash.

Directive supersession: the user's later explicit instructions to build Birthday Autopilot as a React Native application for Android and iOS supersede the earlier attached VS Code Developer Workbench objective. `PROJECT_ABOUT.md` is authoritative; the earlier objective is historical context only.

### 2026-07-12 cross-platform amendment lineage

| Item | SHA-256 | Evidence status |
| --- | --- | --- |
| Historical parent blueprint | `acf9457cb40ef3131686e7e3db39d667962f959777fb6f4d1dac3738e0f917f4` | Digest preserved; exact source unavailable for reproduction |
| Reviewed Android+iOS amendment input | `2f4359ecc709c12efdee09780302a28a8b6a47fd495b3869e45da11d4639acc4` | Independently reviewed; document/repository inconsistencies were found, so this hash was never accepted as production-ready |
| Companion Stitch manifest | `6856adf8627a471913810dc3df80a6d470eddd3a9a4b56c447bd68d9a6e8904e` | Non-authoritative UI inventory with 63 unique base IDs; corrected to use the app-owned **Review message** action, Android-only 400-day ledger wording, and truthful system-controlled MessageUI sender-line/transport limits; Stitch frames remain ungenerated and unaccepted |
| Resolved authoritative blueprint | `970b22599a1c9acf5a63802e20854ae20bededa9184c0c22d7b6ea3c29e1fbde` | Binding document corrections applied and consistency-checked; implementation and all external release evidence remain unresolved as separately recorded |

Evidence applicability is intentionally narrow: the retained Android package-neutral boundary hashes and runtime observations remain reproducible where rechecked; prior present-tense repository-state assertions are historical snapshots; no historical evidence proves the new iOS protected store, Google/People integration, reminders, MessageUI safety state machine, App Check, physical-iPhone behavior, App Store acceptance, or Android/iOS coexistence.

## 1. Historical repository snapshot before gated implementation

- At the time of the original snapshot, no production React Native, Android, Functions, or infrastructure scaffold existed. A deliberately quarantined disposable React Native/Android probe existed at `spikes/rn-native-boundary/` and still cannot be promoted.
- The retained Firebase file is at `app/google-services.json`; it is historical holding configuration, not a verified active environment.
- `DESIGN.md` is untracked and was not read, changed, or adopted as product authority.
- At the time of the original snapshot, the repository root had no `package.json`, production wrapper, or iOS scaffold, and dependency installation was authorized only inside the reviewed disposable spike.

Superseding repository-state observation on 2026-07-12: a gated cross-platform React Native implementation candidate, root package/lockfile, Android wrapper/app, native iOS companion sources, Firebase Functions control-plane sources, Firestore rules/indexes, and hosted legal/support/deletion surfaces now exist and pass their recorded local automated checks. Their presence does not satisfy any external Phase 0 gate, does not promote the disposable spike, and does not authorize activation, restricted packaging, distribution, or production-ready claims. Deployed per-tier Firebase/Google configuration and inspection evidence, production secrets, signed release evidence, physical Android SMS evidence, a full-Xcode iOS build, and physical-iPhone evidence remain absent.

## 2. Local toolchain evidence

| Check | Evidence | Status |
| --- | --- | --- |
| Required Node | NVM installation `/Users/yashsomani/.nvm/versions/node/v24.18.0/bin/node` reports `v24.18.0` | Pass |
| Required npm | The npm bundled with that exact NVM installation reports `11.6.0` | Pass |
| Default interactive shell | Resolves Node `v20.19.6` and npm `11.13.0` until `nvm use` | Blocked until project shell activation |
| Other shell PATH | A non-interactive shell may resolve Homebrew Node `v25.9.0`/npm `11.12.1` | Blocked unless PATH is pinned |
| Project Node pin | `.nvmrc` now pins `24.18.0` | Pass |
| Java | Homebrew OpenJDK `21.0.11` exists at `/opt/homebrew/opt/openjdk@21`; `JAVA_HOME` is unset, interactive PATH may resolve JDK 25, and other contexts may hit Apple's missing-runtime stub | Installed; deterministic JDK 21 wiring pending |
| Android SDK | Exists at `/Users/yashsomani/Library/Android/sdk`; `adb` is `37.0.0` | Installed; environment wiring pending |
| SDK command-line tools | Google command-line tools `20.0` are installed at `cmdline-tools/latest`; `sdkmanager` and `avdmanager` execute, but they warn that they understand SDK XML through version 3 while Android Studio has written version-4 metadata | Partial; reconcile CLI/Studio metadata compatibility before release |
| Latest runtime/research SDK | Android 17/API 37 platform and Build Tools `37.0.0` are installed | Research/runtime coverage only; the supported RN 0.86 production baseline targets API 36 |
| SDK platforms | Platforms 34, 35, 36, 36.1, and 37.0 are installed; NDK `27.1.12297006` and CMake `3.22.1` are present | Partial; future RN/AGP compatibility must be pinned |
| Emulator coverage | Complete ARM64 Google Play images exist for API 29, API 36.1, and API 37.0; API 37 is the 16 KB page-size variant. `Birthday_API_29` and `Birthday_API_37_16K` AVDs passed headless boundary boot smokes; the pre-existing API 36.1 AVD was not booted in this run. API 30–35 images and the full release matrix remain absent | Boundary pass; release matrix still blocked |
| adb PATH | SDK platform-tools supplies adb 37, while the interactive shell may select Homebrew adb 36 | Pending PATH ordering |
| Physical SMS coverage | No physical single/dual-SIM device or carrier evidence is recorded | Blocked |
| Gradle | In the historical snapshot no production wrapper existed. The disposable probe pinned wrapper `9.3.1`; the later gated implementation candidate now has its own wrapper | Probe pass; candidate wrapper presence is not release evidence |
| Host capacity | Apple M4, 16 GB RAM; roughly 30–34 GiB free and data volume about 93% used during final spike runs | Current spike completed; volatile capacity is too tight for the full local matrix without cleanup |

Reproducible check:

```sh
export NVM_DIR="$HOME/.nvm"
. "$NVM_DIR/nvm.sh"
nvm use
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
bash phase0/check-toolchain.sh
```

The earlier `npm install --force` failure needs no npm downgrade or force bypass: the exact Node 24.18.0 installation already bundles npm 11.6.0. Run `nvm use` first. The production candidate now has a reviewed root lockfile, so use `npm ci` at the repository root only after the pinned toolchain preflight succeeds; never use `--force`. The disposable probe remains independently quarantined with its own lockfile.

Current official setup references:

- Android 17/API 37 SDK setup: https://developer.android.com/about/versions/17/setup-sdk
- Google Play target API requirements: https://developer.android.com/google/play/requirements/target-sdk

The binding RN 0.86 production lane now uses compileSdk/targetSdk 36 with AGP 8.12. API 37/AGP 9.1.1 remains research and latest-runtime compatibility evidence only. Before release, recheck Google's target requirement; if it exceeds 36, release blocks until a supported production-stack upgrade passes the full matrix.

Latest verifier result with Node 24.18.0, npm 11.6.0, JDK 21, and explicit SDK paths:

- passed the `.nvmrc`, Node, npm, `JAVA_HOME`/PATH Java, SDK-root parity, platform-tools 37, command-line-tool execution, complete API 37 platform, complete Build Tools 37, complete ARM64 API 29/36/37 images, and the two named boundary AVD-profile checks;
- warned about the command-line-tool SDK XML schema mismatch, missing API 30–35 images, the then-absent production Gradle wrapper, and low free-space headroom. The later production candidate now includes a wrapper; the remaining warnings and release-matrix gaps are not cleared by that source addition.

Installation provenance:

- downloaded Google's `commandlinetools-mac-14742923_latest.zip` over HTTPS from the official Android download host;
- matched the official page's published digest `cc27cca4b84bfdbc7df17e3d0a01d0c640d8ee71` and computed SHA-256 `ed304c5ede3718541e4f978e4ae870a4d853db74af6c16d920588d48523b9dee`;
- passed `unzip -t` with no archive errors before installation;
- installed Build Tools `37.0.0` through `sdkmanager` and verified its `source.properties` revision;
- installed `system-images;android-29;google_apis_playstore;arm64-v8a` revision 9 and `system-images;android-37.0;google_apis_playstore_ps16k;arm64-v8a` revision 6 through `sdkmanager`;
- verified both images have registered `package.xml`, matching `source.properties`, ARM64 ABI metadata, and nonempty `system.img` payloads before creating their AVDs.

This network intercept requires Java tools to use the macOS system keychain for repository access:

```sh
export JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=/Library/Keychains/System.keychain'
```

Use that only for Java tools needing the intercepted network path; do not silently change application runtime trust behavior.

### Boundary runtime boot evidence

| AVD | Direct runtime evidence | Result |
| --- | --- | --- |
| `Birthday_API_29` | Boot completed; SDK `29`; ABI `arm64-v8a`; kernel page size `4096`; `com.android.vending` resolves to the system `Phonesky64.apk` | Pass |
| `Birthday_API_37_16K` | Boot completed; SDK `37`; ABI `arm64-v8a`; kernel page size `16384`; `com.android.vending` resolves to the product `Phonesky.apk` | Pass |

Both were clean headless boots with snapshots disabled and were shut down after inspection. The generated AVD configs say `PlayStore.enabled=no` even though their selected images are Google Play images; direct package inspection proves the Play Store payload exists, but an actual Google-account sign-in remains an application-level spike. These boot facts alone prove image/runtime viability only. Section 5 separately proves the narrow disposable React Native/TurboModule/Room/WorkManager boundary; neither section proves Google identity, Contacts, Firebase, Gemini, telephony, real SMS, installer allowlisting, or physical-device reliability.

## 3. Retained Firebase/package evidence

### Historical configuration evidence retained from Git history

- The former tracked `app/google-services.json` was valid JSON and had SHA-256 `a890656e5723da63f187757fce398f645ff21d99d080488c56839748dda5473f`, matching the historical blueprint. The working-tree file is now deliberately absent so it cannot be selected or copied into the new application; Git history is the evidence source for the facts below.
- The historical file targeted Firebase project `relateai-birthday-ysomani` and package `com.aistudio.relateai.qxtjrk`.
- It contained one matching Android OAuth entry with SHA-1-shaped certificate metadata, one Web OAuth entry, and one public Firebase configuration API-key entry. No credential values were copied into this register.
- It contained no service-account key, OAuth client secret, refresh token, or private signing key.
- Git history shows package divergence: the legacy Kotlin app used `com.aistudio.relateai.qxtjrk`, while a later React Native migration declared `com.relateai.app`. History does not establish a permanent application ID.

### Not proven locally

- Firebase/Google Cloud ownership, IAM, billing, budget alerts, quota owners, or incident contacts.
- Which tier owns the retained project, or separate dev/staging/production projects.
- Whether either OAuth client is current and enabled, which certificate the retained SHA-1 represents, or any release/Play SHA-1 and App Check SHA-256 registration.
- Generated `default_web_client_id`, Firebase Auth Google provider, People API, OAuth consent/verification, or `contacts.readonly` behavior.
- Live Vertex-backed Firebase AI Logic model/location/billing/quotas/monitoring, enforced App Check and replay protection, deployed callable Functions, deployed server-only Firestore rules/indexes/TTL/PITR controls, deployed Hosting deletion route, or production Secret Manager values. Local candidate implementations and automated contracts for Functions, Firestore configuration, and Hosting now exist; this evidence register has no proof that the corresponding cloud resources are provisioned or deployed correctly.
- Android/API restrictions on the public Firebase configuration key.
- Absence of enabled Realtime Database, Cloud Storage, FCM, Analytics, ads, or unapproved monitoring products in the live project.

Conclusion: the Phase 0 Firebase gate is not ready. The binding production Android/iOS identifier is now `com.yashsomani.birthdayautopilot`, but the retained project's tier, signing certificates, and live console controls remain unverified. Android must use one matching file per used flavor at `android/app/src/<environment>/google-services.json`; iOS must use configuration-selected `ios/Config/<environment>/GoogleService-Info.plist`. The historical holding file never becomes active configuration.

### Current local control-plane evidence

On 2026-07-12, the pinned Node 24.18.0/npm 11.6.0 and JDK 21 toolchains ran `npm run backend:test:emulator` against the isolated `demo-birthday-autopilot` project. Firebase Firestore Emulator 1.21.0 executed two test files and 17 tests successfully. The suite directly proved deny-all unauthenticated and authenticated client rules at every tested ledger depth and exercised the candidate's server-side sender fencing, TEST/Birthday separation, transfer/deletion races, signed-out receipt completion, recursive cleanup/repair paging, and crash recovery. This is local emulator evidence only: it does not prove deployed rules, IAM, App Check enforcement, production secrets, tier configuration, quotas, logging controls, retention/TTL activation, or regional reliability.

## 4. Unattended SEND_SMS distribution evidence

### Binding platform fact

`SEND_SMS` is dangerous and hard restricted. The installer of record must allowlist it before Android can grant it; allowlisting does not itself grant the runtime permission, and the user cannot manually create the restricted-permission allowlist.

### Candidate channels

| Candidate | Current evidence | Decision status |
| --- | --- | --- |
| Public Google Play, Device automation exception | Google's current exception table expressly allows `SEND_SMS` when cross-area device automation based on user-set triggers is the app's critical core function and no alternative works. Approval is case-by-case and requires the declaration/review package | Credible consumer-first experiment; written approval required |
| Managed Google Play private app on a fully managed device via Microsoft Intune or Android Management API | Official enterprise APIs support managed app deployment and per-app permission grant policy. This is the strongest named managed candidate, but EMM auto-grant alone does not prove the separate installer restricted-permission allowlist | Credible enterprise fallback; exact signed artifact/device proof required |
| Ordinary browser/file-manager APK sideload | “Install unknown apps” does not prove that the installer allowlists a hard-restricted permission. No named installer/vendor commitment exists | Not a viable release candidate yet |
| Default SMS role | Can provide another permission route only if the app is a genuine default SMS handler | Rejected by current product scope |

Required proof before choosing an unattended channel:

1. Written Google Play Device automation approval for the exact package/build, or written installer/EMM confirmation of hard-restricted `SEND_SMS` allowlisting.
2. Production-signed install and update evidence showing installer of record, restricted-permission allowlist, runtime grant, Settings state, and a real unattended physical-device SMS.
3. API/OEM, single/dual-SIM, prepaid/postpaid, update, rollback, reinstall, permission-reset, and revocation results.
4. SMS declaration, demo video, reviewer access, privacy/Data Safety, carrier-cost, and external-copy disclosures.

Official sources:

- Android `SEND_SMS`: https://developer.android.com/reference/android/Manifest.permission#SEND_SMS
- Android restricted permissions: https://source.android.com/docs/core/permissions/runtime_perms
- Package installer allowlisting: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setWhitelistedRestrictedPermissions(java.util.Set)
- Google Play SMS/Call Log policy: https://support.google.com/googleplay/android-developer/answer/10208820?hl=en
- Google Play permission declaration: https://support.google.com/googleplay/android-developer/answer/9214102?hl=en-EN
- Android Management API policies: https://developers.google.com/android/management/reference/rest/v1/enterprises.policies
- Managed Google Play private apps: https://support.google.com/googleplay/android-developer/answer/9874937?hl=en
- Microsoft Intune managed Google Play: https://learn.microsoft.com/en-us/intune/app-management/deployment/add-managed-google-play
- Microsoft Intune Android permission configuration: https://learn.microsoft.com/en-us/intune/app-management/configuration/configure-managed-android

## 5. Disposable React Native/native-boundary spike

Charter: [RN_NATIVE_BOUNDARY_SPIKE.md](RN_NATIVE_BOUNDARY_SPIKE.md)

### Scope and quarantine

- The accepted source is retained at `spikes/rn-native-boundary/` under the permanent throwaway package `dev.phase0.disposable.boundaryprobe` and display name `DELETE ME — PHASE 0`.
- It is not a Birthday Autopilot scaffold. It has no production package, Firebase configuration/plugin, Google identity, Contacts, Gemini, SMS, telephony, real personal data, or production signing material.
- The only key file is the stock Android debug keystore. The final control APK verifies with one V2 debug signer whose certificate SHA-256 is `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.
- The first `/private/tmp` reconstruction was unexpectedly purged. Its APK hash and one manual API 29 observation are superseded historical observations, not accepted results. Every result below was rebuilt or rerun from the retained quarantine.
- `node_modules`, Gradle state, CMake/build outputs, reports, and APKs are ignored. Production import/copy/cherry-pick is forbidden by `DO_NOT_PROMOTE.md` and `spikes/README.md`.

### Pinned lanes

| Item | Stock control | API 37 experiment |
| --- | --- | --- |
| React Native / React | `0.86.0` / `19.2.3` | Same |
| Node / npm / JDK | `24.18.0` / `11.6.0` / `21.0.11` | Same |
| Gradle / Kotlin / KSP | `9.3.1` / `2.1.20` / `2.1.20-2.0.1` | Same |
| Android Gradle Plugin | Template `8.12.0` | `9.1.1` with `android.builtInKotlin=false` and `android.newDsl=false` |
| min / compile / target | `29` / `36` / `36` | `29` / `37` / `37` |
| Build Tools / NDK / ABI | `36.0.0` / `27.1.12297006` / ARM64 only | Same |
| Persistence / work | Room `2.8.4` / WorkManager `2.11.2` | Same |
| Runtime architecture | Hermes and New Architecture required | Same |

The bootstrap used Community CLI `20.2.0`; the generated/retained project resolves CLI packages `20.1.0`. React Native `0.86.0` is the stable line used for this dated evidence. AGP 8.12 officially supports through API 36; AGP 9.1.1 supports API 37 and defaults to built-in Kotlin/new DSL. Therefore the target-37 lane is compatibility evidence only, not an official React Native baseline.

Official references:

- React Native 0.86: https://reactnative.dev/blog/2026/06/11/react-native-0.86
- React Native versions: https://reactnative.dev/versions.html
- Turbo Native Modules on Android: https://reactnative.dev/docs/turbo-native-modules-android
- AGP 8.12 compatibility: https://developer.android.com/build/releases/agp-8-12-0-release-notes
- AGP 9.1 compatibility: https://developer.android.com/build/releases/agp-9-1-0-release-notes
- AGP built-in Kotlin migration: https://developer.android.com/build/migrate-to-built-in-kotlin
- Android 16 KB page-size guidance: https://developer.android.com/guide/practices/page-sizes

### Accepted source and artifact identities

| Evidence | SHA-256 |
| --- | --- |
| Retained source aggregate from `scripts/source-hash.sh` | `734217ba4f764074acdb43288c7f6e508d9ac8a11e1affb8d2ce5189d47d894d` |
| `package-lock.json` | `2f6bc0d21e4a8ca65de919c456004eabd1c663d2cabd2c245f085ec94b1a8ae3` |
| Stock target-36 `app-probe.apk` | `20dc9ec97ecc846ab78252c56f99c51942c35184ad5ac5586b311d26108be256` |
| Isolated target-37/AGP-9.1.1 `app-probe.apk` | `4c9dec1384e88c8405a08b7c54ed95e753e70d2ad8b4602758cc2662288e4ba8` |
| Retained target-37 configuration patch | `064cb0fecd45659796995d5e8a34d65e0fca11668e64ee83d803d67ad9adda44` |

Run `spikes/rn-native-boundary/scripts/source-hash.sh` from any directory to reproduce the retained-source aggregate. It hashes the sorted per-file SHA-256 listing with stable probe-relative paths and excludes declared dependency/generated/build state. The isolated experiment existed at `/private/tmp/birthday-api37-agp911.szFoli` when recorded. Its reviewed configuration delta is retained as `phase0/rn-native-boundary-agp91.patch`; `patch --dry-run -p1` passes against the stock quarantine. Its changed configuration hashes were:

- root `android/build.gradle`: `a4e31f02efb82ece15698d48bfe10a84100a56656ddb83a137c9ad23299ec272`;
- app `android/app/build.gradle`: `c0dd6c50094dc80eb2315002899a296e4b6b80138d1c035319dd507a6ef3ed28`;
- `android/gradle.properties`: `a8ed8ef0333fc6267d673891a26028cf6e2b163b5d741c28e0a7c239e5808ddf`;
- RN plugin version catalog: `81c2890acb90b955bf945e43306764532174520d15dc7c0d87314ce68f835ffa`.

The target-37 APK is not retained in the repository. Its hash identifies the tested temporary artifact but does not make a future build byte-for-byte reproducible; the retained patch makes the configuration and build procedure reviewable. Recreate an isolated configuration only after `npm ci` has populated the stock probe:

```sh
SOURCE="/Users/yashsomani/Desktop/Android Project/AI-Birthday/spikes/rn-native-boundary"
EXPERIMENT="$(mktemp -d /private/tmp/birthday-api37-agp911.XXXXXX)"
rsync -a --exclude '/android/.gradle/' --exclude '/android/build/' \
  --exclude '/android/app/build/' --exclude '/android/app/.cxx/' \
  --exclude '/node_modules/@react-native/gradle-plugin/.gradle/' \
  --exclude '/node_modules/@react-native/gradle-plugin/react-native-gradle-plugin/build/' \
  --exclude '/node_modules/@react-native/gradle-plugin/settings-plugin/build/' \
  --exclude '/node_modules/@react-native/gradle-plugin/shared/build/' \
  --exclude '/node_modules/@react-native/gradle-plugin/shared-testutil/build/' \
  "$SOURCE/" "$EXPERIMENT/"
(cd "$EXPERIMENT" && patch -p1 < "$SOURCE/../../phase0/rn-native-boundary-agp91.patch")
```

The accepted isolated invocation was:

```sh
cd /private/tmp/birthday-api37-agp911.szFoli/android
./gradlew :app:generateCodegenArtifactsFromSchema :app:lintProbe :app:assembleProbe --no-daemon --stacktrace

cd "/Users/yashsomani/Desktop/Android Project/AI-Birthday/spikes/rn-native-boundary"
./scripts/check-apk.sh \
  /private/tmp/birthday-api37-agp911.szFoli/android/app/build/outputs/apk/probe/app-probe.apk \
  37
PHASE0_PROBE_APK=/private/tmp/birthday-api37-agp911.szFoli/android/app/build/outputs/apk/probe/app-probe.apk \
  ./scripts/acceptance.sh emulator-5558 37 16384 10
```

The Gradle command used the same explicit Node 24.18.0, JDK 21, Android SDK, and Java keychain trust environment recorded in the stock README.

### Build, static, and dependency evidence

- `npm run typecheck`, `npm run lint`, and `npm test -- --runInBand` pass; Jest reports one suite/one test passed.
- The stock lane passes `generateCodegenArtifactsFromSchema`, `testProbeUnitTest`, `lintProbe`, and `assembleProbe` under the exact Node/npm/JDK/SDK environment. The generated Java TurboModule spec and JNI schema/artifacts exist under ignored build output.
- Kotlin unit tests pass for the revision bounds. Atomic Room compare-and-set, invalid/stale rejection, worker execution, and process-local TurboModule absence are asserted by the runtime state machine.
- The current control APK is a non-debuggable, JS-bundled, debug-signed `probe` APK. No AAB exists in its build outputs.
- Manifest queries prove package `dev.phase0.disposable.boundaryprobe`, minSdk `29`, targetSdk `36`, and exactly these five permissions: `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, and the app-scoped dynamic-receiver permission. It has no `INTERNET`, SMS, Contacts, account, telephony, exact-alarm, location, camera, microphone, calendar, call-log, or storage permission.
- Build Tools 37 `zipalign -c -P 16 -v 4` passes. The APK contains exactly ten ARM64 libraries, no other ABI, and all 31 inspected ELF `LOAD` segments are ELF64/AArch64 with alignment at least `0x4000`.
- Resolved runtime dependencies contain Room `2.8.4` and WorkManager `2.11.2`; WorkManager's transitive Room `2.7.0` request resolves to `2.8.4`. Package/build/lock scans find no Firebase, Google Services, auth, People, Gemini, or app-authored telephony dependency/plugin. The APK check also scans decoded resources, DEX packages, archive names, and Hermes bundle strings for known provider classes/config keys and secret signatures. A recursive source filename/content scan finds no Firebase config, service-account file, private-key block, OAuth refresh token, client email, or Firebase-style API key.
- `@react-native/jest-preset@0.86.0` was added because the generated template omitted the preset required by its Jest configuration. KSP `2.3.9` was rejected after a Kotlin-API `NoSuchMethodError`; the matching published `2.1.20-2.0.1` pin passes. A debug-derived probe accidentally merged `SYSTEM_ALERT_WINDOW`; making the accepted `probe` release-derived removed it and the exact allowlist prevents regression.
- Native enqueue now waits for WorkManager's `Operation` result before JavaScript exposes `PHASE0_ARMED_REVISION_1`; the harness separately waits until the native JobScheduler record is visible.
- The persistent SDK XML schema-version warning and Gradle deprecation warning remain. Neither was hidden or counted as a release-ready toolchain result.

### Clean-install process-death runtime matrix

| APK lane | Runtime | Runs | Exact result |
| --- | --- | --- | --- |
| Stock target 36 / AGP 8.12 | API 29, ARM64, 4 KB pages | 10/10 | Pass; non-namespaced job `0`, one forced run each |
| Stock target 36 / AGP 8.12 | API 37, ARM64, 16 KB pages | 10/10 | Pass; namespace `androidx.work.systemjobscheduler`, job `0`, two forced runs each after platform rescheduling |
| Experimental target 37 / AGP 9.1.1 | API 37, ARM64, 16 KB pages | 10/10 | Pass in a fresh final series; namespaced job `0`, two forced runs each |

Every counted run:

1. clears/uninstalls the disposable package, verifies no package path remains, then installs without `-r`;
2. launches without Metro and renders exact SDK/page-size/ARM64 facts plus Hermes/New Architecture;
3. proves invalid `-1` does not mutate revision `0`, commits TurboModule CAS `0 -> 1`, waits for enqueue persistence, and renders `PHASE0_ARMED_REVISION_1`;
4. backgrounds and demotes the activity, records its PID, runs bounded `am kill --user 0` retries (never force-stop), verifies two consecutive absent-process samples, and discovers the exact WorkManager service namespace/job ID;
5. forces native work, requires a distinct worker PID, follows the single matching replacement job if its ID changes, and requires three consecutive samples with no matching WorkManager job before React Native is relaunched;
6. renders `PHASE0_PASS` only for revision `2`, writer `WORKER`, and `worker saw TurboModule: false`, then rejects stale revision `0` without mutation;
7. derives every observed app PID from the cleared process-start log window, then scans relevant time-bounded main/crash logs for fatal Java/native/linker/ANR signatures and secret, personal-data, business-payload, or Firebase terms before removing the package.

On API 37, the first forced job is consistently canceled/rescheduled while WorkManager binds after `am kill`; forcing the exact replacement completes the worker. An earlier experimental series passed 9/10, then correctly failed its tenth run when WorkManager replaced job `0` with job `2`. That series was not accepted. The harness was hardened to follow one matching namespace/service across job-ID changes, and a wholly new ten-run target-37 series passed.

`worker saw TurboModule: false` proves that `NativeBoundaryProbe` was not instantiated before `ProbeWorker.doWork`. Because `MainApplication.onCreate` calls React Native's application entry point in every process, this spike does not prove that no JavaScript-runtime object is allocated during worker-process bootstrap; no probe JavaScript is intentionally invoked before the worker.

### API 37/AGP 9.1.1 experiment outcome

- The RN 0.86 Gradle plugin compiled and loaded against AGP 9.1.1 using the documented temporary opt-outs `android.builtInKotlin=false` and `android.newDsl=false`.
- Initial configuration correctly failed because AGP 9 rejects `getDefaultProguardFile("proguard-android.txt")`. The isolated lane changed only to `proguard-android-optimize.txt`; minification remains disabled.
- AGP 9.1.1 did not generate `testProbeUnitTest` for the release-derived custom `probe` variant. This is recorded as an experiment limitation, not reported as a unit-test pass. The supported stock lane's Kotlin unit tests remain the unit evidence.
- Available target-37 `generateCodegenArtifactsFromSchema`, `lintProbe`, and `assembleProbe` tasks pass. Static inspection proves truthful targetSdk `37`, and the final 10/10 API 37/16 KB runtime series passes.
- This does not establish an official or durable RN 0.86 production baseline. The compatibility opt-outs are deprecated for removal in AGP 10, and the app consumes prebuilt React Native artifacts rather than rebuilding ReactAndroid itself under AGP 9.

### Storage and scheduling boundary

- Room contains one synthetic integer revision row only and is deliberately unencrypted. This is not a production storage selection and may never hold Contacts, birthday, phone, message, token, prompt, or identity data.
- WorkManager `Data` contains only the bounded expected revision integer; its unique name/tag are fixed non-personal constants. No personal data is placed in JobScheduler extras, names, tags, logs, or device-protected storage.
- This spike starts after normal user unlock. WorkManager cannot be assumed to execute before first unlock, so production must reconcile after unlock rather than moving PII into device-protected storage.
- Production encryption, migration/corruption recovery, retention/deletion, direct-boot behavior, and 10,000-contact performance remain separate gates. Room 3 plus Keystore-backed field encryption and Room 2.8.4 plus SQLCipher are candidates for a later evidence spike, not accepted choices here.

This pass proves only the narrow package-neutral React Native/native persistence/background boundary. It does not prove Google sign-in, People/Contacts, Firebase/Auth/App Check, Gemini/AI Logic, cloud coordination, encrypted production storage, account deletion, SMS permission/distribution, real telephony, exact delivery, physical devices/OEMs, nine-day reliability, production signing, accessibility/UI quality, privacy/legal readiness, or any Section 18 release gate.

## 6. Gate status and owner decisions

| Gate | Current state | What unblocks it |
| --- | --- | --- |
| Distribution | Evidence narrowed to Play Device automation or fully managed Intune/AMAPI; neither is proven | Owner channel direction plus written/physical proof |
| Approval model | Accepted in the authoritative product contract and implemented in the candidate; signed-artifact/physical behavior remains unproven | Release evidence for immutable recipient/number/message/SIM/window and no live generation at send |
| Identity | Accepted in the authoritative product contract and implemented behind native SDK boundaries; real tier integration remains unproven | Provisioned OAuth/Firebase/People/App Check tiers plus Android and iOS integration evidence |
| Firebase | Historical config inspected; live console state unknown | Package/tier choice, certificate inventory, console access and verification |
| Coordination | Independently design-audited and implemented as a locally tested Firebase Functions/Firestore candidate; no deployed tier or production App Check/integration proof exists | Provisioned Firebase environments, production HMAC secrets, emulator/deployed integration evidence, and cloud-control inspection |
| Background/SMS | Stock target-36 boundary passes 10/10 on API 29/4 KB and API 37/16 KB; experimental target-37 also passes 10/10 on API 37/16 KB. Intermediate emulators and all physical SMS evidence are absent | Named channel, remaining API/OEM matrix, physical devices/SIMs, nine-day soak |
| Gemini provider | Not verified | Vertex location/model/billing/quota/monitoring console evidence |
| Legal/carrier | No launch country or carrier certification accepted | Launch-country choice and counsel/carrier matrix |
| Storage/account/UX | Production candidate storage, lifecycle, deletion, setup, and UX paths now have automated source/JVM/Jest/static evidence; direct-boot, accessibility, and end-to-end release behavior still need the prescribed physical/configured matrix | Signed configured builds, destructive-race and device matrix, accessibility evidence, performance/soak runs, and release review |
| iOS Companion | Native protected-store, reminder, auth/People boundary, MessageUI, reset, deletion, and cross-platform coordination candidates now exist and pass Swift parsing plus automated contract tests; a full Xcode build and configured physical-iPhone integration remain unavailable | Full Xcode 26.5/CocoaPods build, tier config, signing, Google/Firebase/App Attest/MessageUI device matrix, accessibility/privacy metadata, login-services review, and App Store evidence |

Recorded decisions and remaining release recommendations:

1. Consumer-first: pursue the Google Play Device automation exception as the primary evidence experiment; keep fully managed Intune/AMAPI as an enterprise fallback. Do not treat generic sideloading as a fallback.
2. Accepted product decision: SDK-managed internal OAuth/Firebase/App Check credentials are allowed; user-managed tokens and JavaScript exposure are not. Literal zero-token operation is incompatible with Google Contacts, Firebase, and Gemini.
3. Remaining recommendation only: consider India-first for the initial English/Hindi, carrier, privacy, and legal matrix; the authoritative contract does not select a launch country.
4. Accepted product decision: the permanent Android application ID and iOS bundle identifier are `com.yashsomani.birthdayautopilot`; the divergent historical packages remain inactive.

Items 2 and 4 are already binding through `PROJECT_ABOUT.md`. Items 1 and 3 remain evidence/release recommendations, not launch authorization.
