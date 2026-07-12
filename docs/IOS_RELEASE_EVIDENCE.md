# iOS Companion release evidence

Status: **required runbook; no production approval is represented by this file**

`PROJECT_ABOUT.md` is authoritative. This runbook turns its iOS gates into a repeatable evidence
package. A simulator build, an unsigned archive, or a passing unit test is candidate evidence only.
Birthday Autopilot may be submitted or described as production-ready on iPhone only after the
named owners approve every applicable item below for the exact archived build.

## Product boundary that release must preserve

- iOS is a **Companion Edition**. It schedules best-effort local reminders and opens Apple's
  foreground, editable `MFMessageComposeViewController` only after an explicit current user review.
- The user decides whether to tap Send. The app cannot send in the background, choose the sender
  line, observe the final edited recipient/body, guarantee SMS/MMS/iMessage routing, or prove carrier
  acceptance or delivery.
- A live or unresolved Android sender state suppresses every iOS composer proposal account-wide.
- Google identity, read-only People authorization, Firebase credentials, provider responses,
  protected proposals, and private diagnostics remain outside React Native JavaScript and logs.

Any binary, store text, screenshot, accessibility label, notification, support response, or privacy
declaration that contradicts those statements fails release.

## Exact candidate identity

Record these values from the archive and the provisioned consoles. Do not fill them from memory.

| Field        | Required evidence                                                                          |
| ------------ | ------------------------------------------------------------------------------------------ |
| Git source   | Clean immutable revision and reviewed change record                                        |
| Bundle       | `com.yashsomani.birthdayautopilot` for production                                          |
| Version      | Exact `CFBundleShortVersionString` and `CFBundleVersion`                                   |
| Archive      | SHA-256 of the exported `.xcarchive` and IPA                                               |
| Signing      | Team ID, distribution certificate SHA-256, provisioning profile UUID and expiry            |
| Entitlements | Extracted signed entitlements, App Check entitlement/environment, no unapproved capability |
| Firebase     | Exact project ID/number, Apple app ID, Google OAuth client and reversed client ID          |
| Toolchain    | Xcode 26.5, iOS SDK 26.5, Ruby 3.4.10, Bundler 4.0.15, CocoaPods 1.16.2                    |
| Dependencies | `Podfile.lock`, `Gemfile.lock`, npm lock, CycloneDX SBOMs, advisory/license results        |
| Privacy      | Approved inventory, policy/terms/support URLs, App Privacy answers and deletion workflow   |

The production `GoogleService-Info.plist` is injected from the protected release environment. The
copy gate must prove the exact bundle/project/client mapping. It is not committed or borrowed from
another tier.

## Provisioned-service gates

Release owners must retain dated, immutable evidence for all of the following:

1. The production Firebase/Google Cloud project, billing, quotas, budget alerts, IAM owners,
   incident contacts, and environment isolation are reviewed.
2. Firebase Auth Google sign-in, the exact iOS OAuth client and URL scheme, People API
   `contacts.readonly`, and the single-account binding work on a signed build.
3. The approved production App Check provider succeeds on every supported device class; debug
   providers are absent. Firebase AI Logic and the read-only companion-status callable enforce App
   Check and supported replay protection.
4. Vertex-backed Firebase AI Logic uses the reviewed stable model/location, receives no contact PII,
   has an operational kill switch, quota and spending controls, and AI monitoring remains Off unless
   the separately approved logging exclusion is proven first.
5. Direct Firestore client access remains denied; no Analytics, ads, FCM, Realtime Database, Cloud
   Storage, unapproved crash/performance collection, or service-account credential is present.
6. The public privacy, terms, support, and account-deletion resources are deployed at the reviewed
   HTTPS origin and match the implementation and App Store declarations.

### Gemini operational switch and privacy procedure

