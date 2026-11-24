# Platform Integration Matrix

## Android MethodChannel surface

**Source:** `android/app/src/main/kotlin/com/yashsomani/ai_birthday/MainActivity.kt`

Five channels are declared:

| Channel suffix | Observed methods | Responsibility | Verification status |
|---|---|---|---|
| `notifications` | `getInitialNotification`, `hasPermission`, `requestPermission`, `hasExactAlarmPermission`, `requestExactAlarmPermission`, `apply`, `cancelAll`, `testNotification` | Notification permission, exact alarm capability, scheduling and cancellation | Dart-side tests present; physical-device behavior not verified |
| `nano` | `currentState`, `startDownload`, `generate` | Gemini Nano / ML Kit GenAI prompt bridge | Fake platform tests exist; real device/model readiness not verified |
| `share` | Method handler exists; enumerate exact method contract in Dart/native caller audit | Native share handoff | Runtime/device verification open |
| `contacts` | `hasPermission`, `requestPermission`, `fetchDeviceContacts`, `isSupported` | Device contacts access | Service tests present; physical permission flow not verified |
| `sms_automation` | `hasSmsPermission`, `requestSmsPermission`, `scheduleSmsJob`, `cancelSmsJob`, `cancelAllSmsJobs`, `getSmsAttemptsLog` | SMS permission and scheduled automation adapter | Settings/service test mapping incomplete; manifest flavor and device behavior open |

## Flutter integration modules

| Integration | Source anchor | Current evidence | Open verification |
|---|---|---|---|
| Google/Firebase auth | `lib/features/auth/` | Auth controller and live gateway test files ran in Flutter CI | Live OAuth/account/session renewal |
| Play Billing | `lib/features/subscription/` | Subscription service and verification tests present | Store sandbox, purchase restore and server verification |
| Gemini API | `lib/features/ai/data/user_gemini_api_provider.dart` | Provider and router tests | Live API key/quota/network behavior |
| Gemini Nano | `lib/features/ai/data/gemini_nano_provider.dart`, Kotlin channel | Fake platform tests | Supported Android device + model download/inference |
| Firestore backup | `lib/features/sync/data/cloud_sync_service.dart` | Cloud sync test file present | Live project/rules/backup restore |
| WhatsApp | `lib/features/delivery/data/whatsapp_handoff_builder.dart` | Deep-link builder test | Installed app and user-confirmed handoff |
| SMS | `lib/features/delivery/data/sms_delivery_service.dart`, Kotlin channel | SMS service tests present | Build flavor, permissions, actual send constraints |
| Notifications | Kotlin notification channel and receiver | Scheduler/gateway tests present | Reboot, timezone, exact alarm, notification permission revocation |

## Build evidence update

The earlier Phase 2.1 workflow skipped Android builds after a Flutter test failure. The latest main-branch run [45245907678](https://github.com/yhsomani/AI-Birthday/actions/runs/45245907678) on commit 644571f68be024423376749fe8e50e1db02ff018 successfully built the Play and Personal release-flavor APK artifacts. The Play merged-manifest check confirmed android.permission.SEND_SMS was absent. These were CI build artifacts (the workflow labels the Play artifact unsigned); this does not establish a signed store-ready package, physical-device behavior, successful SMS delivery, or store-policy approval. Native permission lifecycle and device integration remain open.
