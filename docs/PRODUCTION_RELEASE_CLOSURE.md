# Production release closure

Status: mandatory final release gate. Passing any component validator alone does
not authorize production. The checked-in template and authority pin are
deliberately unusable and must remain that way until the external release
authority and every production coordinate are independently provisioned.

## What this closes

`npm run release:closure:validate -- ...` is the one executable decision that
composes the Android AAB distribution, physical Play delivery, cloud, store-submission, and
Hosting proofs. It accepts only an authority-signed final manifest whose exact
bytes bind:

- one clean authoritative Git HEAD and the tracked Ed25519 authority SPKI pin;
- the production Firebase project number/ID and exact Android Firebase app ID;
- the exact Google Play AAB bytes, name, version, package ID, signing-certificate digest, and the exact Play-delivered base/split
  APK inventory observed on a physical device installed by Google Play;
- the exact Hosting origin, approved release-config bytes, and deployed
  artifact bytes;
- the byte digest and length of all six deterministic component verification
  reports; and
- a current final approval plus component approval/report expiries that all
  outlive the final manifest.

The final validator rereads bounded regular files without following symlinks,
rejects path traversal and unknown manifest/report fields, verifies the detached
signature with the same repository-pinned authority, rechecks artifact hashes,
and verifies that HEAD and the worktree did not change. Evidence, artifacts,
reports, signatures, and the completed manifest belong in access-controlled
storage outside the repository.

## Produce the component reports

Run every command from the exact clean commit. The examples intentionally use
placeholders; do not copy values from another tier or invent missing evidence.

1. Run the protected Android `verify-authority-approved-artifact` workflow. Only
   after the channel-specific AAB/APK verifier has completed its manifest,
   signature, native-ELF, policy, authority, artifact, and referenced-evidence
   checks does the workflow create `release-closure-report.json`. That
   deterministic report binds the retained full-verifier text and verification
   manifest digests as well as source/tier/channel, exact artifact bytes,
   upload/installed certificates, authority, and approval expiry. Use that file
   as the Android component report. The distribution validator's internal JSON
   projection alone is insufficient. A prepackage report, direct APK report, or
   non-Play channel cannot satisfy the store closure. This is the required AAB
   upload proof; it does not prove what Google Play installed.
2. Install the approved Play track on a physical certified device and run the
   `--play-delivered-evidence ... --report ...` command in
   `ANDROID_RESTRICTED_RELEASE_EVIDENCE.md`. Use its exclusive structured output
   as `androidPlayDelivery`. It binds the same AAB digest plus the delivered base
   digest, installer of record, hashed device identity/API, exact base/split
   inventory, source, authority, expiry, and actual signer SHA-1/SHA-256. The
   closure cross-checks SHA-1 against the production Android OAuth registration
   and SHA-256 against App Check, store evidence, and the installed certificate.
   Device proof expires no later than 24 hours after its UTC observation, so the
   final approval and closure must complete inside that window. Both Android
   reports are mandatory. Retain the pulled files in one flat,
   access-controlled directory using their reported APK file names; the final
   validator independently hashes the exact base and every split and rejects
   missing, additional, linked, renamed, or changed files.
3. Run the cloud validator with its new optional report output:

   ```sh
   npm run cloud:evidence:validate -- \
     --file <protected/cloud-release-evidence.json> \
     --evidence-root <protected/cloud-evidence> \
     --signature <protected/cloud-release-evidence.sig> \
     --public-key <protected/release-authority-public.pem> \
     --report <protected/reports/cloud-release-verification.json>
   ```

   The report contains no credentials or request bodies. It deterministically
   projects the signed evidence/source/authority, production project and mobile
   app IDs, earliest approval expiry, Android Play App Check/OAuth trust,
   web reCAPTCHA Enterprise registration, and
   Hosting source/config/deployment digests. The final closure cross-checks
   those projections against the delivered Android signer and
   exact Hosting release config.

5. Run the signed store wrapper with all its existing environment inputs and an
   additional protected output path:

   ```sh
   BIRTHDAY_STORE_VERIFICATION_REPORT=<protected/reports/store-release-verification.json> \
     npm run store:release:check
   ```

   The wrapper creates the report only after signature verification and the
   complete release-mode store validator pass. It hashes the exact AAB, IPA,
   Hosting config, signed store evidence, approval scope, current signed mobile
   version/build coordinates, and earliest approval expiry.

