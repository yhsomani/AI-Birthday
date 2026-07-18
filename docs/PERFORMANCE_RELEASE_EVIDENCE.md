# Performance release evidence

Status: release-gating protocol subordinate to `PROJECT_ABOUT.md`. Emulator, simulator, debug, and local developer measurements are diagnostic only and cannot approve a release.

## Required measurement run

Use the exact signed release-like Android or iOS artifact and the unchanged reference device recorded by Phase 0. Android requires at least 6 GiB RAM. Reset the app/device to the protocol baseline, disable unrelated profiling overhead, retain raw timestamps and memory/power captures, and record every run—including failures and outliers. Do not delete a sample and rerun it without preserving and explaining the original result.

The version-2 evidence JSON consumed by `tools/validate-performance-evidence.mjs` contains only numeric samples, device/build identifiers, and immutable references plus SHA-256 digests and byte lengths for the raw private lab bundle. It must identify a physical device through a one-way device digest, the signed installation source, and the exact measurement tool/version. It must not contain contact names, numbers, birthday dates, message text, provider IDs, tokens, notification identifiers, or request bodies.

Collect at least:

- 30 cold Home, warm Home, 10,000-contact search, and iOS composer-presentation samples;
- 10 normalization/atomic-commit, no-due worker/reminder, reminder-horizon replacement, and controlled battery samples;
- 100 claim-plus-Arm network samples split across the recorded reference Wi-Fi and 4G networks; and
- the complete stress result with zero app-caused crash, ANR, OOM, or main-thread network/database violation.

Android battery evidence is a paired 24-hour no-due control/candidate run on the same device and conditions. iOS MessageUI latency ends when the ready system composer is presented after the explicit current foreground tap; system animation is excluded consistently. Network latency excludes a separately identified callable cold start, but the excluded samples remain in the raw bundle.

## Validation and release binding

Run:

```zsh
node tools/validate-performance-evidence.mjs \
  --file /external/evidence/android-performance.json \
  --platform android \
  --source-revision "$(git rev-parse HEAD)" \
  --artifact /external/release/app-prod-release.apk \
  --evidence-root /external/evidence/android-performance-support
```

Use `--platform ios` and the exact signed IPA for iOS. The validator recomputes the artifact SHA-256, verifies source/artifact binding, sample floors, freshness, release-like signing declaration, the reference-device RAM floor, nearest-rank P95/P99 values, maximum bounded-operation values, and every numeric budget in `tools/performance-budgets.json`.

The Android restricted-distribution validator and iOS archive/IPA validator also parse their signed
`performance` reference and invoke this semantic validator with the exact release source and artifact
digest (the final Android physical proof occurs at the direct or Play-delivered APK gate). Merely
hashing a performance JSON file or setting a release-approval boolean is insufficient.

For this standalone command, `--evidence-root` is mandatory and names a dedicated directory containing exactly the two regular,
non-symlink files named by `protocolReference` and `rawResultsReference`. Both
references are normalized relative paths below that root. The validator hashes
the actual bytes, rejects missing, extra, linked, escaping, oversized, or
changing files, and compares them with `protocolSha256`/`protocolBytes` and
`rawResultsSha256`/`rawResultsBytes`. Do not point the standalone option at a
shared evidence directory. When Android or iOS artifact verification embeds
this check, those same two files must instead be present in that platform's
broader exact release-evidence root alongside its primary and scenario-raw
files; the parent gate supplies the root and rejects anything outside that
derived union.

The release authority must independently retain and hash the JSON, raw-results bundle, protocol bytes, artifact, source revision, device inventory, and tool output. A passing local validator is evidence preparation only; the final distribution/store decision remains blocked until the independent authority and all physical device, carrier, accessibility, privacy, and store gates pass.
