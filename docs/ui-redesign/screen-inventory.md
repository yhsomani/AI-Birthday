# Complete Application Screen & Component Inventory

> Historical design references and previous test-status labels are not evidence of current-HEAD validation. The current Android shell has four primary destinations; the Settings route is opened from the screen app-bar action. Updated UI tests require execution with the repository Flutter toolchain.

| Screen / Component | Route / Path | Current Purpose | Key Sub-components & Dialogs | State Providers | Stitch Status | Verification Status |
|---|---|---|---|---|---|---|
| **Today** | `/dashboard` | Today's celebrations, action queue, and upcoming birthdays | Actionable empty state, birthday cards, quick actions | `birthdaysStreamProvider`, `peopleStreamProvider` | Prior design reference | Updated; tests pending |
| **People Directory** | `/people` | Contact directory with search and birthday/relationship filters | Search, relationship selector, birthday-month selector, CSV import/export | `peopleStreamProvider`, `contactCsvServiceProvider` | Prior design reference | Updated; tests pending |
| **Calendar View** | `/calendar` | Monthly celebrations timeline | Month selector, calendar grid, day agenda sheet, event cards | `birthdaysStreamProvider`, `peopleStreamProvider` | Prior design reference | Existing checks; rerun required |
| **Message Studio** | `/message-studio/:birthdayId` | AI greeting composer and review | Recipient profile, tone selection, draft editor, delivery action bar | `birthdayDetailProvider`, `aiRouterProvider`, delivery providers | Prior design reference | Existing checks; rerun required |
| **Activity** | `/history` | Delivery and message activity | Timeline items, channel and outcome details | `historyStreamProvider`, delivery providers | Prior design reference | Updated; tests pending |
| **Settings** | `/settings` | Account, reminders, AI, backup, and app controls | Settings sections and provider-backed controls | `credentialStorageProvider`, `cloudSyncServiceProvider`, feature providers | Prior design reference | Existing checks; rerun required |
| **Contact Form** | `/people/add`, `/people/edit/:id` | Add/edit recipient | Text fields, date selection, relationship selector, validation | `personFormProvider`, people services | Documented spec | Existing checks; rerun required |
| **Onboarding Walkthrough** | `/onboarding` | First-run setup | PageView, feature cards, permission explanation | `credentialStorageProvider` | Documented spec | Existing checks; rerun required |
| **App Shell** | Nav scaffolding | Global Android navigation | `AppScaffold`, four-destination `NavigationBar`, top-level Settings action | `routerProvider` | Updated implementation | Updated; goldens/tests pending |
| **Error Screen** | `_NotFoundScreen` | Route fallback | `EmptyState`, return-to-shell action | None | Documented spec | Existing checks; rerun required |
