# RelateAI Changelog

## [Unreleased]
### Added
- Added root `features.md`, feature compliance Kiro specs, and a UI validation ledger.
- Enforced the biometric app lock at app cold start/resume when the Settings toggle is enabled, with localized lock and prompt text.

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
