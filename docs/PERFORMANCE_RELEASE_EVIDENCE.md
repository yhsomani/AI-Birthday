# Performance release evidence

Status: release-gating protocol subordinate to `PROJECT_ABOUT.md`. Emulator, simulator, debug, and local developer measurements are diagnostic only and cannot approve a release.

## Required measurement run

Use the exact signed release-like Android or iOS artifact and the unchanged reference device recorded by Phase 0. Android requires at least 6 GiB RAM. Reset the app/device to the protocol baseline, disable unrelated profiling overhead, retain raw timestamps and memory/power captures, and record every run—including failures and outliers. Do not delete a sample and rerun it without preserving and explaining the original result.

The evidence JSON consumed by `tools/validate-performance-evidence.mjs` contains only numeric samples, device/build identifiers, and immutable references plus SHA-256 digests for the raw private lab bundle. It must not contain contact names, numbers, birthday dates, message text, provider IDs, tokens, notification identifiers, or request bodies.

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
  --artifact /external/release/app-prod-release.apk
```

Use `--platform ios` and the exact signed IPA for iOS. The validator recomputes the artifact SHA-256, verifies source/artifact binding, sample floors, freshness, release-like signing declaration, the reference-device RAM floor, nearest-rank P95/P99 values, maximum bounded-operation values, and every numeric budget in `tools/performance-budgets.json`.

The release authority must independently retain and hash the JSON, raw-results bundle, protocol bytes, artifact, source revision, device inventory, and tool output. A passing local validator is evidence preparation only; the final distribution/store decision remains blocked until the independent authority and all physical device, carrier, accessibility, privacy, and store gates pass.
