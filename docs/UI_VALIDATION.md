# RelateAI UI and Integration Validation

Last updated: 2026-06-11

This document records manual and automated UI validation for the feature compliance pass. The attached validation device discovered during planning is `1b87b5db`.

## Validation Legend

- Pass: validated successfully.
- Fail: validated and broken; requires a fix before feature completion.
- Blocked: prerequisite missing, such as account credentials, SIM/device capability, WhatsApp setup, notification permission, or exact-alarm permission.
- Not Run: still pending in this feature pass.

## Automated Baseline

| Check | Status | Evidence |
|---|---|---|
| Unit tests and lint | Pass | Latest complete full validation produced 253 unit tests, 0 failures, 0 errors, 0 skipped after the Chat History interaction pass. |
| Biometric lock compile/test | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.example.BiometricLockPolicyTest --no-configuration-cache`. |
| Automation scheduling/reminder targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest --tests com.example.core.automation.scheduler.EventReminderSchedulerTest :app:testDebugUnitTest --tests com.example.domain.usecase.GenerateMessageUseCaseTest --tests com.example.core.automation.workers.MessageGenerationWorkerTest --tests com.example.core.automation.workers.MessageDispatchWorkerTest --tests com.example.core.automation.workers.DailyTriggerWorkerTest --tests com.example.domain.usecase.SaveManualEventUseCaseTest --tests com.example.domain.usecase.DiscoverEventsUseCaseTest --no-configuration-cache`. |
| Background contact sync parity targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.core.automation.workers.ContactSyncWorkerTest --tests com.example.domain.usecase.SyncContactsUseCaseTest --no-configuration-cache`. |
| Gmail event-aware subject targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest --tests com.example.core.automation.sender.EmailSubjectBuilderTest :app:testDebugUnitTest --tests com.example.core.automation.workers.MessageDispatchWorkerTest --tests com.example.core.automation.AutomationPipelineTest --no-configuration-cache`. |
| Localization and helper-script targeted tests | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --tests com.example.tools.HelperScriptsTest --tests com.example.core.automation.workers.DailyTriggerWorkerTest --tests com.example.core.automation.workers.MessageGenerationWorkerTest --no-configuration-cache`. |
| Home dashboard interaction smoke | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.home.HomeScreenInteractionTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed 2 tests covering settings, readiness, analytics, activity history, style coach, AI Doctor, backup, planner contact navigation, and sync-error retry/dismiss controls. |
| Contact list interaction smoke | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.contacts.ContactListScreenInteractionTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed 2 tests covering search input, clear search, filter chips, sort chips, sync-error retry/dismiss refresh controls, and contact row navigation. |
| Messages inbox interaction smoke | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.messages.MessagesScreenInteractionTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed 4 tests covering tabs, search, channel filters, sort chips, pending row edit/approve/reject, reject confirmation, selection, approved revoke, failed retry, and approve/reject/retry bulk actions. |
| Wish Preview interaction smoke | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.wish.WishPreviewScreenInteractionTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed 2 tests covering back navigation, variant selection, draft editing, why-signals, feedback chips, regeneration, test-send, reject, approve, approved state, and missing-message error state. |
| Chat History interaction smoke | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.chat.ChatHistoryScreenInteractionTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed 2 tests covering populated sent-message history, back navigation, loading, empty, and error states. |
| Compose/instrumented UI smoke build | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit`. Added `MainActivityNavigationSmokeTest` for onboarding-to-auth and guest-mode bottom navigation. Debug builds use `com.aistudio.relateai.qxtjrk.debug` so validation can install side-by-side with an existing production-signed app. |
| Connected Compose UI smoke execution | Blocked | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.MainActivityNavigationSmokeTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` found device `1b87b5db`, installed/started `com.aistudio.relateai.qxtjrk.debug`, and reported `Starting 2 tests`. Progress remained at `0/2` for several minutes while `dumpsys activity top` showed `com.google.android.youtube` foregrounded and the RelateAI debug process backgrounded; the stuck wrapper was stopped with no test failures recorded. Live UI validation needs an idle, unlocked device that is not being used by another foreground app. |
| Full release-readiness validation | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit`, rerun after the Chat History interaction coverage change; build successful with 253 unit tests, 0 failures, 0 errors, 0 skipped. |
| Debug Android-test APK build | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebugAndroidTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed after the Chat History UI refactor. |

## Screen Interaction Checklist

