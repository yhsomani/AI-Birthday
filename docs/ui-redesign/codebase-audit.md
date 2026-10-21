# AI-Birthday Codebase & UI Architectural Audit

## Executive Summary
This document provides an exhaustive technical audit of the **AI-Birthday** Flutter/Android codebase, mapping every user-facing surface, navigation flow, Riverpod state provider, Drift/SQLite entity, and Android system boundary.

## Discovered Routes & Screens
| Screen Name | Route / Modal Entry | Path / Presentation | Primary Role & State Bindings |
|---|---|---|---|
| **OnboardingScreen** | `/onboarding` | Full-screen walkthrough (`OnboardingScreen`) | 3-slide value-prop introducing AI-Birthday, privacy pledge, notification setup. Guarded by `credentialStorageProvider.hasCompletedOnboarding()`. |
| **DashboardScreen** | `/dashboard` | StatefulShell tab 0 (`DashboardScreen`) | Command center dashboard: Metrics summary strip, Action Needed section (urgent ≤7d with direct WhatsApp action), Today's Birthdays list, and upcoming celebrations. Watches `birthdaysStreamProvider` & `peopleStreamProvider`. |
| **PeopleScreen** | `/people` | StatefulShell tab 1 (`PeopleScreen`) | Contact CRM & Directory: Search filter, relationship category chips, contact cards, quick actions, CSV export & import bottom sheet. |
| **CalendarScreen** | `/calendar` | StatefulShell tab 2 (`CalendarScreen`) | Monthly interactive grid with birthday dot markers, month jump controls, and date celebration list cards. |
| **HistoryScreen** | `/history` | StatefulShell tab 3 (`HistoryScreen`) | Chronological delivery audit log: Delivered messages, confirmation receipts, timestamp, WhatsApp / SMS status. |
| **SettingsScreen** | `/settings` | StatefulShell tab 4 (`SettingsScreen`) | Multi-card executive control center: Gemini API key & Nano status, Exact Alarm notification preferences, quiet hours, carrier consent disclaimers, cloud backup. |
| **MessageStudioScreen** | `/message-studio/:birthdayId`, `/message-studio/person/:personId` | Root Navigator modal push | AI composition studio: Recipient profile header, Tone chips, Personal context prompt, Generated draft editor with live metrics, Regenerate, and WhatsApp handoff action bar. |
| **PersonFormScreen** | `/people/add`, `/people/edit/:id` | Root Navigator modal push | Add / Edit recipient form: Name, relationship picker, birth month/day/year, delivery channel, phone number, custom notes. |
| **AppScaffold** | Shell wrapper | IndexedStack bottom navigation | 5-tab docked navigation bar with active indicators and accessible semantic labels. |
| **_NotFoundScreen** | Error fallback | Dynamic unmatched route | Graceful empty-state fallback with a button returning to `/dashboard`. |

## State & Persistence Architecture
- **State Management**: `flutter_riverpod` (v2.6) with pure reactive streams (`birthdaysStreamProvider`, `peopleStreamProvider`, `settingsProvider`).
- **Database Engine**: `drift` with SQLite (`AppDatabase`), reactive stream queries, migrations, and transactional updates.
- **Security & Credentials**: `flutter_secure_storage` storing Gemini API keys, encryption keys, and onboarding flags.
- **Background Tasks & Notifications**: `flutter_local_notifications` + `android_alarm_manager_plus` scheduling exact alarms with battery optimization compliance.
- **Flavors**: `personal` (direct SMS/WhatsApp options, unconstrained Gemini usage) and `play` (Google Play Store compliance, strict SMS permissions policies).
