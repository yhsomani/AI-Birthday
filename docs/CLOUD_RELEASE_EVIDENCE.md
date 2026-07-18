# Production Firebase and Google Cloud release evidence

The repository now has an executable, fail-closed cloud release gate. It does
not deploy anything, provision infrastructure, infer production identifiers, or
claim that any Firebase/Google Cloud console gate has passed.

The gate has five parts:

- `tools/cloud-release-evidence.schema.json` is the strict structural contract;
- `tools/cloud-release-evidence.template.json` enumerates required fields and
  evidence, but is deliberately unusable because its decision is pending and
  all external coordinates, observations, reviews, and approvals are null;
- `tools/validate-cloud-release-evidence.mjs` applies source, semantic, expiry,
  file-digest, cross-field, and release-authority signature checks;
- `tools/create-cloud-readonly-observation-report.mjs` and its strict report
  schema create the compact source/project/workflow association consumed by the
  validator;
- `.github/workflows/cloud-readonly-evidence.yml` is an operator-triggered,
  protected-environment observation workflow. It can collect a subset of
  current console state, but its artifact explicitly says
  `mutationAuthorized:false` and cannot approve a release;
- `.github/workflows/hosting-production-deploy.yml` is the separate protected,
  keyless production mutation boundary. It builds one canonical Hosting
  package, deploys only files extracted from those exact bytes, and retains the
  exact Firebase release/version provenance. It does not approve itself.

No production project ID, project number, Firebase app ID, billing account,
Hosting site, OAuth client, service account, public URL, quota, budget, SLO, or
approver identity is invented or stored in the template.

## Trust and source binding

The final JSON file's exact raw bytes must be signed with the existing release
authority. The verifier checks the Ed25519 public key against
`tools/distribution-authority-pin.json`; a supplied public key is not trusted by
itself. That pin remains `UNPROVISIONED`, so no cloud release package can pass
today. Provisioning the release authority is a separate controlled security
decision. Its private key never belongs in this repository, a mobile signing
environment, or the read-only audit workflow.

Validation requires a clean checkout at the exact approved `HEAD` and recomputes
content digests for:

- `PROJECT_ABOUT.md`;
- `backend/firebase.json`;
- the complete deployable Functions source, package/toolchain/compiler inputs,
  and exact Functions lockfile;
- Firestore rules, indexes, and the TTL policy inventory;
- the complete Hosting page/static source, package/toolchain/compiler inputs,
  release-config writer, and lockfile.

The final package also binds its deployed Functions source revision to this same
Git revision and requires a separate deployment-provenance attestation. An
operator-authored JSON file, an unclean worktree, stale console exports, a
signature from an unpinned key, or correct-looking project IDs cannot authorize
release.

Hosting uses an additional byte chain. The canonical artifact contains the
exact `hosting/public` files, a no-hook deployment `firebase.json` projection,
the selected project/site, the approved release-config digest, the Hosting
source-tree digest, and deterministic file/tree manifests. The deployment
report binds those bytes to Firebase CLI `15.23.0`'s exact created version and
the matching live `DEPLOY` release inside the recorded workflow window. The
signed cloud evidence hashes that exact report in the evidence reference whose
ID is `hosting-release`; this is distinct from the separately required
`source-deployment-provenance` reference selected by
`source.deploymentProvenanceEvidenceId`.

Use a clean detached release checkout to print the source coordinates:

```sh
npm run cloud:evidence:source
```

The command intentionally fails in a dirty checkout.

## Protected read-only observation workflow

Run `Cloud production read-only evidence observation` manually against the exact
release revision. The GitHub environment
`cloud-production-readonly-audit` must require reviewers and define these
non-secret environment variables:

- `CLOUD_AUDIT_WIF_PROVIDER` — the exact Workload Identity Provider resource;
- `CLOUD_AUDIT_SERVICE_ACCOUNT` — a dedicated production-project audit identity
  distinct from the Functions runtime identity.
- the same `HOSTING_ADMISSION_BUCKET`, `HOSTING_AUDIT_*`,
  `HOSTING_ADMISSION_READER_*`, and `HOSTING_DEPLOY_*` variables consumed by
  the two protected Hosting workflows;