| Area | Interactions to Validate | Status | Evidence |
|---|---|---|---|
| Splash | cold start routing to onboarding/auth/home | Blocked | `MainActivityNavigationSmokeTest` covers splash-to-onboarding and splash-to-home routing after build; connected run installed and started on device but stalled while another app was foregrounded. |
| Onboarding | continue, setup checklist, skip/finish routing | Blocked | Compose smoke test covers continue-to-auth path after build; connected run installed and started on device but stalled while another app was foregrounded. |
| Auth | Google sign-in, guest/developer bypass, auth errors | Blocked | Compose smoke test asserts Google sign-in and developer bypass actions after onboarding; live OAuth still requires account/device validation and an idle, unlocked device. |
| App shell | bottom tabs, permission rationale grant/not-now, back stack | Blocked | Compose smoke test covers permission rationale "Not now" and Home/Contacts/Events/Messages/Analytics bottom-nav clicks after build; connected run installed and started on device but stalled while another app was foregrounded. |
| Home | settings, analytics, activity, style, backup, setup links; dashboard cards; sync error retry/dismiss | Pass | `HomeScreenInteractionTest` composes a populated Home state and clicks settings, readiness, analytics, activity history, style coach, AI Doctor, backup, planner contact, retry, and dismiss controls. Live device visual validation still requires an idle, unlocked device. |
| Contacts | search, clear search, filters, sort, refresh, contact open | Pass | `ContactListScreenInteractionTest` covers search input, clear search, representative filter chip dispatch, representative sort chip dispatch, sync-error retry/dismiss refresh controls, and contact row navigation. Live visual validation with seeded contacts still requires an idle, unlocked device. |
| Contact Detail | back, memory, gifts, chat, generate wish, edit preferences, quick enrichment, switches/chips/text fields | Not Run | Pending seeded contacts. |
| Events | search, add event, contact picker, new contact, type chips, date fields, save/cancel, filters | Not Run | Pending idle, unlocked device. |
| Messages | tabs, search, filters, row selection, approve/reject/retry/revoke, bulk actions, preview open | Pass | `MessagesScreenInteractionTest` covers tab switching, search input, channel filters, sort chips, pending edit/approve/reject, reject confirmation, checkbox selection, approve/reject/clear bulk actions, approved edit/revoke/reject, failed retry, and failed bulk retry. Live generated-message visual validation still requires an idle, unlocked device. |
| Wish Preview | variants, edit text, feedback, regenerate, test send, approve, reject, back | Pass | `WishPreviewScreenInteractionTest` covers back navigation, variant selection, draft editing, why-signals, feedback chips, regenerate, test-send, reject, approve, approved state, and missing-message error state. Live SMTP test-send still requires configured Gmail credentials and an idle, unlocked device. |
| Analytics | activity link, export/share action, empty and populated states | Not Run | Pending data and device share sheet. |
| Activity History | search, type/date/status filters, open route | Not Run | Pending activity data. |
| Style Coach | sample text, analyze button, recent-message analysis, back | Not Run | Pending idle, unlocked device. |
| Memory Vault | add, category chips, pin/unpin, delete, validation | Not Run | Pending contact. |
| Gift Advisor | FAB, add gift dialog, cost validation, feedback buttons, delete, AI suggestions | Not Run | Pending contact and AI config. |
| Backup/Restore | passphrase visibility, export, restore, back, invalid passphrase states | Not Run | Pending document picker/device validation. |
| Settings | AI key, email settings, automation mode, quiet hours, biometric, reminders, channel blackout, sync, sign out | Not Run | Pending idle, unlocked device. |
| Biometric lock | enable toggle, background app, resume, authenticate, cancel/retry, unsupported-device messaging | Not Run | Policy and compile tests pass; live prompt still pending. |
| Automation Setup | diagnostics, settings/style/contacts/activity links, system-setting handoffs, dry run, email test | Not Run | Pending permissions and credentials. |
| Chat History | empty/populated states and back | Pass | `ChatHistoryScreenInteractionTest` covers populated sent-message history, back navigation, loading, empty, and repository-error states. Live visual validation with real sent-message data still requires an idle, unlocked device. |

## External Surfaces

| Surface | Status | Evidence |
|---|---|---|
| SMS live send/status | Blocked | Requires SIM/SMS-capable device, safe test recipient, credentials/permissions where relevant, and an idle, unlocked device. |
| WhatsApp Accessibility live send | Blocked | Requires WhatsApp installed, accessibility service enabled, unlocked device, safe test recipient, and an idle foreground state for automation. |
| Gmail SMTP live send | Blocked | Requires sender Gmail address/app password, generated test data, and an idle, unlocked device. |
| Google OAuth and People API sync | Blocked | Requires configured Google account/Firebase OAuth client, Contacts permission, and an idle, unlocked device. |
| Gemini live generation/classification | Blocked | Requires Gemini API key or authenticated Firebase Vertex path, generated test data, and an idle, unlocked device. |
| Notifications/action receivers | Blocked | Requires notification permission, generated pending/reminder data, and an idle, unlocked device. |
| Exact alarms/boot recovery | Blocked | Requires exact alarm permission, scheduling window, and an idle, unlocked device. |
| Widget | Blocked | Requires widget placement on launcher and an idle, unlocked device. |
| Dynamic shortcuts | Blocked | Requires launcher shortcut inspection on an idle, unlocked device. |
| Deep links | Blocked | Requires `adb shell am start` against the installed debug package on an idle, unlocked device. |
