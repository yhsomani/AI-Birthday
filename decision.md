# UI/UX Architectural Decisions & Stitch MCP Design Reference

**Stitch MCP Project**: `projects/3119246805913699827` (`Birthday Autopilot App`)  
**Design System Asset**: `assets/18258546519984416487` (`Birthday Autopilot Design System`)  
**Generation Model**: `GEMINI_3_1_PRO`  
**Status**: Approved & Validated against `PROJECT_ABOUT.md`

---

## 1. Executive Summary & Design Rationale

All UI/UX designs for **Birthday Autopilot** are prototyped, iterated, and validated using **Stitch MCP** with the `GEMINI_3_1_PRO` model as the single source of truth before any frontend implementation. This document records the foundational design system tokens, layout hierarchies, screen prototyping decisions, alternatives considered, and accessibility compliance.

---

## 2. Design System Foundation (Stitch MCP Asset `assets/18258546519984416487`)

### 2.1 Aesthetic & Visual Direction

- **Calm, Neutral, & Utility-First**: Clean surfaces (`#FFFFFF` on light `#F7F7FC`, `#1B1C27` on dark `#11121A`) with a distinctive Indigo accent (`#4B52A3` light, `#BFC2FF` dark).
- **Zero Clutter**: Strictly no confetti animations, cake graphics, script fonts, gamified streaks, or deceptive optimistic sending animations.
- **Truthful Outcomes**: Explicit text status paired with semantic icons for all states (e.g., _"Sending from this phone"_, _"Sent from this phone; delivery not confirmed"_, _"Needs your attention"_).

### 2.2 Token Specification

| Token Name        | Light Value | Dark Value | Purpose / Usage                                         |
| :---------------- | :---------- | :--------- | :------------------------------------------------------ |
| `background`      | `#F7F7FC`   | `#11121A`  | Main application canvas                                 |
| `surface`         | `#FFFFFF`   | `#1B1C27`  | Primary card and row surface                            |
| `surfaceRaised`   | `#FFFFFF`   | `#232431`  | Modals, elevated dialogs, bottom sheets                 |
| `surfaceMuted`    | `#EFF0F8`   | `#292B3A`  | Tag containers, search input fills, secondary cards     |
| `text`            | `#171824`   | `#F5F5FA`  | High-emphasis body, headlines, and labels               |
| `textMuted`       | `#5D6073`   | `#C2C3D0`  | Secondary metadata, helper text, timestamps             |
| `border`          | `#D9DAE6`   | `#3C3E50`  | Structural dividers and input borders (1px)             |
| `accent`          | `#4B52A3`   | `#BFC2FF`  | Primary brand CTAs, active tab icons, selection borders |
| `accentPressed`   | `#373D82`   | `#D5D6FF`  | Button active/pressed states                            |
| `positive`        | `#256A45`   | `#8ED4AD`  | Success badges, active automation indicators            |
| `positiveSurface` | `#E7F5EC`   | `#173B2A`  | Background tint for positive status tags                |
| `warning`         | `#8A4F08`   | `#F4C06E`  | Needs review, unsaved changes, expiration alerts        |
| `warningSurface`  | `#FFF2D8`   | `#463311`  | Background tint for warning status tags                 |
| `critical`        | `#A53535`   | `#FFB4AB`  | Errors, missing permissions, invalid numbers            |
| `criticalSurface` | `#FCEAEA`   | `#4F2425`  | Background tint for critical status tags                |
| `info`            | `#315B91`   | `#A8C8F3`  | Guidance notes, informational banners                   |
| `infoSurface`     | `#E9F1FC`   | `#203753`  | Background tint for info status tags                    |

### 2.3 Typography Scale (Inter Font Family)

- **Hero / Page Header**: 28px font size / 34px line height / 700 bold
- **Section Title**: 20px font size / 26px line height / 600 semibold
- **Card Headline / Subhead**: 16px font size / 22px line height / 500 medium
- **Body Regular**: 15px font size / 22px line height / 400 regular
- **Caption / Metadata**: 13px font size / 18px line height / 400 regular
- **Tag / Badge Label**: 12px font size / 16px line height / 500 medium

### 2.4 Layout, Shape & Rhythm

- **Corner Radii**: 8px (`sm`), 14px (`md` standard for cards/inputs/buttons), 20px (`lg` for dialogs/sheets), 999px (`pill` for badges).
- **Spacing Scale**: 4px (`xs`), 8px (`sm`), 16px (`md`), 24px (`lg`), 32px (`xl`), 48px (`xxl`).
- **Touch Target**: Strict minimum 48dp on all interactive elements.

---

## 3. Major Screen Prototyping & Decisions (Gemini 3.1 Pro)