- `RELEASE_SECURITY_LOG_SINK`, `RELEASE_SECURITY_LOG_BUCKET`, and
  `RELEASE_SECURITY_LOGGING_LOCATION` for the dedicated release-security audit
  destination.

The audit identity must have only the metadata read permissions needed by the
listed commands. Use Workload Identity Federation; do not create or upload a
service-account key. Google documents Workload Identity Federation as the
preferred keyless deployment-pipeline pattern:
[deployment-pipeline federation](https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines).

Every project, app, billing, runtime, Hosting, Logging-location, source, provider,
and retained-tier coordinate is an explicit `workflow_dispatch` input and is
regex-validated before authentication. The workflow checks the observed project
number, billing attachment, Firebase app/site inventory, and active audit
identity against those inputs. It uses immutable action commits and exact Google
Cloud CLI `575.0.1` and Firebase CLI `15.23.0` versions.

The inspection job intentionally uses repository Node `24.18.0`; the isolated
Functions package permits `>=22 <25`, while the separately hosted Functions CI
lane on Node 22 remains the binding deployed-runtime proof. The short-lived OAuth
access token exists only in shell memory and a protected curl-config stdin pipe:
it is never a curl argument, file, artifact, command trace, or printed value.

The workflow uses only metadata operations: list, describe, get-policy, and four
HTTPS GETs. It observes:

- project/billing identity, enabled APIs, and budget metadata;
- deployed second-generation Functions and Scheduler jobs;
- Firestore database/PITR, indexes, TTL fields, backup schedules, and existing
  backups;
- Secret Manager secret/version metadata only—never a secret-version access;
- runtime/audit service-account metadata, user-managed key counts, and their
  project bindings;
- Logging exclusions, sinks, and `_Default`/`_Required` retention;
- Firebase Android/iOS/web app IDs and Hosting site ID;
- Android, App Attest iOS, and web App Check provider configurations;
- the active Remote Config template.
- the separate release-security project and its complete GCS bucket inventory,
  exact admission-bucket metadata/IAM, application-project empty bucket
  inventory, all three Hosting release service accounts/key inventories and
  service-account IAM policies, and all three WIF providers;
- fully explored Cloud Asset IAM Policy Analyzer responses, with inherited
  policies, expanded roles, and expanded groups, for the admission bucket and
  the exact Firebase Hosting mutation permission set, plus each release
  service account's token-minting permissions so an alternate impersonator
  cannot hide in inherited IAM;
- release-security Storage Data Access audit configuration, exact log sink, and
  permanently locked 30-day Logging bucket.

It creates a source-bound evidence manifest, packages the manifest plus raw
observations into a deterministic `cloud-readonly-observation.tar`, and then
creates `cloud-readonly-observation-report.json`. The report binds the exact
manifest/tar digests and byte counts, clean source, production project and all
three Firebase app IDs, Hosting site, runtime/audit identities, observation
time, authoritative repository, workflow run/attempt/ref, and
`mutationAuthorized:false`. Its producer parses the project, Firebase app,
Hosting site, and service-account observations instead of copying dispatch
labels into a report.

Preserve the protected 14-day GitHub artifact in the release record. In the
final evidence root, reference the JSON report as `live-readonly-audit` and
retain its exact declared `evidence-manifest.json` and
`cloud-readonly-observation.tar` companions. Those are the only extra companion
paths the validator permits. It rehashes all three and requires signed
`capturedAt` to equal the report `observedAt`; another project, source, app,
site, identity, repository/workflow, stale relabel, or changed companion fails
closed. The producer and final validator also parse the archive as strict
link-free POSIX ustar, require its exact manifest/raw inventory, and compare
every retained raw file's length and SHA-256 with the manifest; an opaque or
unrelated tar cannot substitute for the observations. The package is
observation, not proof of signed-channel App Check
success, replay rejection, deletion, IAM least privilege, AI monitoring
privacy, or service SLOs.

