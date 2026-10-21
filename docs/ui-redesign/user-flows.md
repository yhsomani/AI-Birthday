# AI-Birthday Core User Flows & Navigation Journeys

## 1. Overview
This document specifies the end-to-end user journeys across the redesigned AI-Birthday application, ensuring all navigation transitions, state preservations, and system boundaries operate cohesively.

---

## 2. Primary User Journeys

### Flow 1: First-Run Onboarding & Permission Grant
1. **Entry**: App cold launch when `hasCompletedOnboarding() == false`.
2. **Step 1**: Value Proposition Slide 1 (Executive birthday intelligence, local privacy).
3. **Step 2**: Value Proposition Slide 2 (AI personalization with Gemini & offline Nano).
4. **Step 3**: Value Proposition Slide 3 (Reliable Android exact-alarm reminders).
5. **Action**: User taps "Get Started".
6. **State Mutation**: `credentialStorageProvider.setOnboardingComplete(true)`.
7. **Transition**: Navigation directs to `/dashboard` shell.

---

### Flow 2: Urgent Birthday Action & Delivery Handoff
1. **Entry**: User opens `/dashboard`.
2. **Trigger**: An upcoming birthday exists within ≤7 days with status `needs_action` or `draft_ready`.
3. **Display**: The "Action Needed" prominent alert card highlights the recipient, countdown chip (e.g., "In 2 days"), and action button.
4. **Action Branch A (Review/Customize)**:
   - User taps the card body or "Review Message".
   - App navigates to `/message-studio/:birthdayId`.
   - User adjusts tone chips, adds memory context, and taps "Regenerate".
   - Gemini generates new greeting via `aiRouterProvider`.
5. **Action Branch B (Direct Send via WhatsApp)**:
   - User taps "Send via WhatsApp" button.
   - App validates phone number format and prepares URI `whatsapp://send?phone=...&text=...`.
   - Android intent is dispatched.
   - User confirms delivery in app dialog; status updates to `delivered` in Drift SQLite.
   - Event logged in `/history`.

---

### Flow 3: Adding & Managing Recipients in People Directory
1. **Entry**: User navigates to `/people` tab.
2. **Search / Filter**:
   - User enters query in search bar (matches name, notes, phone).
   - User filters by relationship chip (All, Family, Friends, Work, VIP).
3. **Add Recipient**:
   - User taps FAB "+ Add Person".
   - Navigates to `/people/add` (Modal Form).
   - Fills Name, selects Relationship, picks Birth Month & Day, selects preferred tone and channel.
   - Taps "Save Person". Form validates inputs.
   - Person is written to Drift `PeopleTable` and Birthday cycle record created.
   - SnackBar confirmation displayed; popped back to `/people`.
4. **Data Portability**:
   - User opens top action menu -> "Export CSV" / "Import CSV".
   - Triggers `contactCsvServiceProvider` with Android file picker / share sheet.

---

### Flow 4: Calendar Exploration & Proximity Planning
1. **Entry**: User navigates to `/calendar` tab.
2. **Month Navigation**: User pages between months or taps "Today".
3. **Grid Interaction**:
   - Days with birthdays display colored indicator dots.
   - Tapping a date filters the bottom agenda list to that specific day.
   - Selecting empty day shows "No celebrations on this date".
4. **Quick Action**: Tapping an event in the agenda sheet opens `/message-studio/:birthdayId`.

---

### Flow 5: Delivery Audit Log & History Review
1. **Entry**: User navigates to `/history` tab.
2. **Metric Summary**: Top summary displays Total Delivered, On-Time Delivery Rate %, and Channel breakdown.
3. **Filter**: User filters by Status (Delivered, Pending, Failed) or Channel (WhatsApp, SMS).
4. **Audit Card**: Each card shows recipient name, delivery timestamp, message preview, and channel badge.

---

### Flow 6: System Configuration & Security Management
1. **Entry**: User navigates to `/settings` tab.
2. **AI Provider Configuration**:
   - User taps "Gemini API Key".
   - Enters API key in secure modal; validated and saved to `flutter_secure_storage`.
   - User toggles Gemini Nano on-device model support (with hardware availability verification).
3. **Notification & Exact Alarms**:
   - User verifies Android Exact Alarm permission status.
   - Sets reminder lead time (Same day, 1 day before, 3 days before).
   - Configures Quiet Hours range.
4. **Regulatory & Compliance**:
   - Reads carrier compliance and SMS consent terms.
   - In Google Play flavor, direct SMS options are disabled in compliance with Play policies.
5. **Encrypted Backup**:
   - Triggers manual encrypted JSON backup export or restore.
