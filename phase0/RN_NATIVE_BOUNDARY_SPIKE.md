# React Native/native boundary spike charter

Evidence through: 2026-07-12

Status: acceptance evidence complete; disposable Phase 0 work, not a production scaffold

Authority: this charter is subordinate to `PROJECT_ABOUT.md`. The spike may prove compatibility facts, but it cannot accept a product gate, select the permanent application ID, configure a Firebase tier, or become production code without a fresh review against the audited blueprint.

## Purpose

Prove the smallest package-neutral slice that is currently possible without owner or console decisions:

1. React Native `0.86.0`, React `19.2.3`, Hermes, and the New Architecture build with the pinned Node/npm and local Android toolchain.
2. A code-generated TypeScript TurboModule reaches Kotlin, performs an atomic nonsensitive Room transition, and enqueues native WorkManager without exposing credentials or provider objects.
3. A native worker resumes that transition after the original React Native process dies without instantiating `NativeBoundaryProbe` or executing probe JavaScript before `doWork`. React Native's application bootstrap is not instrumented deeply enough to claim that no JavaScript runtime object is allocated.
4. The same bundled probe artifact installs and launches on the API 29/4 KB and API 37/16 KB ARM64 boundary AVDs and passes 16 KB native-library alignment checks.
5. A stock React Native 0.86/API 36 control is measured separately from the API 37/AGP 9 experiment; an unsupported version combination is never represented as an official production baseline.

React Native `0.86` is the current stable line as of this date. `0.87` is a release candidate and is excluded. Official references:

- https://reactnative.dev/blog/2026/06/11/react-native-0.86
- https://reactnative.dev/versions.html
- https://reactnative.dev/docs/turbo-native-modules-introduction
- https://reactnative.dev/docs/turbo-native-modules-android
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/build/releases/agp-8-12-0-release-notes
- https://developer.android.com/build/releases/agp-9-1-0-release-notes
- https://developer.android.com/build/releases/agp-9-0-0-release-notes

## Isolation contract

- Quarantined location: `spikes/rn-native-boundary/`; reviewed source, scripts, and the npm lockfile are retained for reproducibility, while `node_modules`, Gradle state, native intermediates, reports, and built artifacts remain ignored.
- The first scratch copy under `/private/tmp` was unexpectedly purged. No result from that deleted tree is acceptance evidence; the quarantined copy was rebuilt and every accepted check was rerun.
- Display name: `DELETE ME — PHASE 0`.
- Throwaway application ID and namespace: `dev.phase0.disposable.boundaryprobe`.
- The package is deliberately not any historical, recommended, or future production package.
- No `google-services.json`, Firebase plugin, OAuth client, API key, production signing key, service-account material, Contacts data, phone number, birthday, message, prompt, or personal fixture is allowed. The only key file is the stock, publicly known Android debug keystore used solely to sign the disposable APK.
- Do not declare or request `SEND_SMS`; do not call `SmsManager`.
- Do not add account access, app-authored network calls, Firebase, People, Gemini, Room encryption, custom product receivers, SMS scheduling, or distribution behavior merely to make the spike look application-like. WorkManager's own components and `ACCESS_NETWORK_STATE` are permitted and inspected.
- Use only synthetic numeric probe input and non-sensitive runtime facts.
- Root `app/google-services.json` remains a historical holding file and must not be copied.
- The spike must carry a visible disposable warning and may be deleted without a migration path.
- Android acceptance uses only a debug-signed, non-debuggable, JS-bundled `probe` APK. No release application variant, AAB, publish, or upload output is accepted or invoked. The CLI-generated iOS tree is inert and outside this Android-only charter.
- A build assertion requires the `dev.phase0.disposable.` prefix and rejects adjacent project/app `google-services.json` files; a separate retained-source scan covers the quarantine recursively.
- No source file is promoted into the future production scaffold by copy. Any later reuse requires an explicit contract-by-contract review and reimplementation decision.

## Pinned candidate matrix

| Item | Spike pin |
| --- | --- |
| React Native | `0.86.0` |
| React | `19.2.3` |
| Community CLI/template | Bootstrap CLI `20.2.0`; retained CLI dependencies `20.1.0`; React Native template `0.86.0` |
| Node/npm | `24.18.0` / `11.6.0` |
| JDK | Local JDK `21.0.11`; compatibility must be proven because React Native documentation generally recommends JDK 17 |
| Android minSdk | `29` |
| Stock control | Template AGP `8.12.0`, Gradle `9.3.1`, Kotlin `2.1.20`, compile/target `36`, Build Tools `36.0.0` |
| API 37 experiment | AGP `9.1.1`, Gradle `9.3.1`, compile/target `37`; built-in-Kotlin migration or an explicitly recorded compatibility mode is required |
| Kotlin symbol processing | KSP `2.1.20-2.0.1`, matched to template Kotlin `2.1.20` |
| Synthetic persistence | Room `2.8.4`; intentionally unencrypted and forbidden from storing PII |
| Native background work | WorkManager `2.11.2`; only a bounded integer revision in `Data` |
| Architecture | New Architecture required; no fallback run |
| JavaScript engine | Hermes required |
| Test runtimes | `Birthday_API_29` and `Birthday_API_37_16K` |