The workflow must never gain a deploy, create, patch, update, delete, restore,
import, export, enable, disable, Secret Manager access, request-body, or other
remote mutation command. `tools/cloud-readonly-workflow-contract.test.mjs`
enforces that boundary.

Provision one private admission bucket in a **separate release-security Google
Cloud project**, never in the production Firebase/application project. Set the
bucket name as the protected `HOSTING_ADMISSION_BUCKET` environment variable
for both Hosting workflows and dispatch both with the exact release-security
project number. The report rejects that number if it equals the Firebase
project number, and binds it to the bucket provider metadata.

The bucket contract is exact: public access prevention enforced, uniform
bucket-level access enabled, object versioning disabled, soft delete disabled,
a permanently locked bucket retention policy of exactly 900 seconds, and one
`Delete` lifecycle rule at age one day scoped only to
`hosting-production-change-freezes/`. Locking is irreversible; configure and
independently review the bucket before locking it. The delayed lifecycle cleanup
does not define admission validity: the canonical object value does, while the
locked retention guarantees that the object cannot disappear during that
validity window.

Use bucket-scoped cross-project custom roles. The current-live observer may have
only `storage.buckets.get`, `storage.objects.create`, and
`storage.objects.get`. A distinct admission-reader identity used by the deploy
workflow may have only `storage.buckets.get`, `storage.objects.list`, and
`storage.objects.get` there. The Hosting-mutating deploy identity has **zero**
admission-bucket permissions and only its separately scoped Hosting rights.
None may update, overwrite, compose, rewrite, restore, or delete lease objects
or mutate the bucket. App Check identities, mobile/web clients, Firebase
runtime service accounts, human application operators, and the Hosting site
have zero access.
The authority-signed `hostingReleaseControl` object and protected
`live-readonly-audit` raw archive cover this cross-project bucket. The final
loader reparses the retained tar bytes and refuses a report that is not the
deterministic semantic projection of those raw observations. Every WIF provider
must be active, pairwise distinct, use GitHub's exact OIDC issuer, and use the
exact six-claim mapping and condition that bind immutable repository/owner IDs,
repository path, workflow file, protected environment subject, and
`refs/heads/main`. Each identity uses a distinct active pool whose full provider
inventory contains exactly its one approved provider; a weaker sibling provider
cannot reuse the pool-scoped subject principal. Each
service-account IAM policy may expose `roles/iam.workloadIdentityUser` to only
the matching exact subject principal; fully explored token permission analysis
must find no alternate access-token, ID-token, implicit-delegation, blob-signing,
or JWT-signing principal.

The collector also retains each application/security project's complete Cloud
Asset `RESOURCE` inventory and the effective
`iam.disableCrossProjectServiceAccountUsage` policy. It scans every resource
payload for the three release service-account emails, allows only each account's
own IAM ServiceAccount asset, and rejects an attached VM, Cloud Run service/job,
Function, or other runtime. Wait for Cloud Asset inventory propagation after
any IAM or runtime change before capturing evidence; stale observations are not
release proof.

The collector retains each project's authoritative ancestor chain. IAM Policy
Analyzer runs at the top organization scope when one exists, or at the project
only when the raw ancestry proves that project has no folder/organization
parent; the signed control object binds both derived scopes. A project-scoped
`fullyExplored:true` response is never treated as proof against inherited
organization/folder grants.

