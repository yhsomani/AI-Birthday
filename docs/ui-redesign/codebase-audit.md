# AI-Birthday Codebase & UI Architectural Audit

> This is a source snapshot, not evidence of live service deployment or current-HEAD test results. The navigation and automatic-SMS statements below were reconciled during the Android-first rebuild; visual references remain historical.

## Executive Summary
This document provides an exhaustive technical audit of the **AI-Birthday** Flutter/Android codebase, mapping every user-facing surface, navigation flow, Riverpod state provider, Drift/SQLite entity, and Android system boundary.

## Discovered Routes & Screens
| Screen Name | Route / Modal Entry | Path / Presentation | Primary Role & State Bindings |
|---|---|---|---|
| **OnboardingScreen** | `/onboarding` | Full-screen walkthrough (`OnboardingScreen`) | 3-slide value-prop introducing AI-Birthday, privacy pledge, notification setup. Guarded by `credentialStorageProvider.hasCompletedOnboarding()`. |
| **DashboardScreen** | `/dashboard` | StatefulShell tab 0 (`DashboardScreen`, labeled Today) | Today's birthdays, action queue, upcoming celebrations, useful empty-state actions, and Settings entry. Watches `birthdaysStreamProvider` & `peopleStreamProvider`. |
| **PeopleScreen** | `/people` | StatefulShell tab 1 (`PeopleScreen`) | Contact directory with name/relationship search, relationship and birthday-month filters, contact cards, and CSV export/import. |
| **CalendarScreen** | `/calendar` | StatefulShell tab 2 (`CalendarScreen`) | Monthly interactive grid with birthday dot markers, month jump controls, and date celebration list cards. |
| **HistoryScreen** | `/history` | StatefulShell tab 3 (`HistoryScreen`, labeled Activity) | Chronological message and delivery activity. |
| **SettingsScreen** | `/settings` | Root route opened from app-bar Settings actions | Account, reminders, AI, backup, and app controls. |
| **MessageStudioScreen** | `/message-studio/:birthdayId`, `/message-studio/person/:personId` | Root Navigator modal push | AI composition studio: Recipient profile header, Tone chips, Personal context prompt, Generated draft editor with live metrics, Regenerate, and WhatsApp handoff action bar. |
| **PersonFormScreen** | `/people/add`, `/people/edit/:id` | Root Navigator modal push | Add / Edit recipient form: Name, relationship picker, birth month/day/year, delivery channel, phone number, custom notes. |
| **AppScaffold** | Shell wrapper | IndexedStack bottom navigation | Four primary destinations: Today, People, Calendar, and Activity. Settings is a separate root route. |
| **_NotFoundScreen** | Error fallback | Dynamic unmatched route | Graceful empty-state fallback with a button returning to `/dashboard`. |

## State & Persistence Architecture
- **State Management**: `flutter_riverpod` (v2.6) with pure reactive streams (`birthdaysStreamProvider`, `peopleStreamProvider`, `settingsProvider`).
- **Database Engine**: `drift` with SQLite (`AppDatabase`), reactive stream queries, migrations, and transactional updates.
- **Security & Credentials**: `flutter_secure_storage` storing Gemini API keys, encryption keys, and onboarding flags.
- **Background Tasks & Notifications**: `flutter_local_notifications` + `android_alarm_manager_plus` scheduling exact alarms with battery optimization compliance.
- **Flavors**: `play` excludes `SEND_SMS`; `personal` declares it. Automatic SMS remains disabled by Dart/native gates in both variants, so neither manifest alone proves the capability is usable.