The only iOS client switch is the native Firebase Remote Config boolean
`gemini_suggestions_enabled`. The in-app default is **false**. Only an activated Remote Config
value whose source is remote and whose canonical value is exactly `true` enables a later Firebase
AI Logic request. Missing, default, static, malformed, uninitialized, or unavailable configuration
stays Off. Foreground authoring reads the already-activated cache and never waits for configuration
network I/O. A non-blocking refresh/activate is attempted at launch and on foreground entry with an
eight-second Firebase timeout, a ten-second app-owned completion fence, and a one-hour minimum
fetch interval. Real-time listeners and custom targeting signals are forbidden.

Remote Config fetch uses Firebase's native Installations token. The exact 12.15.0 CocoaPods graph
also includes Firebase Installations, Remote Config Interop, and AB Testing because Remote Config
depends on them; the app does not configure experiments or Analytics. That identifier, the switch
value, fetch status, project/provider details, prompts, messages, contacts, and credentials must
never cross React Native or enter application logs/support diagnostics. Do not enable Analytics,
audience targeting, experiments, or custom signals for this switch. Remote Config does not replace
request authorization: every enabled AI request must still pass the existing Firebase Auth/App
Check gate and use a limited-use App Check token. Reliable built-in English/Hindi templates remain
available when the switch or any AI dependency is unavailable.

Before enabling, retain the reviewed Remote Config template version/hash, exact unconditional
boolean parameter, Firebase project, approver, change ticket, signed-build App Check probe, quota,
budget, and deterministic-fallback evidence. For an incident, publish `false`, record its template
version/time/operator, disable or quota-stop Firebase AI Logic server-side when immediate containment
is required, and verify a foregrounded signed build observes Off within the documented cache window.
Re-enabling requires a new review; a console screenshot alone is not authorization.

Console screenshots alone are insufficient. Evidence must include a signed-build probe whose
bundle, certificate, App Check verdict, project, and request time can be correlated without logging
tokens, prompts, contact data, message text, or phone numbers.

## Automated candidate checks

Run from a clean checkout with the exact pinned toolchain and the production dependency graph:

```zsh
npm ci
bundle install
npm run ios:pods
npm run check
npm run doctor:ios
npm run security:native:ios
node --test tools/ios-*.test.mjs tools/gemini-native-contract.test.mjs
```

CI must additionally retain:

- hosted native XCTest results, not only Swift source parsing;
- Debug and minified Release simulator builds;
- the exact npm, CocoaPods, and Ruby locks plus JavaScript and CocoaPods SBOMs;
- the lock-bound OSV report, exact CocoaPods source map, verified trunk-podspec
  source count, empty ordinary-CI exception policy, and JavaScript license
  result;
- deterministic SHA-256 manifests for app bundles, archives, test results, locks, and SBOMs;
- a signed device archive/export verification job before distribution.

The release archive job must fail when signing, provisioning, Firebase configuration, App Check,
privacy approval, or evidence inputs are absent. An unsigned simulator compile smoke may never be
promoted or uploaded.

## Signed archive and IPA artifact gate

The manual [iOS signed artifact workflow](../.github/workflows/ios-release-evidence.yml) has two
deliberately separate operations:

1. `build-signed-candidate-not-release` runs only in the protected
   `ios-production-signing-candidate` environment. It requires the Apple distribution certificate,
   App Store profile, production Firebase plist, manual `app-store-connect` export options, privacy
   review switch, and authority-signed protected-store release history. It retains an archive ZIP,
   IPA, export options, locks, SBOMs, audit result, provenance manifest, and a clearly labeled
   `candidate-observation-not-release.json` for 14 days. A successful run is a **candidate**, not
   production-release approval.
2. After independent review, the release authority records the observed values in
   [`tools/ios-release-evidence.template.json`](../tools/ios-release-evidence.template.json), hashes
   every supporting evidence file, replaces every false/sentinel value, and signs the final JSON's
   exact bytes with the Ed25519 key whose public SPKI digest is committed in
   [`tools/distribution-authority-pin.json`](../tools/distribution-authority-pin.json). The private
   authority key remains outside GitHub and the signing environment.