6. Download the exact retained output of the protected
   `Hosting production artifact deploy and provenance` workflow. Compose the
   Hosting report from the successful cloud/store report bytes, approved raw
   config, canonical deployment artifact, its manifest, and the provider-bound
   deployment provenance. First run the protected read-only current-live
   workflow and use its report before its 15-minute expiry:

   ```sh
   npm run hosting:release:report -- \
     --cloud-report <protected/reports/cloud-release-verification.json> \
     --store-report <protected/reports/store-release-verification.json> \
     --hosting-config <protected/hosting-release-config.json> \
     --deployment-artifact <protected/hosting-deployment-artifact.json> \
     --deployment-manifest <protected/hosting-deployment-manifest.json> \
     --deployment-provenance <protected/hosting-deployment-provenance.json> \
     --current-live-observation <protected/hosting-current-live-observation.json> \
     --output <protected/reports/hosting-release-verification.json>
   ```

   The composer revalidates every deterministic package file, the no-hook
   deployment config, manifest, exact Firebase CLI-created version, matching
   live `DEPLOY` release, project/site/origin, workflow identity/window,
   source/config/tree digests, and all cloud/store cross-bindings. A local
   build, arbitrary archive, latest-release observation, or operator-supplied
   version ID is not proof of deployment.

   The current-live workflow and the only authorized Hosting deploy workflow
   share one site-scoped concurrency group. Both receive the exact number of a
   separate release-security Google Cloud project and the protected
   `HOSTING_ADMISSION_BUCKET` name. That bucket must be private, uniform-access,
   non-versioned, soft-delete-disabled, and permanently locked to exactly 900
   seconds, with the one documented prefix-scoped lifecycle cleanup rule.
   Before publishing the observation, the workflow creates a unique
   site/source/run/attempt object with `ifGenerationMatch=0` and reads its pinned
   generation back byte-for-byte. Cancellation cannot remove or shorten it.

   Before any Hosting mutation, the deploy workflow validates the exact locked
   bucket metadata, paginates the entire site prefix, pins and downloads every
   listed object generation, re-creates each canonical lease value, and denies
   deployment if any lease remains unexpired. The workflow retains every raw
   bucket/list-page/object-metadata/object-content byte and an exact manifest;
   provenance generation re-hashes that link-free, no-extra inventory and binds
   its canonical root digest and byte count. The PASS snapshot is additionally
   bound to the application project, source, site prefix, repository, workflow,
   run, and attempt. The Hosting and final reports bind
   the external security project, bucket/object names, generations,
   metagenerations, provider creation/retention times, canonical content digest,
   raw bucket/object observation digests, and exact run/attempt. The final
   validator also cross-binds the exact observer, admission-reader, and deployer
   service accounts and WIF providers to the signed cloud control report,
   recomputes the canonical lease digest, and requires PASS-before-deployer and
   the 900-second timing relationships.

   Signed IAM evidence must show that the observer has only bucket metadata,
   create, and readback access; a distinct admission-reader service account has
   only bucket metadata, list, and get access; the Hosting-mutating deploy
   service account has zero bucket access; app/mobile/web/runtime identities
   have zero access; and no Hosting writer exists outside the protected deploy
   workflow. The build, admission, and deploy jobs must use the distinct
   `hosting-production-build`, `hosting-production-admission`, and
   `hosting-production-deploy` environments. Build has no OIDC; admission runs
   no downloaded/project code and can mint only the reader; deploy begins only
   after the current-attempt PASS and can mint only the writer. Re-run all jobs,
   never only the failed deploy job, because a prior-attempt PASS is rejected
   before artifact download or source execution. Signed logging evidence must cover Cloud Audit Logs Data Access
   for this bucket in the separate release-security project. This operational
   bucket is never Firebase Storage product data, so the application project's
   `prohibitedServices.applicationProjectCloudStorageEnabled:false` remains
   exact.

   This proof is the authority-signed cloud manifest's strict
   `hostingReleaseControl` section plus the protected `live-readonly-audit`
   report, manifest, and raw tar. The final cloud loader reparses the raw
   application/security project policies, all three keyless service accounts
   and service-account IAM policies, their three active and distinct WIF
   providers, the complete bucket inventories/metadata/IAM, fully explored
   inherited Cloud Asset access analyses, and the release-security audit
   config/sink/locked-retention bucket. Narrative IAM assertions are not
   accepted as a substitute.

   GitHub environment configuration is retained as authoritative raw evidence,
   not inferred from an environment-name string. The cloud collector uses a
   pinned, short-lived GitHub App installation token and retains the exact
   organization-owned repository, fail-closed `main` branch protection, all
   five protected environment GETs, their exact `main`-only deployment branch
   policy lists, and organization audit events proving administrator bypass is
   disabled. Branch protection must strictly require exactly
   `Release admission for exact source SHA`, the GitHub Actions aggregate job
   that depends on every CI job including the complete-history credential scan.
   The raw package also binds that required check's App ID and successful check
   suite to the successful `CI` push run for the closure's exact source SHA;
   branch configuration alone is not accepted as proof that the selected
   revision passed. The protected environments are
   `cloud-production-readonly-audit`, `hosting-production-readonly-live`,
   `hosting-production-build`, `hosting-production-admission`, and
   `hosting-production-deploy`; each must require immutable non-self reviewer
   IDs. Configure the protected `GITHUB_GOVERNANCE_APP_CLIENT_ID` variable and
   `GITHUB_GOVERNANCE_APP_PRIVATE_KEY` secret for an App installed only on this
   repository with Actions read, Checks read, repository Administration read, and
   organization Administration read. Human-role separation within the
   configured reviewer IDs remains an operational release-management check.

