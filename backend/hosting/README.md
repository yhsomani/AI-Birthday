# Birthday Autopilot public site

This package builds the public Firebase Hosting surface for Birthday Autopilot.
It provides deterministic routes:

- `/` — product and public-resource entry point;
- `/delete/` — recent-Google-authenticated account-deletion request;
- `/privacy/` — data inventory, retention, sharing, and deletion boundaries;
- `/terms/` — product-specific personal-use and platform terms;
- `/support/` — help and the configured verified-support handoff.

The source is complete, but **there is no production deployment or deployable
release configuration in this repository**. The Firebase project, public URL,
developer identity, legal approvals, Hindi review, reCAPTCHA Enterprise site
key, and identity-verified support/admin workflow are external release gates.
The normal build deliberately omits `public/runtime-config.json`, so a preview
shows legal information but the deletion control fails closed. Firebase Hosting
predeploy runs `build:release`, which refuses to deploy unless every release
gate in the private config is explicitly satisfied.

## Security model

The deletion page:

- obtains its Firebase web configuration from the project-bound Firebase
  Hosting reserved endpoint `/__/firebase/init.json`;
- initializes reCAPTCHA Enterprise App Check before Auth or Functions use;
- keeps Firebase Auth persistence in memory only;
- requests no Google Contacts scope and never reads a Google access token;
- performs `reauthenticateWithPopup` immediately before deletion;
- calls `requestAccountDeletion` in `asia-south1` with
  `{contractVersion: 1, requestId: <canonical lowercase random UUIDv4>}`;
- requests a limited-use App Check token so the callable can consume it for
  replay protection;
- writes only the pending unlinkable UUIDv4 to this tab's temporary
  `sessionStorage` before submission, so an ambiguous result can be recovered
  after reload; it never uses a URL, local storage, cookie, analytics event, or
  log and clears the journal only after displaying exact `COMPLETED` or an
  explicit Clear action;
- accepts `STARTED`/`REPLAYED` only when outer and nested keys are exact, the
  echoed receipt matches, WebCrypto derives the same domain-separated request
  hash, timestamps are ordered safe integers, and an Android fence drain equals
  the tombstone drain;
- maps only fixed error/status classes and never logs or renders raw provider
  errors or callable responses;
- signs out after acceptance only after the SDK resolves and
  `auth.currentUser === null`; failure keeps the account identity visible and
  exposes a fixed retry action;
- describes its initial receipt as request acceptance, never deletion
  completion;
- checks `accountDeletionReceipt` only after verifying `auth.currentUser === null`,
  blocks concurrent sign-in while the lookup is in flight, and uses the same
  consumed-App-Check protection; the backend independently rejects any lookup
  carrying Firebase Auth; and
- accepts an explicitly pasted receipt without putting it in a URL, local
  storage, cookie, analytics event, or log, treating `NOT_FOUND` as unknown
  rather than success or failure.

The website has no analytics, ads, session replay, service worker, or
non-essential cookies. Firebase Auth, App Check, Hosting, and Functions still
process the minimum network/security metadata required for the secure request.
The CSP permits only the same-origin application plus the Google/Firebase
origins needed by Auth and reCAPTCHA. `same-origin-allow-popups` is intentional
for Google sign-in; pages remain non-embeddable.

## Exact release-config contract

Create the real config outside the repository and expose only its path through
`BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH`. Start from
`release-config.example.json`; never fill or commit that example.

