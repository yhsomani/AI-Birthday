# Android restricted-release evidence runbook

Status: implementation runbook subordinate to `PROJECT_ABOUT.md`. It cannot approve a channel, waive an external gate, or authorize release.

## Trust boundary

`tools/distribution-authority-pin.json` is the only accepted authority-key pin. Its default `UNPROVISIONED` value is intentional: lab/prod APK and AAB packaging must fail until an independently controlled release authority provisions an Ed25519 public key, the SHA-256 of that key's DER SubjectPublicKeyInfo is committed in the pin, and the build runs from that exact clean commit.

The authority's private key must remain outside the repository, developer machines used for mobile signing, and ordinary CI. Do not replace the sentinel with a test key, generate a production private key in this project, or add a CLI option that selects a different pin. Only a public key may be supplied to the build; PKCS#8 and private-key objects are rejected even when their derived public digest matches the pin. The public key's DER SubjectPublicKeyInfo digest must equal the tracked pin.

To calculate the pin from an independently supplied public PEM:

```zsh
openssl pkey -pubin -in authority-public.pem -outform DER |
  openssl dgst -sha256
```

Commit only the lowercase 64-character digest. After that commit, prepare evidence against the new full 40-character `git rev-parse HEAD`; both tracked modifications and untracked files make restricted validation fail. The validator also requires the pin's exact bytes to equal `HEAD`, requires a normal regular-file index entry, and rejects assume-unchanged, skip-worktree, and fsmonitor-valid flags on the pin. Keep working evidence, signatures, and public-key copies outside the repository checkout.

## Evidence construction and review

Start from `tools/distribution-evidence.template.json`, but never submit the template itself. The exact schema-v4 document must satisfy `tools/distribution-evidence.schema.json`. Each lab or prod approval independently binds:

- its exact application ID, `versionCode`, `versionName`, installed-APK/app-signing certificate SHA-256, and clean source revision;
- for `google-play`, the separate upload-certificate SHA-256 used to sign the AAB; managed/direct approvals omit that field and require the packaged APK signer to equal the installed signer;
- the approved channel, installer, Android API range, launch countries, expiry, App Check/privacy/telephony decisions, and every required true gate; and
- immutable references plus SHA-256 digests of the exact policy approval, certified device matrix, carrier/SIM matrix, performance-budget results, accessibility matrix, native SBOM/lock/verification review, and legal review bytes.

Performance evidence must first pass `tools/validate-performance-evidence.mjs` against the exact signed release-like artifact and follow `docs/PERFORMANCE_RELEASE_EVIDENCE.md`. The authority verifies that output and its raw evidence independently. A generated SBOM or checksum file is not self-approval: the authority reviews the locked Gradle graph, Gradle artifact-verification metadata, native CycloneDX SBOM, vulnerability results, and retained candidate manifest before setting `nativeSupplyChainReviewed`.

The dependency evidence must follow the
[`native dependency advisory gate`](NATIVE_DEPENDENCY_ADVISORY_GATE.md) and
retain distinct SBOM/report entries for `prodReleaseRuntimeClasspath`, the
broader complete app/build/test graph, and the build-plugin graph. The authority
must bind the exact locks, Gradle verification metadata, SBOMs, advisory report,
and exception-policy digest. A combined or mislabeled graph, unavailable scan,
active finding, or ordinary-CI exception fails review. A zero count means no
active mapped OSV advisory at scan time; it is not an authority approval or a
claim that the dependency is vulnerability-free.

If one document contains both tiers, both entries must be completely valid even when only one tier is being built or verified. A malformed or stale unselected entry rejects the whole document.

The authority signs the file's exact bytes with Ed25519 and returns a raw 64-byte detached signature. Reformatting JSON, changing a newline, or adding an AAB/APK digest after signing invalidates the signature. One OpenSSL-compatible authority-side operation is:

```zsh
openssl pkeyutl -sign -rawin \
  -inkey authority-private.pem \
  -in distribution-evidence.json \
  -out distribution-evidence.sig
```

That private-key operation belongs only in the authority's controlled environment.

## Prepackage gate

From a clean checkout of the evidence-bound revision, export the mobile release-signing variables, the exact tier Firebase configuration, and:

```zsh
export BIRTHDAY_DISTRIBUTION_EVIDENCE_FILE=/external/release/prepackage-evidence.json
export BIRTHDAY_DISTRIBUTION_EVIDENCE_SIGNATURE_FILE=/external/release/prepackage-evidence.sig
export BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY_FILE=/external/release/authority-public.pem
```