3. `verify-authority-approved-artifacts` downloads the exact candidate run and a separately retained
   `ios-release-supporting-evidence` artifact. It runs only in the protected
   `ios-production-release` environment. It is the sole operation that can emit the
   `ios-production-release-evidence` package.

The verifier rejects an unprovisioned/mismatched authority key, dirty or different Git revision,
expired approval/profile, missing referenced bytes, or any mismatch in:

- the deterministic xcarchive tree digest, exact IPA and export-options digests;
- production bundle, version, build, iOS 15.1 device platform, embedded source revision, app binary,
  and embedded-framework manifest;
- production Firebase environment, project ID/number, Apple app ID, OAuth client/reversed scheme,
  protected config digest, and one-way API-key digest;
- archive/export certificate SHA-256, team, exact App Store profiles and expiry, application
  identifier, signed entitlements, `Info.plist`, and `PrivacyInfo.xcprivacy`;
- arm64-only application/framework binaries, production App Attest, complete data protection,
  absence of debug/forbidden capabilities, extra URL schemes, extensions, App Clips, Watch payloads,
  permissive ATS, local networking, document sharing, push, iCloud, app groups, VPN/network
  extensions, and other unapproved capabilities;
- SBOM, provenance, dependency audit, Firebase/App Check console review, protected-store and backup
  exclusion tests, physical-device, performance, accessibility, privacy, App Store submission,
  login-services, deletion, and incident/rollback evidence.

For `dependencyAudit`, the authority must follow the
[`native dependency advisory gate`](NATIVE_DEPENDENCY_ADVISORY_GATE.md) and
verify the exact `Podfile.lock`, CocoaPods SBOM, npm lock, checksum-bound
podspec-source map, OSV query identities/report, and exception-policy bytes.
OSV has no direct CocoaPods package mapping, so a zero result without the exact
SwiftURL/npm mapping, Maven/npm/Swift canaries, and complete podspec verification is
not acceptable. Any missing source, service failure, active finding, stale
mapping, or unauthorized exception blocks the archive even when native tests
pass.

On macOS, the same final check can be run without GitHub:

```zsh
npm run ios:release:verify -- \
  --archive /protected/BirthdayAutopilot.xcarchive \
  --ipa /protected/BirthdayAutopilot.ipa \
  --export-options /protected/ExportOptions.plist \
  --evidence /protected/ios-release-evidence.json \
  --signature /protected/ios-release-evidence.sig \
  --public-key /protected/release-authority-public.pem \
  --evidence-root /protected/supporting-evidence \
  --report /protected/verification-report.json
```

The script uses Apple's `/usr/bin/codesign`, `/usr/bin/security`, `/usr/bin/plutil`,
`/usr/bin/ditto`, `/usr/bin/unzip`, and `/usr/bin/lipo`. It verifies both signatures and profiles;
it does not trust filenames, a self-authored JSON file, an unsigned simulator app, or a workflow
label. Supporting-evidence references are normalized in-root regular files, never URLs or symlinks,
and their exact SHA-256 bytes must match the signed document. Approval is valid for at most 90 days
and may not outlive either provisioning profile.

The schema is machine-readable at
[`tools/ios-release-evidence.schema.json`](../tools/ios-release-evidence.schema.json). The checked-in
template is intentionally invalid (`replace-before-release`, zero digests, and false approvals), so
copying or signing it cannot accidentally authorize a release.

## Physical iPhone matrix

Name the supported iOS range and at least one older and one current physical iPhone. Include real
single-SIM, dual-SIM/eSIM, and no-SIM configurations where supported. Test English and Hindi, light
and dark appearance, 200% text/Dynamic Type, VoiceOver, reduced motion, increased contrast, and the
pseudo-RTL nonshipping harness.

For every device/OS row, retain repeatable results for:

