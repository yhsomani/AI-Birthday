# Production Firebase and Google Cloud release evidence

The repository now has an executable, fail-closed cloud release gate. It does
not deploy anything, provision infrastructure, infer production identifiers, or
claim that any Firebase/Google Cloud console gate has passed.

The gate has four parts:

- `tools/cloud-release-evidence.schema.json` is the strict structural contract;
- `tools/cloud-release-evidence.template.json` enumerates required fields and
  evidence, but is deliberately unusable because its decision is pending and
  all external coordinates, observations, reviews, and approvals are null;
- `tools/validate-cloud-release-evidence.mjs` applies source, semantic, expiry,
  file-digest, cross-field, and release-authority signature checks;
- `.github/workflows/cloud-readonly-evidence.yml` is an operator-triggered,
  protected-environment observation workflow. It can collect a subset of
  current console state, but its artifact explicitly says
  `mutationAuthorized:false` and cannot approve a release.

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

It creates a source-bound evidence manifest, packages the manifest plus raw
observations into a deterministic `cloud-readonly-observation.tar`, records its
SHA-256, and uploads a protected 14-day artifact. Preserve the protected GitHub
artifact in the release record, but copy only that tar file into the final
evidence root and reference the tar as `live-readonly-audit`; the validator
rejects unreferenced loose files. The tar is observation, not proof of
signed-channel App Check success, replay rejection, deletion, IAM least
privilege, AI monitoring privacy, or service SLOs.

The workflow must never gain a deploy, create, patch, update, delete, restore,
import, export, enable, disable, Secret Manager access, request-body, or other
remote mutation command. `tools/cloud-readonly-workflow-contract.test.mjs`
enforces that boundary.

## Evidence package layout

Keep evidence outside the Git checkout so source remains clean:

```text
cloud-release/
  cloud-release-evidence.json
  cloud-release-evidence.sig
  release-authority-public.pem
  evidence/
    ...the 42 files named by evidenceReferences...
```

Each referenced file must be a bounded regular file beneath the evidence root.
Symlinks, path traversal, missing files, digest mismatches, duplicate IDs, stale
observations, and references that expire before the package are rejected. The
package is valid for at most 30 days. Model-availability confirmation must be no
older than 24 hours.

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
   callables and companion status; signed-channel Android/iOS AI/callable probes;
   and zero debug providers.
5. The exact 17 callable and two scheduled Functions, Node.js 22,
   `asia-south1`, the dedicated runtime identity, source revision, and exact
   timeout/memory/min/max/concurrency/schedule options. Request bodies and raw
   exceptions remain excluded.
6. Server-only Firestore deny-all rules, exact rule/index/TTL digests, all 12 TTL
   collection groups, transactional logical expiry, production contention/index
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
10. Exact Hosting site/version/URLs and source/deployed/release-config digests,
    reCAPTCHA Enterprise App Check, security headers, EN/HI legal review,
    identity-verified external support, and production deletion-saga proof.
11. Absence of Realtime Database, Cloud Storage product use, FCM, Analytics, ads,
    unapproved Crashlytics/Performance, raw-contact/message cloud storage, and a
    direct mobile Firestore path.
12. Continuity, regional failure, load/contention, deletion, rollback, incident,
    cost, and SLO evidence. Disaster recovery must create a reviewed new ledger
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

Passing this validator means the supplied evidence package is internally
consistent, current, source-bound, file-bound, and signed by the pinned release
authority. It does not replace legal, Play SMS permission, App Store, physical
device/carrier, signed artifact, or human production change approval gates.

The checked-in iOS client constructs `AppAttestProvider`, and its signed release
gate requires the production App Attest entitlement. DeviceCheck or a custom
provider is therefore not an interchangeable console choice for this source.
Adopting one requires a reviewed client implementation, entitlement, device
matrix, signed-probe, schema, workflow, and evidence-contract change together.