The generated template was built unchanged in the original bootstrap scratch as a historical control, then the reconstructed quarantine was built and tested with the stock React Native Android versions before the native slice was accepted. React Native 0.86 does not publish an official AGP 9/API 37 compatibility claim: AGP 8.12 officially supports through API 36, while AGP 9.1.1 supports API 37 but changes Kotlin integration. The API 37 lane is therefore a separate experiment, not a routine override. Every accepted configuration change and material failure is recorded in the evidence register.

## Typed boundary

The single `NativeBoundaryProbe` TurboModule exposes only:

- `getEnvironment()`: schema version, SDK level, page size, primary ABI, and native New Architecture state;
- `getSnapshot()`: integer revision, last writer (`NONE`, `TURBO_MODULE`, or `WORKER`), and whether the TurboModule was already seen in the worker process;
- `compareAndIncrement(expectedRevision)`: one atomic Room compare-and-set transition;
- `enqueueDelayedIncrement(expectedRevision)`: one uniquely named, delayed WorkManager request.

The React Native screen adds whether Hermes is present from the JavaScript runtime. The module accepts only bounded integer revisions, rejects invalid/stale input without mutation, and never accepts tokens, contact data, arbitrary strings, or business payloads. The one-row Room schema contains only fixed ID `1`, revision, writer enum, and the worker's process-local TurboModule-seen flag.

## Acceptance evidence

### Source and dependency

- Exact versions, npm lockfile, retained-source aggregate hash, and APK hashes are recorded as evidence.
- `package.json` pins the supported Node/npm engine and Codegen configuration.
- The quarantine contains `DO_NOT_PROMOTE.md`, has no import path from production code, and keeps generated/dependency state ignored.
- A scan finds no production secret-like material, production application/provider identifier, or copied Firebase configuration. Product-name warnings remain intentionally visible; the stock disposable debug keystore is explicitly permitted and its debug certificate is recorded.
- Android manifest inspection finds no `SEND_SMS`, contacts, account, telephony, exact-alarm, background-location, or storage permission.
- Dependency inspection finds no Firebase, Google Services, auth, People, Gemini, or telephony dependency/plugin.

### Static and unit

- TypeScript strict checking, linting, and Jest pass.
- `generateCodegenArtifactsFromSchema` succeeds and generates the expected Android spec.
- Kotlin unit tests cover the bounded revision policy. Atomic compare-and-set, stale/invalid rejection, worker rescheduling, and process-state capture are exercised by the clean-install runtime state machine rather than overstated as local unit coverage.
- The untouched stock template control builds with Node 24.18.0, npm 11.6.0, JDK 21, and its pinned wrapper before native dependencies are added.
- The final stock `probe` variant passes codegen, Android lint, unit tests, and APK assembly. No production release variant or AAB/publish/upload output is invoked.

### API 29 and API 37/16 KB runtime

For the same bundled `probe` APK on each named AVD, ten clean-data runs must all pass:

- clean install succeeds;
- the activity reaches a stable rendered state without Metro;
- UI and direct native evidence agree on SDK level and page size (`29`/`4096`, `37`/`16384`), and ABI is `arm64-v8a`;
- New Architecture is true and Hermes is present;
- initial revision `0` rejects invalid input without mutation, then atomically advances to `1` and exposes `PHASE0_ARMED_REVISION_1`;
- after Home, foregrounding Settings to demote the previous process, and bounded `am kill --user 0` retries (never force-stop), the exact native WorkManager job advances `1` to `2` in a fresh process where the TurboModule-seen flag is false;
- the harness discovers WorkManager's namespace and job ID, follows a single matching replacement job if Android/WorkManager changes the ID after process death, and requires three consecutive samples with no matching job before relaunch;
- relaunch rejects a stale revision without mutation and exposes `PHASE0_PASS`;
- time-bounded relevant-process logs contain no fatal/native linker signature or secret, personal-data, business-payload, or Firebase initialization value.

### Artifact

- Build Tools 37 `zipalign -c -P 16 -v 4` passes the probe APK, and every packaged ELF `LOAD` segment is aligned to at least `0x4000`.
- APK manifest and native-library inspection confirm minSdk 29, the lane's truthful targetSdk, the throwaway package, ARM64 support, and no prohibited permission/configuration.
- Results, hashes, and experiment commands are recorded in `phase0/PHASE0_EVIDENCE.md`; stock reproduction commands are retained in `spikes/rn-native-boundary/README.md`.

## What a pass does not prove

A pass does not prove that React Native allocates no JavaScript runtime object during worker-process application bootstrap, production architecture completeness, UI usability, accessibility, Room encryption/migrations/corruption recovery/retention/10,000-contact performance, periodic or nine-day WorkManager reliability, receivers, background eligibility, real SMS behavior, hard-restricted permission allowlisting, Google identity, People authorization/sync, Firebase Auth/App Check, AI Logic/Gemini, cloud coordination, account deletion, production signing, distribution, physical-device/OEM behavior, carrier behavior, legal readiness, or any release gate in Section 18.
