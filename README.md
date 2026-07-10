# RelateAI React Native

RelateAI is the active React Native app for the migration target. It follows:

- `docs/feature-fssot.md`
- `docs/feature-roadmap-analysis.md`
- `docs/README.md`

The active app entrypoint is `src/App.tsx`. Legacy Android/Gradle artifacts, if present in the repository, are archival migration references only and are not part of the React Native build, test, or release path.

## Run

Use Node 24.18.0 and npm 11.6.x. The repository pins both through `.nvmrc`, `.node-version`, `package.json`, and npm's strict engine check. With `nvm`:

```bash
nvm use
npm ci
npm run start
```

Then open the app with Expo on Android, iOS, or web.

AI drafting can call a secure backend endpoint when one is configured:

```bash
EXPO_PUBLIC_RELATE_AI_ENDPOINT=https://your-secure-backend.example.com/draft npm run start
```

The React Native client sends only the approved drafting payload and must not contain provider secrets. Every `EXPO_PUBLIC_` value is visible in the compiled client, so use `.env.example` only for endpoint URLs and non-secret development switches. When the endpoint is missing, unreachable, or returns an invalid response, the app falls back to a review-first local template.
Release-ready provider endpoints must use HTTPS, must not embed credentials in the URL, and must not point to localhost or private-network hosts. Local development endpoints can be tested only when explicitly allowed:

```bash
EXPO_PUBLIC_RELATE_ALLOW_LOCAL_PROVIDER_ENDPOINTS=true EXPO_PUBLIC_RELATE_AI_ENDPOINT=http://localhost:8787/draft npm run start
```

Email delivery can also use a secure backend endpoint for approved email messages:

```bash
EXPO_PUBLIC_RELATE_EMAIL_ENDPOINT=https://your-secure-backend.example.com/email/send npm run start
```

The app stores only the sender email preference. Provider credentials should stay on the backend.

## Release Config

The React Native replacement uses `app.json` for native identifiers and permissions and `eas.json` for EAS build profiles. SMS and WhatsApp currently use user-controlled deep-link/share handoff; the RN release config blocks direct SMS, SMS inbox, phone-log, phone-number, exact-alarm, and AccessibilityService permissions.

## Validate

```bash
npm run typecheck
npm test
npm run test:native-prebuild
npm audit --audit-level=moderate
npx expo install --check
npx expo export --platform web --output-dir reports/web-export
git diff --check
npm run release:evidence -- --fail-on-blockers
```

`npm test` runs the React Native source-contract suite without per-file process isolation so local and CI validation do not depend on child `node`/`esbuild` process launches.
`npm run test:native-prebuild` requires JDK 17 and an Android SDK. It creates an isolated temporary Expo project, runs a real Android prebuild, compiles the debug APK, and removes the fixture. JDK 21 alone is not sufficient because the React Native Gradle plugin requests a JDK 17 toolchain; relying on Foojay auto-provisioning can also fail on restricted or slow networks, so CI provisions Temurin 17 explicitly.
`npm run release:evidence` executes the typecheck, test, native prebuild/debug compile, audit, Expo dependency, web export, and diff checks itself. It writes the ignored external artifact `reports/react-native-release-evidence.json` with command exit codes, timing and output hashes plus the exact commit, dirty-state fingerprint, lockfile hash, and Node/npm versions. Environment variables cannot mark checks passed. `--fail-on-blockers` makes missing/failed proof, dirty source, unsafe release configuration, or other blockers fail the command.

The generated report is not imported by the application and is never a prerequisite for typecheck, test, or bundle. Setup Check safely reports release evidence as unattached because operational CI/release evidence does not belong in a user runtime. GitHub CI proves the clean-checkout order, uploads the report, and attests it on protected-branch pushes.

## Current React Native Scope

The user interface is intentionally temporary. `src/App.tsx` exposes one unstyled,
bounded JSON command input plus redacted readiness, operation, issue, and entity-count
output. It exists only to execute and validate the product behavior while the final UI
is redesigned separately in Figma. It contains no product layout, theme, animation,
icon system, or visual polish that the later UI must preserve.

The implementation behind that console remains local-first and includes the behavior
defined by the feature documents: contacts and conflict-aware imports, relationship
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
External AI or email requests require a short-lived authenticated provider session;
provider credentials and session tokens are never persisted, exported, displayed, or
logged. Native contact, calendar, notification, widget, backup, and handoff bridges are
invoked only after the corresponding user intent and product guardrails succeed.

The feature documents define behavior; the command console is not a replacement UX
specification. The future Figma implementation must call the same application services
without weakening confirmations, permission rationale, review-first rules, privacy
boundaries, or recovery semantics.

Release evidence generation executes the required checks, binds results to source and
toolchain provenance, records native identifiers/EAS profiles/permission policy, and
keeps the artifact outside the runtime application.

Legacy Android/Gradle artifacts are not an active project surface. Any reintroduced legacy path is release drift and must be resolved before signing.

See `docs/react-native-migration-status.md` for the current parity status and remaining replacement work.
