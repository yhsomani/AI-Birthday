# RelateAI Changelog

## [Unreleased]
### Added
- Added root `features.md`, feature compliance Kiro specs, and a UI validation ledger.
- Enforced the biometric app lock at app cold start/resume when the Settings toggle is enabled, with localized lock and prompt text.
- Added a shared automation scheduling policy for contact custom send times, quiet-hour deferral, blackout-date deferral, reminder timing, and channel-block parsing.
- Added AlarmManager-backed event reminder scheduling from `notifyDaysBefore`, with boot/daily rescheduling and reminder-toggle guards.
- Added event-aware Gmail SMTP subject generation for birthdays, anniversaries, work anniversaries, custom events, and SMTP test emails.
- Added localization parity and helper-script portability regression tests.
- Added Compose instrumented smoke coverage for onboarding/auth routing and app-shell bottom navigation.
- Added a side-by-side debug package (`com.aistudio.relateai.qxtjrk.debug`) and matching debug Firebase config so UI validation can run without uninstalling an existing production-signed app.
- Added Home dashboard Compose/Robolectric interaction coverage for dashboard links, planner navigation, and sync-error retry/dismiss controls.
- Added Contact List Compose/Robolectric interaction coverage for search, clear search, filters, sort, sync-error actions, and row navigation.
- Added Messages inbox Compose/Robolectric interaction coverage for tabs, search, filters, sort, row actions, reject confirmation, selection, and approve/reject/retry bulk actions.
- Added Wish Preview Compose/Robolectric interaction coverage for back navigation, variants, editing, why-signals, feedback, regeneration, test-send, approval, rejection, and terminal states.
- Added Chat History Compose/Robolectric interaction coverage for populated sent-message history, back navigation, loading, empty, and error states.
- Added Analytics Compose/Robolectric interaction coverage for activity navigation, export, loading, empty, populated dashboard, and disabled export states.
- Added cache-backed FileProvider CSV attachment sharing for Analytics exports, with a readable attachment regression test.
- Added Activity History Compose/Robolectric interaction coverage for search, type/date/status filters, action-route opening, back navigation, loading, empty, and error states.
- Added Style Coach Compose/Robolectric interaction coverage for sample entry, manual analysis, recent-message analysis, back navigation, learned profile/history rendering, empty history, loading, error, and disabled busy states.
- Added Memory Vault Compose/Robolectric interaction coverage for note entry, category chips, add, pin, unpin, delete, back navigation, loading, empty, error, and add-button validation states.
- Added Gift Advisor Compose/Robolectric interaction coverage for AI suggestions, record dialog entry, feedback buttons, save, delete, back navigation, loading, empty, error, generating, and validation states.
- Added Backup/Restore Compose/Robolectric interaction coverage for passphrase entry, visibility, export, restore, back, dismiss, invalid, loading, success, and error states.
- Added selected-document encrypted backup export/import regression coverage.

### Changed
- Notification backup reminder and AI fallback alert copy now uses localized string resources, and critical Hindi notification labels were refreshed.
- `scripts/extract_strings.sh` now detects the repository root dynamically instead of assuming `/workspace`.
- Background contact sync now runs through the same Google + device merge, relationship normalization, mock cleanup, and event-discovery path as foreground sync.
- Automatic message generation now skips contacts with `skipAutoWish`, while manual generation forces review instead of auto-send for those contacts.
- Dispatch workers now defer approved sends that become due during quiet hours or blackout dates.
- Analytics export now shares an actual `text/csv` attachment instead of only placing CSV text in the share intent body.
- Activity History now exposes repository load failures, and search also matches severity, status, and action routes.
- Activity History Hindi search, action, AI, and status labels were refreshed.
- Style Coach Hindi copy was refreshed for the screen title, training controls, profile metrics, history labels, and status/error text.
- Memory Vault category chips now wrap across rows on narrow screens, and touched Hindi Memory Vault labels were refreshed.
- Gift Advisor add-record dialog now scrolls on small screens, and touched Hindi Gift Advisor labels were refreshed.
- Backup/Restore export and restore actions now stack on narrow screens and both actions stay disabled while an export/import is in progress.

### Validation
- Full local validation passes with 277 unit tests, lint, debug assemble, and JaCoCo report.
- Debug Android-test APK build passes after the Backup/Restore UI test refactor.
- Connected UI smoke execution now targets the side-by-side debug package. Device `1b87b5db` installed and started the test run, but it stalled at 0/2 tests while another app was foregrounded; live UI validation needs an idle, unlocked device.

## [1.0.0] - 2026-06-08
### Added
- **SMS Delivery Tracking & Confirmation**: Created `SmsStatusReceiver` for dynamic sent and delivered broadcast confirmations. Integrated unique request code hashing based on message ID in `SmsSender` to prevent intent collisions.
- **Robust Exception Handling**: Wrapped SMS dispatching and WhatsApp fallback routes with safety try-catch blocks to prevent background worker crashes when SMS permissions are missing.
- **UI Screens**: Built Jetpack Compose screens for `HomeScreen`, `ContactListScreen` (pull-to-refresh), `ContactDetailScreen`, `EventsScreen`, `MessagesScreen` (4-tab pager for Today, Pending, Sent, Failed), `AnalyticsScreen` (real SentMessageDao chart data), `SettingsScreen` (Gemini key configuration), `StyleCoachScreen`, `BackupRestoreScreen`, `MemoryVaultScreen`, and `GiftAdvisorScreen`.
- **Permissions**: Integrated runtime permission flows in `MainActivity.kt` requesting `SEND_SMS` and `POST_NOTIFICATIONS` at app startup.
- **Database Schema v11**: Incremented Room database schema version to 11 with custom migration pathways (v4 to v11), adding budget columns, fallback tracking flags, and strict table-level uniqueness constraints.
- **Google Sync Improvements**: Clears mock contacts proactively upon Google Sign-In and refreshes contacts with incremental paging tokens and retry triggers.
- **Automated Testing Suite**: Expanded tests to 99 unit/integration tests covering all UseCases, WorkManager background workers, database migrations, and SMS delivery tracking broadcasts. All tests pass successfully.

## [0.1.0] - 2026-06-07
### Added
- **Security Enhancements**: Moved SQLCipher database encryption keys to `EncryptedSharedPreferences`, established secure sign-out cache wiping, separated `SecurePrefs` authentication namespaces, and sanitized PII from Gemini prompts.
- **Automation Engine**: Implemented duplicate message guards and leap-year event logic within `EventDiscoveryWorker` and `MessageGenerationWorker`. Added WhatsApp sending accessibility validation loop.
- **Style Coach**: Configured writing style profiling using historical message analysis.
