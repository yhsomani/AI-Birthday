# RelateAI React Native

RelateAI is the active React Native implementation. Product direction and implementation evidence are deliberately separate:

- `SSOT.md` is the only normative business/end-user product scope.
- `docs/product-reset/README.md` indexes the zero-based product, UX, business, and rebuild assessment.
- `docs/react-native-migration-status.md` reports current implementation status.
- `docs/feature-fssot.md` and `docs/feature-roadmap-analysis.md` are superseded historical scope inputs.

The active app entrypoint is `src/App.tsx`. Legacy Android/Gradle artifacts, if present in the repository, are archival migration references only and are not part of the React Native build, test, or release path.

## Run

Use Node 24.18.0 and npm 11.6.0. The repository pins both through `.nvmrc`, `.node-version`, `package.json`, Corepack, and npm's strict engine check. With `nvm`:

```bash
nvm use
corepack enable npm
npm --version # must print 11.6.0
npm ci
npm run start
```

Then open the app with Expo on Android or iOS. Web is not a supported release surface because protected persistence is native-only.

The checked-in production composition is local-only: AI requests use review-first local
templates and email uses explicit mail-app handoff. No authenticated provider-session
issuer is bundled, so setting an endpoint environment variable alone does not enable AI
or email backend calls.

The endpoint variables in `.env.example` are reserved for integration builds that inject
a tested short-lived `ProviderSessionSource`. Such a build must keep credentials on its
backend, send only the approved bounded payload, use HTTPS without embedded credentials
or private-network targets, and complete backend/session/device evidence before claiming
provider readiness. Every `EXPO_PUBLIC_` value is visible in the compiled client and must
never contain a secret.

## Release Config

The React Native replacement uses `app.json` for native identifiers and permissions and `eas.json` for EAS build profiles. SMS and WhatsApp currently use user-controlled deep-link/share handoff; the RN release config blocks direct SMS, SMS inbox, phone-log, phone-number, exact-alarm, and AccessibilityService permissions.

## Validate

```bash
node --import tsx src/config/releaseEvidenceCli.ts --source-only --fail-on-blockers
```

`npm test` runs the React Native source-contract suite without per-file process isolation so local and CI validation do not depend on child `node`/`esbuild` process launches.
`npm run test:native-prebuild` requires JDK 17 and an Android SDK. It creates an isolated temporary Expo project, runs a real Android prebuild, compiles the debug APK, and removes the fixture. JDK 21 alone is not sufficient because the React Native Gradle plugin requests a JDK 17 toolchain; relying on Foojay auto-provisioning can also fail on restricted or slow networks, so CI provisions Temurin 17 explicitly.
The checked-in `src/config/releaseEvidenceCli.ts` command executes typecheck, lint, formatting, thresholded coverage, native prebuild/debug compile, audit, Expo dependency, and diff checks itself. CI and release owners invoke that file directly so a modified package alias cannot replace the evidence generator. The CLI also validates the exact `release:evidence` package alias for local tooling. It writes the ignored external artifact `reports/react-native-release-evidence.json` with command exit codes, timing and output hashes plus the exact commit, dirty-state fingerprint, lockfile hash, and Node/npm versions. Environment variables cannot mark checks passed. `--fail-on-blockers` makes missing/failed proof, dirty source, unsafe release configuration, or other blockers fail the command.

`--source-only` is an explicitly limited assessment used by CI: missing signed builds, device smokes, and store-submission evidence remain visible warnings, and the report cannot be treated as production approval. A final production assessment is the default (or can be named with `--production`) and requires an external JSON array containing exactly the five signed Android/iOS build, Android/iOS device-smoke, and store-submission evidence items. Each `Attached` item must include a unique structured attachment with its owner, timestamp, credential-free HTTPS evidence URL, exact commit, working-tree fingerprint, app version, and applicable signed artifact hashes. Device-smoke attachments also identify the tested device/OS/run; store evidence identifies both platform submission records:

```bash
node --import tsx src/config/releaseEvidenceCli.ts --production --device-evidence=/path/to/device-evidence.json --fail-on-blockers
```

Any required item that is `Pending`, `Failed`, free-form rather than structured, bound to a different candidate/artifact, malformed, duplicated, or missing is a production blocker. Generated-native and legacy-artifact evidence is derived from the checkout and cannot be supplied by that file. A release owner must still open and verify the linked primary records; the JSON validator is not a substitute for external approval.

The generated report is not imported by the application and is never a prerequisite for typecheck, test, or bundle. Setup Check safely reports release evidence as unattached because operational CI/release evidence does not belong in a user runtime. GitHub CI proves the clean-checkout order, uploads the explicitly source-only report, and attests it on protected-branch pushes.

## Current Implementation Scope

The user interface is intentionally temporary. `src/App.tsx` exposes one unstyled,
bounded JSON command input plus redacted readiness, operation, issue, and entity-count
output. It exists only to execute and validate the product behavior while the final UI
is redesigned separately in Figma. It contains no product layout, theme, animation,
icon system, or visual polish that the later UI must preserve.

The implementation behind that console remains local-first and includes historical
feature coverage recorded in the superseded feature documents: contacts and conflict-aware imports, relationship
events and reminders, review-first AI or local-template drafting, message approval and
delivery recovery, explicit manual channel handoff, memories and enrichment context,
check-ins and follow-ups, privacy and permission controls, deep-link/navigation state,
home planning, diagnostics, derived relationship analytics, redacted summary sharing,
confirmed CSV export, selected idempotent calendar export, encrypted backup/restore,
and protected persistence. Goal-specific onboarding cannot bypass required choices;
scheduled work is returned to review after a device time-zone change; and advanced bulk
message actions use a short-lived preview/confirm boundary with live eligibility checks.
Risky or de-emphasized roadmap features are kept out of the primary runtime surface;
commands may expose bounded validation hooks where release testing requires them.

Run `{"type":"system.catalog"}` to inspect the exhaustive command vocabulary and
review-first workflow examples. Successful non-secret commands clear the command input;
failed non-secret commands remain editable for retry. Secure input is always cleared and
is accepted only through the `$SECURE_INPUT` placeholder.

All state-changing commands run through deterministic domain transitions, verified
durable commits, operation serialization/cancellation, native reconciliation, and
redacted error reporting. Private state is not shown while biometric locking is active.
External AI or email requests remain disabled in the checked-in composition. Integration
builds require a short-lived authenticated provider session; provider credentials and
session tokens are never persisted, exported, displayed, or logged. Native contact,
calendar, notification, widget, backup, and handoff bridges are
invoked only after the corresponding user intent and product guardrails succeed.

The current source tests define implemented behavior; they do not prove business value or
authorize the previous feature breadth. The command console is not a replacement UX
specification. Future Figma work should follow the normative journeys and information
architecture in `SSOT.md`. The documents under `docs/product-reset/` provide supporting
analysis only where they are consistent with the SSOT. Preserve only reviewed safety
invariants that remain relevant to the narrower product.

Release evidence generation executes the required checks, binds results to source and
toolchain provenance, records native identifiers/EAS profiles/permission policy, and
keeps the artifact outside the runtime application.

Legacy Android/Gradle artifacts are not an active project surface. Any reintroduced legacy path is release drift and must be resolved before signing.

See `docs/react-native-migration-status.md` for current implementation evidence and
`docs/product-reset/README.md` for the rebuild decision and next product work.