| Field                                  | Contract                                                                                                                                            |
| -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `schemaVersion`                        | exactly `1`                                                                                                                                         |
| `publicBaseUrl`                        | provisioned public HTTPS origin; no local, reserved, credential-bearing, or placeholder URL                                                         |
| `developerDisplayName`                 | approved public legal/developer identity; never a product-name placeholder                                                                          |
| `supportUrl`                           | public HTTPS entry to a separately provisioned identity-verified support/admin-deletion workflow; must have a different origin from the static site |
| `recaptchaEnterpriseSiteKey`           | registered Firebase App Check reCAPTCHA Enterprise site key; it is public configuration, not a secret                                               |
| `privacyEffectiveDate`                 | reviewed `YYYY-MM-DD` date                                                                                                                          |
| `termsEffectiveDate`                   | reviewed `YYYY-MM-DD` date                                                                                                                          |
| `legalApprovalReference`               | non-placeholder private evidence reference; validated but never emitted publicly                                                                    |
| `privacyApprovalReference`             | non-placeholder private evidence reference; validated but never emitted publicly                                                                    |
| `hindiCopyApprovalReference`           | human-review evidence for the Hindi copy; validated but never emitted publicly                                                                      |
| `adminDeletionRunbookReference`        | reviewed operator/runbook evidence; validated but never emitted publicly                                                                            |
| `verifiedAdminDeletionWorkflowTested`  | exactly `true` only after disabled/lost-Google-account deletion is proven end to end                                                                |
| `productionFirebaseDeletionSagaTested` | exactly `true` only after the real authenticated callable, permit drain, recursive absence verification, Auth deletion, and repair path pass        |

The generated public `/runtime-config.json` contains exactly:

```json
{
  "schemaVersion": 1,
  "publicBaseUrl": "<provisioned HTTPS URL>",
  "developerDisplayName": "<approved public identity>",
  "supportUrl": "<verified-support HTTPS URL>",
  "recaptchaEnterpriseSiteKey": "<public Enterprise site key>",
  "privacyEffectiveDate": "YYYY-MM-DD",
  "termsEffectiveDate": "YYYY-MM-DD",
  "functionsRegion": "asia-south1"
}
```

Approval references and booleans are build evidence only and are not emitted.
The runtime file is generated, ignored by Git, served with `no-store`, and must
not contain a password, OAuth secret, token, service-account credential, HMAC
pepper, private key, or user identity.

## Local verification

Use the repository's exact Node.js 24.18.0 and npm 11.6.0 toolchain:

```sh
cd backend/hosting
npm ci
npm run check
```

`npm run build` creates a fail-closed preview without runtime configuration.
Do not use an App Check debug provider in a release build. If a local App Check
test is needed, follow Firebase's separate debug-provider process in a disposable
development project and never commit, print, or ship its debug token.

## Production provisioning and release evidence

Before setting either tested boolean to `true`:

1. Choose the explicit Firebase tier and Hosting site; do not deploy against the
   repository's demo project or a default/implicit project.
2. Register that web app for Firebase Auth and reCAPTCHA Enterprise App Check;
   authorize the exact Hosting/custom domain for Google sign-in; enforce App
   Check on Functions and grant the callable service identity the App Check
   Token Verifier role.
3. Provision the approved public developer identity, legal effective dates,
   legal/privacy review, and human-reviewed Hindi copy.
4. Provision a real identity-verification intake and least-privilege operator
   runbook for a disabled Firebase user or a person who lost Google access. The
   operator must invoke the same `DELETING` fence, frozen permit drain,
   recursive cleanup, absence verification, and Auth deletion. There is no
   immediate epoch-revocation shortcut.
5. Test `/delete/` end to end for an iOS-only account, an Android account with no
   permit, an Android account with a live permit, ambiguous client response,
   disabled/lost account, recursive-deletion repair, and final receipt handling.
6. Verify the public site has no analytics/cookie/session-replay requests, the
   CSP permits Auth/App Check but blocks unapproved origins, all routes pass
   keyboard/screen-reader/200%-zoom/contrast tests, and English and Hindi copy
   match approved evidence.
7. Put the same exact `/delete/` URL in Play Console, App Store privacy/support
   metadata, OAuth branding, the mobile Settings surface, and reviewer notes.

An authorized deployment then uses an explicit project and the private config:

```sh
cd backend
BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH=/secure/out-of-repository/release-config.json \
  npx firebase-tools deploy --project <explicit-tier-project-id> --only hosting
```

The configured support workflow is intentionally not implemented as an
unauthenticated static form. Collecting identity evidence without a protected
backend, restricted IAM, retention enforcement, and reviewed verification
procedure would create a new privacy and account-takeover risk.