- first setup, retained same-account reconnect, different-account rejection, sign-out retain,
  sign-out wipe, Google revoke, local erase, remote deletion, interrupted deletion and recovery;
- Contacts consent grant/deny/partial/revoke, pagination, 401 reconnect, 403, 429, expired sync
  token, deletion/tombstone, offline partial sync, process death, and a 10,000-contact dataset;
- protected-store migration/corruption/Keychain loss/backup exclusion, notification cancellation,
  reminder reconciliation races, timezone/date changes, and same-day reset suppression;
- notification not-determined/authorized/denied/provisional states, Focus or delayed visibility,
  civil-date coalescing, the bounded 60-request horizon, partial scheduling, exhaustion, reboot and
  app update;
- `canSendText` unavailable/available, explicit foreground review, stale review/nonce rejection,
  recipient/body edits, Cancel, Failed, system-reported Sent, lost delegate/process outcome Unknown,
  repeat suppression, and no automatic reopening;
- Android coexistence: no binding, live binding, unresolved occurrence, status unavailable, Android
  transfer/deletion, and proof that suppression is account-global;
- offline/App Check/Auth/AI failures and deterministic non-AI templates remaining usable;
- diagnostics and support export proving private contact, phone, birthday, message, prompt,
  credential and provider-response data is absent.

Every MessageUI result is a composer outcome only. Test notes must not relabel system-reported Sent
as carrier delivery or assert which sender line or transport was used.

## Performance and accessibility evidence

Use the exact signed candidate and named reference iPhone. Bind raw traces and the candidate SHA to
the signed performance evidence described in `docs/PERFORMANCE_RELEASE_EVIDENCE.md`. At minimum,
prove the shared cold/warm/search/normalization budgets plus:

- no-change reminder reconciliation at or below 2 seconds CPU time;
- 400-day planning and bounded notification replacement at or below 2 seconds wall time;
- ready composer presentation P95 at or below 1 second, excluding system animation;
- zero reproducible app-caused hang, main-thread network/database work, memory failure, or private
  data in traces.

Accessibility evidence must include human VoiceOver navigation of the complete four-step setup,
permission denial and repair, People selection, message approval, reminder activation, Home status,
composer review, privacy erase/deletion, and diagnostics. Automated labels and contrast checks are
supporting evidence, not a substitute for physical acceptance.

## App Store and legal evidence

Before upload, retain named approvals for:

- launch countries, personal-message/carrier-charge law, privacy and deletion behavior;
- App Review login-services rationale for Google-only identity as a Google Contacts client, or a
  formally approved identity change if Review does not accept that rationale;
- App Privacy answers, privacy manifest/reason APIs, export compliance, age rating, support contact,
  and all localized metadata/screenshots;
- store copy that says reminders and user-controlled Messages composer, never automatic iPhone SMS;
- external Messages, iCloud, carrier, recipient and backup copies that app deletion cannot remove;
- rollback, incident response, support escalation, account-deletion verification, and dependency
  vulnerability ownership.

TestFlight or App Store acceptance does not prove notification reliability, carrier behavior,
privacy correctness, or product safety; those independent gates still apply.

The exact IPA digest, bundle/version/build, App Privacy export, merged privacy manifest, localized
listing/screenshots, Google-specific-service rationale, MessageUI/notification notes, countries,
public URLs, accessibility evidence, reviewer materials, and App Review decisions must also pass the
independent [store submission evidence gate](STORE_SUBMISSION_EVIDENCE.md). That gate cannot replace
the signed archive/IPA and physical-iPhone evidence in this runbook.

## Approval record

The release evidence package must end with dated approvals from product, engineering, security,
privacy, legal/policy, accessibility/UX, operations/support, and release owners. Each approval binds
the exact source revision, archive/IPA hash, signing identity, bundle/version, Firebase project,
physical-device matrix, performance/accessibility evidence, SBOMs, privacy declarations, and store
metadata. Missing, expired, mutable, or mismatched evidence leaves the iOS production release
blocked.