### 3.1 Screen G02 & H01: Main Shell & Home Dashboard

- **Stitch MCP Reference**: `projects/3119246805913699827/sessions/7759590777852409302` (Model: `GEMINI_3_1_PRO`)
- **Decision**: Implement a 3-tab bottom navigation (**Home**, **People**, **Settings**). Main Home screen features an active automation hero card with immediate status transparency, an upcoming birthdays queue with countdown badges, and an actionable "Needs Attention" alert banner.
- **Why Chosen**:
  - Provides instant glanceability of background automation health.
  - Eliminates clutter and keeps the primary user job (knowing what will be sent next) front and center.
  - Keeps navigation stable and predictable across both Android Automation Edition and iOS Companion Edition.
- **Alternatives Considered**:
  - _Option A: 5-tab bar with dedicated 'Activity' and 'Upcoming' tabs._ Rejected to prevent cognitive fragmentation and sparse tabs.
  - _Option B: Gamified calendar grid with birthday streak counts._ Rejected because it violates the core calm utility principle and adds unnecessary visual noise.

### 3.2 Screen P01: People Directory & Multi-Filter List

- **Stitch MCP Reference**: `projects/3119246805913699827/sessions/846231095308439648` (Model: `GEMINI_3_1_PRO`)
- **Decision**: Contact list with persistent search bar, multi-category filter chips (_All_, _Enabled_, _Ready_, _Needs Attention_, _Excluded_), initials-based avatar chips (never requesting or rendering photo attachments), and masked phone numbers (`+1 •••-•••-8832`).
- **Why Chosen**:
  - Privacy-preserving: Phone numbers are masked in list views to prevent shoulder surfing.
  - Grouping by status (_Ready_ vs. _Needs Attention_) enables one-tap resolution of missing birthdays or ambiguous phone numbers.
- **Alternatives Considered**:
  - _Option A: Auto-enrolling all Google Contacts by default._ Rejected because all contacts must start Off, requiring explicit user consent per contact.
  - _Option B: Displaying contact profile photos._ Rejected because the app does not request or store photo permissions.

### 3.3 Screen S10 & P02: Contact Approval & Message Proposal

- **Stitch MCP Reference**: `projects/3119246805913699827/sessions/8661775396951016161` (Model: `GEMINI_3_1_PRO`)
- **Decision**: Dedicated card showing recipient name, verified unmasked destination number (with explicit confirmation), birthday date with leap-year rule, message template preview with variable tokens (`{name}`), real-time Unicode character and SMS segment counter, selected SIM badge, delivery window, and an immutable "Approve Person" button.
- **Why Chosen**:
  - Eliminates surprise carrier charges by displaying exact SMS part counts and Unicode warnings before approval.
  - Ensures full alignment with Google Play restricted SMS policies and user confirmation guarantees.
- **Alternatives Considered**:
  - _Option A: Inline quick-toggle switch without approval preview._ Rejected because users must review the exact message payload, SIM card, and timing window before automation is enabled.

---

## 4. UI/UX Validation Checklist

- [x] **Navigation Flows**: 3 permanent tabs; deep task flows open as full screens or sheets with explicit back buttons.
- [x] **Information Architecture**: Clear separation between active status, upcoming queue, contact directory, and system settings.
- [x] **Accessibility (a11y)**:
  - 48dp minimum touch target bounding boxes.
  - High contrast ratio (>= 4.5:1 for normal text, >= 3:1 for large text).
  - Clean scaling up to 200% Dynamic Type without clipping.
  - Full TalkBack / VoiceOver label coverage on all icon buttons.
- [x] **Mobile Responsiveness**: Fluid single-column layout on 320dp to 480dp viewports, safe area insets respected.
- [x] **Error & Empty States**:
  - Empty: Calm illustration + clear explanatory title + primary CTA (_"Sync Google Contacts"_).
  - Loading: Centered indicator with accessible state text (_"Checking background readiness..."_).
  - Error / Attention: Semantic badge + exact reason + one-tap repair action.

---

## 5. Implementation Gate Compliance

1. **Design Exists in Stitch MCP**: Project `projects/3119246805913699827` is configured with design system `assets/18258546519984416487` and screens prototyped via `GEMINI_3_1_PRO`.
2. **Reviewed Against Business Requirements**: Verified 100% compliant with `PROJECT_ABOUT.md` and `stitch/SCREEN_MANIFEST.md`.
3. **Validated for Usability & Accessibility**: Verified 48dp tap targets, color contrast, and 200% font scaling.
4. **Code Implementation Mapped**: Every screen is linked to its respective implementation in `src/features/` and verified in `stitch/IMPLEMENTATION_CROSSWALK.json`.
