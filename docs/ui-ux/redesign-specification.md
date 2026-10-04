# AI-Birthday — Comprehensive UI/UX Redesign Specification

**Version:** 2.0.0 (October 2026)  
**Status:** Authoritative Implementation Blueprint  
**Target:** Flutter Android Application  

---

## 1. Scope & Screen Mapping

This specification outlines the architectural refactoring and visual redesign for every primary screen, dialog, and sheet across the AI-Birthday application:

```
[AppScaffold (Bottom Navigation Shell)]
  ├── /dashboard  ──> DashboardScreen (Unifies Dashboard & HomeScreen)
  ├── /people     ──> PeopleScreen (Unifies People & PersonListScreen)
  │     ├── /people/add       ──> PersonFormScreen (Add mode)
  │     └── /people/edit/:id  ──> PersonFormScreen (Edit mode)
  ├── /calendar   ──> CalendarScreen
  ├── /history    ──> HistoryScreen
  ├── /settings   ──> SettingsScreen
  └── /message-studio/:birthdayId ──> MessageStudioScreen
```

---

## 2. Screen Specifications & Defect Remediations

### 2.1 Dashboard (`DashboardScreen`)
- **Visual Structure**:
  1. **Editorial Command Header**: Warm linen surface, date banner in uppercase tracking, total tracked count, headline stating celebration state ("2 Birthdays Today" or "All Celebrations On Track"), and summary of pending reviews.
  2. **Action Needed Feed**: High-intent cards highlighting birthdays within the 7-day approach window that require message drafting or review.
  3. **Celebrations Carousel / List**: Categorized today's and upcoming birthdays with turns-age calculation, relationship chips, and direct review triggers.
  4. **Quick Actions Hub**: Add Birthday, View Calendar, and App Settings with 48dp minimum hit targets.
- **Defects Fixed**:
  - Replaces hardcoded hex values with semantic theme tokens.
  - Replaces collision-prone `Row` in action cards with responsive alignment.
  - Clears FAB / NavigationBar obstruction with `AppSpacing.bottomClearance`.

### 2.2 People Screen (`PeopleScreen`)
- **Visual Structure**:
  1. **Search & Filter Bar**: Instant client-side filtering by name, relationship, or month.
  2. **Alphabetical Contact Cards**: Left avatar with clean initials, bold recipient name, relationship tag, and countdown chip.
  3. **Actions Popup & Sheet**: 3-dots popup menu (`Edit`, `Delete`) with minimum 48dp hit area.
  4. **Scroll-Safe Modal Details**: Modal sheet built with `SingleChildScrollView` + `SafeArea`, drag handle, facts list, phone, preferred tone, and action buttons.
- **Defects Fixed**:
  - Solves the critical unconstrained `Column` bottom sheet overflow bug.
  - Prevents trailing countdown chip from squashing long names.
  - Adds 88dp bottom padding so the FAB never covers the last contact in the list.

### 2.3 Person Form Screen (`PersonFormScreen`)
- **Visual Structure**:
  1. **Essential Inputs (Card 1)**: Name (auto-capitalized words), Birthday Month & Day (adaptive row), and Birth Year (numeric with 4-digit max).
  2. **Relationship & Dynamics (Card 2 - Progressive)**: Relationship category and preferred tone segmented buttons.
  3. **Contact & Delivery (Card 3 - Progressive)**: Phone number (international formatted), email, and IANA timezone.
  4. **AI Personalization Context (Card 4 - Progressive)**: Verified facts list with clean add/remove buttons, explicit notice ("AI will never invent facts"), and personal notes buffer.
  5. **Sticky Save Action Bar**: Unconstrained height button with bold typography, disabled during async save.
- **Defects Fixed**:
  - Replaces fixed `height: 48` with flexible button container accommodating text scaling.
  - Fixes dropdown squeeze on narrow screens with minimum width constraints.
  - Incorporates keyboard-safe scroll physics (`ClampingScrollPhysics` with bottom insets).

### 2.4 Calendar Screen (`CalendarScreen`)
- **Visual Structure**:
  1. **Month Header Navigation**: Previous / Next month buttons with accessible hit areas, centered Month Year headline.
  2. **Day-of-Week Strip**: Monday through Sunday with distinct typography.
  3. **Adaptive Day Grid**: Day cells with clean circular highlights for today, dot indicators for birthdays, and non-leap February 29 resolution to February 28.
  4. **Day Celebration Bottom Sheet**: Opens on day tap, displaying avatar, turns-age label, and direct Message Studio review button.
- **Defects Fixed**:
  - Eliminates tight vertical cell constraints causing indicator dot clipping.
  - Adds interactive drag handle and direct studio link to the day celebration sheet.

### 2.5 Message Studio Screen (`MessageStudioScreen`)
- **Visual Structure**:
  1. **Recipient Context Bar**: Name, relationship, phone number, and verified user facts.
  2. **Tone & Dynamics Selector**: Choice chips with selected state indicator for Heartfelt, Humorous, Poetic, Warm, and Short tones.
  3. **Custom Instruction Buffer**: Single-line tweak field with clear helper text.
  4. **Message Body Editor**: Multi-line text field with character count, copy button, and auto-expanding buffer.
  5. **Responsive Action Bar (`ResponsiveActionBar`)**:
     - Automatically adapts between horizontal row on wide screens and stacked vertical buttons on narrow screens / high text scales.
     - "Generate with AI" (Tonal button with spinner state).
     - "Send via WhatsApp" (Branded green button with chat icon).
  6. **Delivery Confirmation Modal**: Clear feedback dialog asking "Did you send the message?", allowing user to confirm and update status to `SENT`.
- **Defects Fixed**:
  - Resolves horizontal overflow on compact viewports and text scaling.
  - Improves editor layout during virtual keyboard appearance.

### 2.6 History Screen (`HistoryScreen`)
- **Visual Structure**:
  1. **Timeline Feed**: Chronological cards of drafts and sent greetings.
  2. **Status Badges**: Distinct "Sent" (green check) vs "Draft" (amber pencil) badges.
  3. **Body Preview**: Clean monospace/editorial bubble with copy button (instant SnackBar feedback) and studio deep link.
- **Defects Fixed**:
  - Replaces static empty placeholder with live reactive stream.
  - Aligns trailing timestamp and status chips cleanly.

### 2.7 Settings Screen (`SettingsScreen`)
- **Visual Structure**:
  1. **Account Tile**: Google Sign-In status with clean state reporting.
  2. **Subscription Card**: Free vs Pro status, feature availability, upgrade button, restore purchases button, and developer sandbox switch.
  3. **AI Credentials Card**: Hardware-backed Gemini API Key with secure mask/unmask toggle and remove action.
  4. **On-Device Intelligence**: Gemini Nano (AICore) readiness chip.
  5. **Reminders & Quiet Hours**: Toggle switch, lead time switches (1d, 3d, 7d), and interactive time pickers for quiet hours.
  6. **Appearance**: Dark mode toggle reacting dynamically to system settings.
- **Defects Fixed**:
  - Standardizes card padding and typography across all sections.
  - Prevents card height jumping when API keys are entered or deleted.

---

## 3. Responsive Breakpoints

- **Compact Phone (< 360dp)**: Single-column layout, stacked action buttons, condensed list tiles.
- **Standard Phone (360dp – 480dp)**: Default layout, side-by-side action buttons where text fits, 16dp margins.
- **Tablet / Large Screen (> 480dp)**: Centered container max-width 720dp, generous 24dp margins.
