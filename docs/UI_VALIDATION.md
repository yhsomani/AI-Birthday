# RelateAI UI and Integration Validation

Last updated: 2026-06-10

This document records manual and automated UI validation for the feature compliance pass. The attached validation device discovered during planning is `1b87b5db`.

## Validation Legend

- Pass: validated successfully.
- Fail: validated and broken; requires a fix before feature completion.
- Blocked: prerequisite missing, such as account credentials, SIM/device capability, WhatsApp setup, notification permission, or exact-alarm permission.
- Not Run: still pending in this feature pass.

## Automated Baseline

| Check | Status | Evidence |
|---|---|---|
| Unit tests and lint | Pass | Full validation produced 241 unit tests, 0 failures, 0 errors, 0 skipped. |
| Biometric lock compile/test | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.example.BiometricLockPolicyTest --no-configuration-cache`. |
| Automation scheduling/reminder targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest --tests com.example.core.automation.scheduler.EventReminderSchedulerTest :app:testDebugUnitTest --tests com.example.domain.usecase.GenerateMessageUseCaseTest --tests com.example.core.automation.workers.MessageGenerationWorkerTest --tests com.example.core.automation.workers.MessageDispatchWorkerTest --tests com.example.core.automation.workers.DailyTriggerWorkerTest --tests com.example.domain.usecase.SaveManualEventUseCaseTest --tests com.example.domain.usecase.DiscoverEventsUseCaseTest --no-configuration-cache`. |
| Background contact sync parity targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.core.automation.workers.ContactSyncWorkerTest --tests com.example.domain.usecase.SyncContactsUseCaseTest --no-configuration-cache`. |
| Gmail event-aware subject targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest --tests com.example.core.automation.sender.EmailSubjectBuilderTest :app:testDebugUnitTest --tests com.example.core.automation.workers.MessageDispatchWorkerTest --tests com.example.core.automation.AutomationPipelineTest --no-configuration-cache`. |
| Localization and helper-script targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --tests com.example.tools.HelperScriptsTest --tests com.example.core.automation.workers.DailyTriggerWorkerTest --tests com.example.core.automation.workers.MessageGenerationWorkerTest --no-configuration-cache`. |
| Compose/instrumented UI smoke build | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebugAndroidTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit`. Added `MainActivityNavigationSmokeTest` for onboarding-to-auth and guest-mode bottom navigation. |
| Connected Compose UI smoke execution | Blocked | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.MainActivityNavigationSmokeTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` found device `1b87b5db` but failed before running tests with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the installed `com.aistudio.relateai.qxtjrk` package is signed with a different certificate. Existing app data was not removed. |
| Full release-readiness validation | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit`, build successful. |

## Screen Interaction Checklist

| Area | Interactions to Validate | Status | Evidence |
|---|---|---|---|
| Splash | cold start routing to onboarding/auth/home | Blocked | `MainActivityNavigationSmokeTest` covers splash-to-onboarding and splash-to-home routing after build; connected run blocked by installed package signature mismatch. |
| Onboarding | continue, setup checklist, skip/finish routing | Blocked | Compose smoke test covers continue-to-auth path after build; connected run blocked by installed package signature mismatch. |
| Auth | Google sign-in, guest/developer bypass, auth errors | Blocked | Compose smoke test asserts Google sign-in and developer bypass actions after onboarding; live OAuth still requires account/device validation and connected run is blocked by signature mismatch. |
| App shell | bottom tabs, permission rationale grant/not-now, back stack | Blocked | Compose smoke test covers permission rationale "Not now" and Home/Contacts/Events/Messages/Analytics bottom-nav clicks after build; connected run blocked by signature mismatch. |
| Home | settings, analytics, activity, style, backup, setup links; dashboard cards; sync error retry/dismiss | Not Run | Pending debug install. |
| Contacts | search, clear search, filters, sort, refresh, contact open | Not Run | Pending seeded contacts. |
| Contact Detail | back, memory, gifts, chat, generate wish, edit preferences, quick enrichment, switches/chips/text fields | Not Run | Pending seeded contacts. |
| Events | search, add event, contact picker, new contact, type chips, date fields, save/cancel, filters | Not Run | Pending debug install. |
| Messages | tabs, search, filters, row selection, approve/reject/retry/revoke, bulk actions, preview open | Not Run | Pending generated messages. |
| Wish Preview | variants, edit text, feedback, regenerate, test send, approve, reject, back | Not Run | Pending generated message and SMTP config for live test. |
| Analytics | activity link, export/share action, empty and populated states | Not Run | Pending data and device share sheet. |
| Activity History | search, type/date/status filters, open route | Not Run | Pending activity data. |
| Style Coach | sample text, analyze button, recent-message analysis, back | Not Run | Pending debug install. |
| Memory Vault | add, category chips, pin/unpin, delete, validation | Not Run | Pending contact. |
| Gift Advisor | FAB, add gift dialog, cost validation, feedback buttons, delete, AI suggestions | Not Run | Pending contact and AI config. |
| Backup/Restore | passphrase visibility, export, restore, back, invalid passphrase states | Not Run | Pending document picker/device validation. |
| Settings | AI key, email settings, automation mode, quiet hours, biometric, reminders, channel blackout, sync, sign out | Not Run | Pending debug install. |
| Biometric lock | enable toggle, background app, resume, authenticate, cancel/retry, unsupported-device messaging | Not Run | Policy and compile tests pass; live prompt still pending. |
| Automation Setup | diagnostics, settings/style/contacts/activity links, system-setting handoffs, dry run, email test | Not Run | Pending permissions and credentials. |
| Chat History | empty/populated states and back | Not Run | Pending sent-message data. |

## External Surfaces

| Surface | Status | Evidence |
|---|---|---|
| SMS live send/status | Blocked | Requires SIM/SMS-capable device, safe test recipient, and debug install; connected install is blocked by signature mismatch. |
| WhatsApp Accessibility live send | Blocked | Requires WhatsApp installed, accessibility service enabled, unlocked device, safe test recipient, and debug install; connected install is blocked by signature mismatch. |
| Gmail SMTP live send | Blocked | Requires sender Gmail address/app password and debug install; connected install is blocked by signature mismatch. |
| Google OAuth and People API sync | Blocked | Requires configured Google account/Firebase OAuth client and debug install; connected install is blocked by signature mismatch. |
| Gemini live generation/classification | Blocked | Requires Gemini API key or authenticated Firebase Vertex path plus debug install; connected install is blocked by signature mismatch. |
| Notifications/action receivers | Blocked | Requires notification permission, generated pending/reminder data, and debug install; connected install is blocked by signature mismatch. |
| Exact alarms/boot recovery | Blocked | Requires exact alarm permission, scheduling window, and debug install; connected install is blocked by signature mismatch. |
| Widget | Blocked | Requires widget placement on launcher and debug install; connected install is blocked by signature mismatch. |
| Dynamic shortcuts | Blocked | Requires launcher shortcut inspection and debug install; connected install is blocked by signature mismatch. |
| Deep links | Blocked | Requires `adb shell am start` against a successfully installed debug package; connected install is blocked by signature mismatch. |