The Hosting writer analysis queries the complete current Firebase Hosting IAM
mutation surface—`firebasehosting.sites.create`, `.delete`, and `.update`—from
the [official Hosting IAM reference](https://cloud.google.com/iam/docs/roles-permissions/firebasehosting).
It does not invent REST resource names as IAM permissions.

The raw cloud package also proves repository and environment governance through
authoritative GitHub REST observations. The repository must be the exact
organization-owned `yhsomani/AI-Birthday` repository. Protected `main` must
enforce administrators, stale-review dismissal, code-owner and last-push
approval, at least one approval, no force-push or deletion, and empty user,
team, and app review-bypass lists. Its required status-check policy must be
strict and contain exactly the GitHub Actions check
`Release admission for exact source SHA`. That stable aggregate job depends on
all CI jobs, including the complete-history credential scan, backend checks,
all Android instrumentation variants, Android device E2E, portable/Android
quality, and iOS. Configure this required check in branch protection only after
the updated `CI` workflow has produced it on `main`; a local workflow file
cannot provision repository protection.

For the selected release revision, the collector retains the bounded latest
check-run response and the exact successful `CI` push workflow run on `main`.
The parser requires one successful aggregate GitHub Actions check, binds its
App ID to the required branch-protection check, binds its check-suite ID to the
successful `.github/workflows/ci.yml` run, and binds both records to the report
source SHA. A PR-only run, another workflow, another SHA, a failed or incomplete
run, a foreign check App, a non-strict policy, or a stale context fails closed.
The five exact environments are
`cloud-production-readonly-audit`, `hosting-production-readonly-live`,
`hosting-production-build`, `hosting-production-admission`, and
`hosting-production-deploy`. Each environment GET must expose at least one
immutable reviewer ID, `prevent_self_review:true`, and exactly one custom
deployment branch policy named `main`; the newest matching organization audit
event must independently prove `can_admins_bypass:false` and
`prevent_self_review:true`.

The workflow mints a short-lived installation token with the pinned
`actions/create-github-app-token` action before collection and never exposes it
to project tooling. Configure `GITHUB_GOVERNANCE_APP_CLIENT_ID` as a protected
environment variable and `GITHUB_GOVERNANCE_APP_PRIVATE_KEY` as a protected
environment secret. Install that App only on `AI-Birthday` with repository
Actions read, Checks read, repository Administration read, and organization
Administration read. The package retains the raw repository,
branch-protection, exact-source check-run/CI-run, environment,
deployment-branch-policy, and organization audit-log responses, binds their
digest before any project tool runs, and reparses them from the final tar. The
workflow fails closed when the phrase-filtered audit response reaches 100
events; save the environment governance again and rerun so its current event is
within the bounded complete window. Human-role separation within the configured
reviewer IDs remains an operational release-management responsibility.

Enable Cloud Audit Logs Data Access for `storage.googleapis.com` in the
release-security project, including Admin Read, Data Read, and Data Write. The
signed logging evidence must bind the exact project/bucket, log sink and
destination, canonical bucket-only filter, locked 30-day retention, access
policy, and no exclusions. The collector also
retains every release-security folder/organization IAM policy and rejects an
exempted member in either `allServices` or `storage.googleapis.com`, because
Data Access audit configuration and exemptions are additive through the
resource hierarchy. Lease bodies contain only
site/source/run/attempt/expiry and no app or user data. This is provider audit
logging, not a second GCS access-log bucket.

The observer creates exactly one unique
`site/source/run/attempt` JSON object using `ifGenerationMatch=0`, reads its
pinned generation back byte-for-byte, and retains the raw bucket/object
metadata observations. Its report binds the bucket/object, bucket and object
metagenerations, object generation, creation/retention times, canonical content
SHA-256, both raw-observation SHA-256 values, and the exact active observer
service account and WIF provider. The deploy workflow paginates
the full site prefix and downloads/validates every retained object before any
Hosting mutation; a missing permission, malformed page/object, changed bucket
policy, or unexpired canonical lease fails closed. The two workflows also share
the site-scoped concurrency group. IAM evidence must prove that the protected
deploy workflow is the sole identity/workflow allowed to mutate the production
Hosting site.

## Protected Hosting deployment workflow

Run `Hosting production artifact deploy and provenance` only for an exact
reviewed revision and pass the separately reviewed release-security project
number. The workflow has three privilege-separated jobs and environments:

- `hosting-production-build` holds only `HOSTING_RELEASE_CONFIG_BASE64`, has
  explicit `id-token: none`, builds once, and uploads the canonical artifact;
- `hosting-production-admission` holds only
  `HOSTING_ADMISSION_BUCKET`, `HOSTING_ADMISSION_READER_WIF_PROVIDER`, and
  `HOSTING_ADMISSION_READER_SERVICE_ACCOUNT`; its WIF provider belongs to the
  release-security project and the identity has only the three bucket
  read/list permissions above; and
- `hosting-production-deploy` holds only `HOSTING_DEPLOY_WIF_PROVIDER` and the
  dedicated `HOSTING_DEPLOY_SERVICE_ACCOUNT` with the selected-site Hosting
  permissions and zero admission-bucket permissions.

The admission job executes no downloaded or checked-out project code. It uses
only pinned actions and workflow-owned shell primitives to hash and inspect the
immutable build handoff before authenticating the reader and running the gate.
The deploy job cannot start until that job succeeds. Its first workflow-owned
step rejects a PASS from another repository, run, or run attempt and rejects
different build/admission artifact IDs or digests before download, checkout,
npm lifecycle, or writer authentication. GitHub's “re-run failed jobs” must not
reuse an earlier PASS: re-run all three jobs so build, admission, and deploy
share the current `GITHUB_RUN_ATTEMPT`. The workflow retains the admission reader/provider,
the post-PASS deployer/provider, and an exact site/source/run-scoped PASS
snapshot. The retained check manifest inventories and hashes every raw bucket,
listing page, object metadata, and object-content byte under
`hosting-admission-check/`; provenance generation re-reads that directory and
rejects links, extras, omissions, changed bytes, or page/object count drift.
Object evidence uses a monotonically increasing six-digit index, never a GCS
generation as a local filename. The build job decodes its protected config only
after locked dependencies install, requires the exact dispatch SHA-256, and
deletes it immediately after packaging.

Both source-executing jobs fetch full history through the pinned checkout action
without persisted credentials and require the exact selected revision to be an
ancestor of `refs/remotes/origin/main` before secret decoding, npm lifecycle, or
deploy tooling runs. A 40-character commit from an unmerged collaborator branch
is not an eligible release source.

The workflow pins Node `24.18.0`, npm `11.6.0`, Firebase CLI `15.23.0`, Google
Cloud CLI `575.0.1`, and every third-party action. It uses keyless Workload
Identity Federation, an explicit project and site, and a unique
source/run/artifact deployment message. No `.firebaserc`, implicit target,
mutable global Firebase CLI, predeploy hook, or post-package rebuild
participates. It:

1. builds `backend/hosting/public` once from the protected release config;
2. writes `hosting-deployment-artifact.json` and its exact manifest;
3. extracts that package into a new root and deploys only that root;
4. requires Firebase CLI JSON to identify exactly one created Hosting version;
5. requires the new live `DEPLOY` release to reference that same version inside
   the recorded operation window; and
6. retains the package, manifest, deploy result, release/version observations,
   raw admission-check directory/manifest/PASS, active deploy identity, and
   `hosting-deployment-provenance.json` together.

Copy those exact retained files into access-controlled release storage. The
cloud authority sets `hosting.deployedArtifactSha256`,
`deploymentManifestSha256`, `deploymentProvenanceSha256`,
`deploymentConfigSha256`, `publicTreeSha256`, `siteId`, and
`deployedVersionId` from those bytes—not from a rebuild or console memory. The
provenance report is the `hosting-release` evidence file, so that evidence
reference's digest must equal `deploymentProvenanceSha256`. Do not place it at
or substitute it for the separate `source-deployment-provenance` reference.

Immediately before final closure, run the separate protected
`Hosting production current-live observation` workflow. It performs only GETs
against the application/Firebase project for the exact `live` channel/current
release and version, site, custom-domain state when applicable, and public
reserved `/__/firebase/init.json`; its sole mutation is creating the immutable
admission object in the separate release-security project. Its report expires
after 15 minutes. The signed cloud evidence hashes it as
`hosting-current-live`; the composer rejects a superseded version, stale report,
origin not mapped to the selected site, or web config whose project/app identity
differs. Firebase documents live-channel serving in the
[Channel resource](https://firebase.google.com/docs/reference/hosting/rest/v1beta1/sites.channels)
and active ownership/host, redirect, deletion, and issue state in the
[CustomDomain resource](https://firebase.google.com/docs/reference/hosting/rest/v1beta1/projects.sites.customDomains).

## Evidence package layout

Keep evidence outside the Git checkout so source remains clean:

```text
cloud-release/
  cloud-release-evidence.json
  cloud-release-evidence.sig
  release-authority-public.pem
  evidence/
    cloud-readonly-observation-report.json  # live-readonly-audit reference
    evidence-manifest.json                  # exact report companion
    cloud-readonly-observation.tar          # exact report companion
    ...the other files named by evidenceReferences...
```

Each referenced or report-declared companion file must be a bounded regular
file beneath the evidence root. Symlinks, path traversal, missing/extra files,
digest or byte-count mismatches, duplicate IDs, stale or cross-associated
observations, and references that expire before the package are rejected. The
package is valid for at most 30 days. The live read-only audit and model
availability confirmation must each be no older than 24 hours.

The evidence files may be console exports, protected CLI exports, signed test
reports, reviews, runbooks, provenance, or approvals. Never put an HMAC keyring
value, service-account credential, OAuth client secret, refresh token, mobile
signing key, access token, Gemini provider key, raw contact, phone number,
birthday, message, or prompt in them.

## Required semantic coverage

The final approved package must bind all of the following, not merely attach a
screen capture:

1. Exact production project number/ID, Firebase Android/iOS app IDs, package and
   bundle IDs, `asia-south1` Functions/Firestore selection, all used-tier
   isolation, and the retained project's one explicit dev/staging/production
   assignment.
2. Exact Web/Android/iOS OAuth clients, Android SHA-1 channels, iOS team and
   reversed client ID, Google Auth lifecycle/deletion, one visible Google
   choice, People API `contacts.readonly`, partial/revocation behavior, refreshed
   client configs, and application/API restrictions on public Firebase keys.
3. Vertex-backed Firebase AI Logic with stable `gemini-3.5-flash` in `global`,
   `firebasevertexai.googleapis.com`, authenticated-users mode, Remote Config
   default Off/canonical-true activation, billing, provider quotas, per-user
   limits, kill switch, current model availability, approved terms/governance,
   monitoring Off, and no Generative Language API on mobile keys.
4. Play Integrity settings matched to the exact outside-Play, Play-only, or
   mixed distribution scope; the production App Attest provider and its
   reviewed supported-device policy;
   baseline enforcement; limited-use replay consumption for all state-changing
   callables—including iOS composer-reservation acquire/commit/release—and
   companion status; signed-channel Android/iOS AI/callable probes; and zero
   debug providers.
5. The exact 20 callable and two scheduled Functions, Node.js 22,
   `asia-south1`, the dedicated runtime identity, source revision, and exact
   timeout/memory/min/max/concurrency/schedule options. Request bodies and raw
   exceptions remain excluded.
6. Server-only Firestore deny-all rules, exact rule/index/TTL digests, all 13 TTL
   collection groups including top-level `iosComposerReservations`,
   transactional logical expiry, production contention/index
   evidence, recursive deletion and Auth absence, PITR disabled, zero backup
   schedules and retained backups, and a `HEALTHY` reviewed ledger generation.
7. `COORDINATION_HMAC_KEYRING` metadata only, distinct current/previous Secret
   Manager versions, reviewed replication/rotation, no repository or CI value,
   runtime-only access, and retention of the previous key for at least 400 days
   after its last affected write. A later rotation must not discard that key
   early.
8. Distinct keyless runtime and audit identities, zero user-managed keys, no
   primitive Owner/Editor or wildcard permission, and reviewed effective
   capability limited to coordination Firestore, Auth deletion, this one secret,
   App Check verification, and Scheduler invocation. Firestore deny rules do not
   substitute for IAM because server SDKs bypass those rules.
9. Callable/content/exception exclusions, AI monitoring Off, log sink review,
   bounded application and Data Access retention, restricted deletion-hash
   correlation, quota/budget owners, hard provider quotas, incident contacts,
   and explicit acknowledgment that budget alerts are alerts—not spending caps.
10. Exact Hosting site/version/URLs and source/release-config/deployment-config/
    public-tree/artifact/manifest/provenance digests, reCAPTCHA Enterprise App
    Check, security headers, EN/HI legal review, identity-verified external
    support, and production deletion-saga proof.
11. Absence of Realtime Database, Firebase/Cloud Storage **application-product
    use in the production Firebase project**, FCM, Analytics, ads,
    unapproved Crashlytics/Performance, raw-contact/message cloud storage, and a
    direct mobile Firestore path.
    `prohibitedServices.applicationProjectCloudStorageEnabled:false`
    has that application-project meaning. It does not describe the separately
    owned, non-product release-security admission bucket above, which is never
    linked into Firebase, shipped in client config, or accessible to app/runtime
    identities.
    The raw Firebase Management project observation must have no
    `resources.storageBucket` application-product resource. The application
    project's full GCS inventory is still retained because Functions/Cloud
    Build can own non-product operational buckets; their existence does not
    change this established field meaning. The separately numbered
    release-security project must contain exactly the one admission GCS bucket.
    A Logging bucket is a Cloud Logging resource and likewise does not change
    this application-project Cloud Storage assertion.
12. Continuity, regional failure, load/contention, deletion, rollback, incident,
    cost, and SLO evidence. This includes real account-global iOS composer
    reservation races against first Android registration and every Android
    sender mutation; exact-owner `PREPARED -> COMMITTED`; 72-hour logical
    expiry independent of delayed TTL; PREPARED-only exact early release;
    sticky Cancel/Failed/Sent/Unknown/crash/wipe behavior; immutable completed
    privacy-receipt replay; and account deletion dominating/removing the
    reservation. Disaster recovery must create a reviewed new ledger
    generation, force re-registration/reapproval, and suppress same-date
    automation. An empty ledger is never proof that no SMS was submitted, and
    the user ledger is never restored from a managed backup.

Firestore TTL is delayed physical cleanup, not authorization. Firebase explains
that expired documents can remain after expiry and that TTL does not delete
subcollections:
[Firestore TTL](https://firebase.google.com/docs/firestore/ttl). PITR and backup
state are therefore inspected independently:
[Firestore PITR](https://cloud.google.com/firestore/docs/use-pitr).

## Independent approval and validation

Seven distinct accountable approvers—backend, cloud owner, finance, privacy,
release, security, and SRE—must approve the same exact package and expiry. Each
approval has its own referenced evidence file. The semantic validator rejects a
single person reused across roles, pending decisions, mismatched expiry, or
missing approval evidence.

After the authority signs the final JSON's raw bytes, validate from the clean
release checkout:

```sh
npm run cloud:evidence:validate -- \
  --file /secure/cloud-release/cloud-release-evidence.json \
  --evidence-root /secure/cloud-release/evidence \
  --signature /secure/cloud-release/cloud-release-evidence.sig \
  --public-key /secure/cloud-release/release-authority-public.pem
```

For the production-closure package, add
`--report /secure/cloud-release/cloud-release-verification.json`. The
deterministic report requires the Google Play channel and projects its App
Check signing-certificate SHA-256, Android OAuth signing-certificate SHA-1,
web OAuth client ID, the iOS OAuth/reversed-client/team coordinates, and the
web Firebase app/reCAPTCHA Enterprise site-key digest. Those fields are not
free-form closure inputs: the final gate compares them with the physical Play
delivery report, the inspected mobile artifacts, and the exact Hosting release
config. The cloud report also projects the exact Hosting site/version and every
canonical artifact/manifest/provenance/config/tree digest, so the Hosting
composer cannot substitute an unrelated local archive.

Passing this validator means the supplied evidence package is internally
consistent, current, source-bound, file-bound, and signed by the pinned release
authority. It does not replace legal, Play SMS permission, App Store, physical
device/carrier, signed artifact, or human production change approval gates.

The checked-in iOS client constructs `AppAttestProvider`, and its signed release
gate requires the production App Attest entitlement. DeviceCheck or a custom
provider is therefore not an interchangeable console choice for this source.
Adopting one requires a reviewed client implementation, entitlement, device
matrix, signed-probe, schema, workflow, and evidence-contract change together.
