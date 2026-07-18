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
- Opening Messages first commits a content-free, account-global server reservation that pauses every
  Android sender mutation for up to 72 hours. Cancel, Failed, reported Sent, Unknown, process death,
  sign-out, revoke, or local wipe does not release that committed hold; Android birthdays may be
  missed during it.
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
   providers are absent. Firebase AI Logic, the read-only companion-status callable, and all three
   composer-reservation callables enforce App Check and supported replay protection with consumed
   limited-use tokens.
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

### Account-global composer-reservation safety gate

The status callable is advisory preflight. Release evidence must prove that the authoritative
cross-platform fence is one top-level `iosComposerReservations/{uid}` document and that the iOS
client can access it only through authenticated `acquireIOSComposerReservation`,
`commitIOSComposerReservation`, and `releaseIOSComposerReservation` callables. The persisted fields
must remain limited to schema version, a domain-separated SHA-256 owner-capability key,
`PREPARED`/`COMMITTED` phase, reviewed ledger generation, and logical/cleanup timestamps. The random
capability UUID, raw account identifiers, contact/date/destination/phone/recipient/message/prompt,
proposal material, and MessageUI outcome must be absent from Firestore, logs, diagnostics, and
React Native.

Retain native tests and physical-device traces for the exact order:

1. Current foreground review revalidates the exact account, proposal, trusted People snapshot
   generation, one-use nonce, scene, reset safety, terminal marker, and content-free coexistence
   result.
2. Native iOS acquires and holds an exact short People-material lease, then acquires `PREPARED` with
   the protected, backup-excluded exact-account capability.
3. After a final recheck, iOS durably marks that local capability sticky before it calls commit. An
   ambiguous commit response fails closed and never presents MessageUI or permits early release.
4. Only an exact `COMMITTED` response permits protected `ComposerOpenCommitted` CAS, a final exact
   People-lease validation, and MessageUI presentation. The lease is released only after confirmed
   presentation.
5. The client dismisses to Unknown when the app resigns active and at least five minutes before
   server expiry using monotonic elapsed time with the complete acquire/commit round trips removed.

Each successful exact-owner acquisition must produce a server-authoritative logical deadline of
server time plus 72 hours; commit itself must not extend that response deadline. Delayed Firestore
TTL cleanup is never authority. Only an exact never-sticky `PREPARED` capability may release early,
and only after an exact `RELEASED` response. `COMMITTED`, ambiguous-sticky, Cancel, Failed, reported
Sent, Unknown, process death, sign-out, revoke, and local wipe remain fenced until logical expiry.
The same exact owner may revalidate/reuse its capability; another capability is refused. Because
wipe/corruption can lose the UUID on the same phone, user/support copy must say **another iPhone or
an earlier protected review**, never assert that the holder is a different device.

Every Android registration, lease, mode, claim, Arm/status seal, report, retry, transfer, and new or
in-progress destructive reset/release transaction must read the same reservation document and
refuse while live. Exact unexpired completed reset/release receipts remain replayable as immutable
privacy proof. Account deletion alone transactionally dominates and removes a live reservation.
The signed evidence must include real concurrent acquire-versus-first-registration and
acquire-versus-every-mutation tests, both serial outcomes, deletion races, logical-expiry behavior,
and production Firestore contention—not only pure/unit tests or emulator screenshots.

The final pre-Review message banner, Privacy inventory, public Privacy Notice, Terms, and support
copy must all disclose that opening Messages commits an account-wide hold, can pause Android for up
to 72 hours even after Cancel/Failed/Unknown, and may cause Android birthdays to be missed. A build
that implies Cancel releases the hold fails release.

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
   every supporting evidence file, and publishes the 14 primary files plus every raw scenario and
   performance-support file those primary JSON documents bind as assets on an access-controlled
   release in the separately administered evidence repository. It then replaces
   every false/sentinel value and signs the final JSON's exact bytes with the Ed25519 key whose
   public SPKI digest is committed in
   [`tools/distribution-authority-pin.json`](../tools/distribution-authority-pin.json). The private
   authority key remains outside GitHub and the signing environment.
3. `verify-authority-approved-artifacts` downloads the exact candidate run and the named private
   evidence-repository release through a read-only protected token. It runs only in the protected
   `ios-production-release` environment. It is the sole operation that can emit the
   `ios-production-release-evidence` package.

Configure `IOS_SUPPORTING_EVIDENCE_REPOSITORY` as the exact private `owner/repository` coordinate
and `IOS_SUPPORTING_EVIDENCE_READ_TOKEN` as a least-privilege, read-only secret in the protected
release environment. Supply the dedicated evidence-release tag through the workflow input and
protect that repository against tag or asset replacement. GitHub tags and release assets are
mutable locators, not trust anchors: any replacement still fails unless the complete exact inventory
matches the authority-signed bindings. Asset names must be the 14 normalized primary paths plus every
scenario `rawEvidenceReference` and both performance support paths; because GitHub release assets are
flat, use unique flat names for all of them. Missing, extra, linked, hard-linked, renamed,
size-mismatched, or digest-mismatched assets fail verification. A release tag or asset label is never
approval by itself: the signed document binds the exact support bytes, source revision, and inspected
candidate archive/IPA digests.

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
label. `--evidence-root` is mandatory. Its exact inventory is the 14 normalized primary files plus
every scenario raw file and the performance protocol/raw-results files derived from those primary
JSON documents; no other file or directory is accepted. The physical-device and accessibility
references are version-2 `tools/mobile-release-scenario-evidence.schema.json` documents, not prose
or a boolean assertion. Their unique required rows bind the source, IPA digest, exported certificate,
marketing/build version, physical iPhone/OS, observation time, scenario ID, passing result, exact raw
file path/digest/length, physical-device identity hash, installation source, collector/protocol, SIM,
composer, notification/application, Android-coexistence, no-background-SMS, carrier-claim, or
accessibility settings and human-review checkpoints as applicable. Observations older than 30 days,
future-dated rows, placeholders, duplicate/missing scenarios, missing raw bytes, or cross-artifact
rows fail. The iOS physical inventory includes the required no-SIM, single/dual-SIM/eSIM composer and
no-carrier-delivery-claim scenarios. Approval is valid for at most 90 days and may not outlive either
provisioning profile.

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
- composer reservation: exact-owner reuse, different/lost capability refusal, acquire and commit
  ambiguity, local-sticky-before-server-commit crash, PREPARED-only exact release, zero release after
  Cancel/Failed/Sent/Unknown, 72-hour logical expiry despite delayed TTL, monotonic five-minute-early
  and background dismissal, and first-registration/every-Android-mutation/deletion races;
- sign-out/revoke/local-wipe/account-deletion recovery: verified reservation-journal absence before
  cleanup markers retire, no server-release claim after local wipe, same-phone lost-capability copy,
  Android remaining paused through the live logical window, and completed privacy-receipt replay;
- offline/App Check/Auth/AI failures and deterministic non-AI templates remaining usable;
- diagnostics and support export proving private contact, phone, birthday, message, prompt,
  credential and provider-response data is absent.

Every MessageUI result is a composer outcome only. Test notes must not relabel system-reported Sent
as carrier delivery or assert which sender line or transport was used.

## Performance and accessibility evidence

Use the exact signed candidate and named reference iPhone. Bind raw traces and the candidate SHA to
the signed performance evidence described in `docs/PERFORMANCE_RELEASE_EVIDENCE.md`. The archive/IPA
gate parses that JSON and executes `tools/validate-performance-evidence.mjs`; a digest and approval
boolean without passing samples cannot authorize the artifact. At minimum,
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