Gradle invokes `tools/validate-distribution-evidence.mjs`; it does not implement a second, weaker parser. Development, staging, debug, and unsigned dev-Release flows do not require these inputs. Lab/prod packaging fails if the pin is absent, untracked, modified, unprovisioned, or mismatched; if Git is dirty or at another revision; if any approval is invalid; or if the exact detached signature fails.

For `google-play`, `BIRTHDAY_UPLOAD_STORE_FILE` and its alias/password inputs identify the AAB upload key, `BIRTHDAY_SIGNING_CERT_SHA256` identifies that same upload certificate, and schema v4 independently supplies `uploadSigningCertificateSha256`. The validator requires all three to agree. `signingCertificateSha256` instead names the Play app-signing certificate that signs delivered APKs; Gradle embeds that authority-approved installed certificate in `BuildConfig`. Google Play approval authorizes only a bundle task. An upload-key-signed APK cannot be packaged as an authorized Play artifact.

This separation follows [Android's app-signing contract](https://developer.android.com/studio/publish/app-signing): the upload key authenticates the submitted AAB, while Play App Signing signs APKs distributed to users. Firebase, OAuth, App Check, runtime readiness, and physical-device evidence use the Play app-signing fingerprint, never the upload fingerprint.

For `controlled-direct` or `managed-enterprise`, the build keystore is the installed-APK key. `signingCertificateSha256` must equal its certificate, `uploadSigningCertificateSha256` and `playUploadApproved` must be absent, and the approval authorizes only the APK task. This distinction prevents an upload key from ever qualifying at runtime as a Play-installed app key.

## Post-build binding

The prepackage approval deliberately has no artifact digest because the artifact does not exist yet. Do not rebuild an artifact after its final authority binding.

### Direct or managed APK

After producing the direct/managed APK, add its exact digest as `artifactApkSha256`, have the authority sign the complete file again, and run:

```zsh
tools/verify-android-apk.sh \
  /external/release/app-prod-release.apk \
  com.yashsomani.birthdayautopilot \
  --restricted-evidence \
  /external/release/final-evidence.json \
  /external/release/final-evidence.sig \
  /external/release/authority-public.pem \
  prod
```

Besides manifest, permission, certificate, APK-signature, alignment, and native-library checks, this reuses the same authority validator, rejects Google Play approval in direct mode, and requires `artifactApkSha256` to match the candidate byte for byte.

### Google Play AAB and delivered device APK

After producing the Play AAB, add its exact digest as `artifactAabSha256`, have the authority sign the complete evidence again, and run:

```zsh
tools/verify-android-aab.sh \
  /external/release/app-prod-release.aab \
  com.yashsomani.birthdayautopilot \
  --play-evidence \
  /external/release/aab-evidence.json \
  /external/release/aab-evidence.sig \
  /external/release/authority-public.pem \
  prod
```

The verifier requires one valid JAR signer, binds it to `uploadSigningCertificateSha256`, and binds the exact AAB bytes. Before evidence acceptance, the dedicated inspector runs the official bundletool `1.18.1` already present in the strict Gradle buildscript lock and checksum-verification metadata, offline through the pinned wrapper. It decodes the base protobuf manifest and requires the exact package, version code/name, compile/min/target SDK values, non-debuggable/non-test/backup-disabled/cleartext-disabled/uncompressed-native release flags, both restricted-SMS permissions, the required telephony-messaging feature, and the absence of every forbidden private-data or high-risk permission. Operator-supplied coordinates and ZIP entry names are never treated as manifest evidence. The verifier also rejects unsafe or duplicate bundle entries and non-arm64 libraries and checks every native ELF LOAD segment for 16 KB alignment. This AAB is an upload candidate only; it is not installed-app proof.

After Play processes the AAB, install the exact approved track on a physical certified device. Obtain that installation's exact `base.apk` bytes without rebuilding them, add their digest as `artifactApkSha256` while retaining `artifactAabSha256`, and have the authority sign the complete evidence again. Then run:

```zsh
tools/verify-android-apk.sh \
  /external/release/play-device-base.apk \
  com.yashsomani.birthdayautopilot \
  --play-delivered-evidence \
  /external/release/play-delivered-evidence.json \
  /external/release/play-delivered-evidence.sig \
  /external/release/authority-public.pem \
  prod \
  <physical-device-serial>
```

This mode requires the named connected device to be physical and inside the certified API range, requires `com.android.vending` as installer of record, compares the device's installed `base.apk` SHA-256 with the authority-approved local bytes, verifies the Play app-signing certificate on the base and every installed split, and checks the complete installed arm64 native set. Internal App Sharing and other Play test paths can be re-signed with a separate test certificate; that certificate must never be placed in production `signingCertificateSha256`, so those builds fail this gate.

Retain the upload AAB, delivered base and split APK evidence, every final evidence revision and signature, public key, referenced evidence objects, source revision, Play track identity, and physical-device record as one immutable release package. Passing these technical gates still does not replace external policy, installer allowlisting, carrier, privacy, or store approval required by `PROJECT_ABOUT.md`.

The same exact AAB digest, Play app identity, SMS declaration/decision, Data Safety export, localized
listing/screenshots, countries, public URLs, accessibility evidence, and reviewer materials must
also pass the independent [store submission evidence gate](STORE_SUBMISSION_EVIDENCE.md). That gate
cannot authorize restricted packaging or replace this authority-signed distribution record.

## Gemini operational switch and privacy procedure

The only Android client switch is the native Firebase Remote Config boolean
`gemini_suggestions_enabled`. Its in-app default is **false**. Only an activated value whose source
is remote and whose canonical value is exactly `true` can enable a later Firebase AI Logic request.
Missing tier Firebase configuration, default/static/malformed values, initialization failure, and
unavailable configuration all stay Off. Foreground authoring reads only the already-activated cache;
launch and foreground refresh remain non-blocking behind Firebase's eight-second fetch timeout, an
app-owned ten-second completion fence, and a one-hour minimum fetch interval. The four built-in
English/Hindi templates remain available while the switch or provider is Off.

Remote Config fetch uses Firebase's native Installations token. That identifier, the flag value,
fetch status, Firebase project/provider details, prompts, contacts, messages, credentials, and
provider responses must never cross React Native, logs, diagnostics, analytics, or support payloads.
Use one unconditional production boolean: do not add audiences, personalization, experiments,
real-time config listeners, custom signals, or another client-side AI switch. Remote Config is only
an operational gate; enabled requests must still pass the exact Firebase Auth session and limited-use
App Check token checks before the provider boundary.

Before enabling, retain the reviewed Remote Config template version and hash, exact unconditional
parameter, Firebase project, approver, change ticket, signed-build App Check probe, quota and budget
controls, privacy approval, and rollback drill. To contain an incident, publish `false`, record the
template version/time/operator, and disable or quota-stop Firebase AI Logic server-side when waiting
for client fetch cadence is not fast enough. Re-enabling requires fresh evidence and approval.

## Residual release-environment assumptions

The validator removes inherited `GIT_*` overrides from its Git subprocesses, disables system/global Git configuration, fsmonitor, and the untracked cache, and ignores any global excludes file. Those defenses cannot make code running inside a mutable checkout self-attesting. Every restricted release must therefore retain these gates:

- Use a fresh, disposable, access-controlled checkout of the exact evidence-bound commit. Do not reuse a developer worktree. Reject local Gradle init scripts and repository-local exclude customizations, and do not permit another process or user to mutate the checkout while Gradle is configured or running.
- Use the exact reviewed Node, Git, JDK, Gradle, Android SDK/NDK, and shell-tool binaries from an attested release image. Sanitize `PATH`, `NODE_OPTIONS`, `BASH_ENV`, `ENV`, Gradle user-home/init-script inputs, and other runtime injection surfaces before invoking Gradle or the standalone verifier.
- Copy the completed AAB or APK to an access-controlled, non-symlinked, non-hard-linked, content-addressed release location before final verification. No process may replace or modify that file while the verifier's independent tools inspect it. Retain every final digest and immutable object.
- Treat only the channel-specific standalone verifier with final artifact-bound evidence as post-build approval. A successful prepackage CLI/Gradle check is permission to build a candidate, never permission to upload or distribute it.
- Require the independent authority to obtain and verify the exact referenced policy/device/carrier/legal bytes and exact final AAB/APK digests itself. A digest supplied only by the build operator is not independent evidence.

If any of these environment or immutability guarantees is unavailable, restricted packaging and distribution remain blocked even when the local validator reports success.