All report writers use exclusive creation and refuse to overwrite an earlier
report. Regenerate into a new protected directory when any input changes.

## Create, sign, and validate the final manifest

Copy `tools/production-release-closure.template.json` into protected release
storage. Replace every `null`, zero byte count, `pending` state, and report
digest with independently established production values. Do not edit the
checked-in template. Keep the closure validity at no more than 30 days and no
later than any component approval/report expiry.

The independently controlled release authority signs the exact completed JSON
bytes with the Ed25519 private key corresponding to
`tools/distribution-authority-pin.json`. The build/release operator receives
only the public key and detached 64-byte raw signature.

Run the final decision from the same clean commit, with reports beneath one
protected evidence root:

```sh
npm run release:closure:validate -- \
  --file <protected/production-release-closure.json> \
  --signature <protected/production-release-closure.sig> \
  --public-key <protected/release-authority-public.pem> \
  --evidence-root <protected/release-closure-evidence> \
  --android-artifact <protected/exact-production.aab> \
  --android-delivered-base <protected/play-installed-apks/base.apk> \
  --android-installed-apk-root <protected/play-installed-apks> \
  --hosting-artifact <protected/hosting-deployment-artifact.json>
```

Only a final `PASS production release closure ...` result authorizes those exact
bytes during that unexpired window. Changing a report, artifact, version,
certificate, Play-installed base/split inventory, Firebase app, project, Hosting origin/config, approval, manifest,
signature, authority pin, source file, HEAD, or worktree requires a new closure.

## Android-only launch scope

This closure process supports Android-only production releases. iOS companion remains a Phase 3 future opportunity; no iOS artifact (IPA) is required for Android Automation Edition launches. When iOS companion reaches production readiness, this closure process will be extended to include iOS artifact verification.

## Intentional blockers

- `tools/distribution-authority-pin.json` defaults to `UNPROVISIONED`.
- `tools/production-release-closure.template.json` contains no production
  project identity, artifact/report digest, approval, or signature.
- The template passes only
  `npm run release:closure:template:check`; it must fail release mode.
- The validator never provisions cloud/store identities, approves policy,
  creates signing keys, deploys Hosting, uploads store artifacts, or substitutes
  local tests for physical-device, carrier, App Store, Play, cloud, legal, or
  human approval evidence.

This final gate intentionally favors a small authority-signed byte closure over
rerunning every expensive platform verifier inside one process. The tradeoff is
that component reports must be retained exactly; the final signature and strict
cross-checking make any replacement or mismatched report fail closed.
