# AI-Birthday Interaction & State Specification

## 1. Interaction Principles
The redesigned AI-Birthday interface implements high-efficiency, predictable mobile-first interactions inspired by modern analytics dashboards.

### Core Rules
- **No Mystery Meat Navigation**: Every control, chip, and button has an explicit, accessible label and distinct icon.
- **Immediate Sensory Feedback**: Every tap triggers a standard Material 3 ripple or tactile press state (`0.98` scale or highlight).
- **Persistent State During Modals**: Opening subpages, date pickers, or bottom sheets retains scroll position and filter state when returning.
- **Fail-Safe Confirmations**: Destructive operations (contact deletion, database wipe) require dual-step confirmation dialogs.

---

## 2. Component Interaction States

### 2.1 Buttons & Action Controls
| Control Type | Default State | Pressed / Active State | Disabled State | Accessibility Label |
|---|---|---|---|---|
| **Primary Action (`FilledButton`)** | `#A64B2A` background, `#FFFFFF` text | `#8C3E22` (darkened 10%), elevation 1dp | `#E2E8F0` bg, `#94A3B8` text | Action verb + target (e.g. "Send message to Sarah") |
| **Secondary Action (`OutlinedButton`)** | `#FFFFFF` bg, 1px `#E2E8F0` border, `#0F172A` text | `#FBECE6` tint bg, `#A64B2A` border | 1px `#CBD5E1` border, `#94A3B8` text | Contextual action label |
| **Urgent Card Action (`ElevatedButton`)** | `#A64B2A` bg, high-contrast white text, pill radius | `#7D381E` bg, elevation 4dp | N/A (Urgent cards only show actionable items) | "Review and send greeting to {Name}" |
| **Filter Chip (`FilterChip`)** | `#FFFFFF` bg, `#64748B` text, 1px `#E2E8F0` border | `#FBECE6` bg, `#A64B2A` text, 1px `#A64B2A` border | `#F1F5F9` bg, `#94A3B8` text | "Filter by {category}" |

---

### 2.2 Screen Loading, Empty, and Error States

#### Loading State
- **Presentation**: Shimmer skeletons matching exact card dimensions (`CardSkeleton`, `MetricsRowSkeleton`).
- **Timing**: Displayed when Riverpod `AsyncValue.loading()` is active. Never show an unstyled centered spinner without card structure.

#### Empty State (`EmptyState` widget)
- **Zero Contacts**: Display warm calendar icon illustration, "No birthdays tracked yet", explanatory body text, and a primary CTA "+ Add First Person".
- **Zero Search Results**: "No contacts found matching '{query}'", with a "Clear search" text button.
- **Zero Action Needed**: Clean success card with green checkmark: "All caught up! No celebrations require action today."

#### Error State
- **Presentation**: Bounded error container with `#FCEDEA` surface, `#B3261E` border/text, clear diagnostic message (e.g., "Unable to connect to Gemini API. Check your API key in Settings."), and a "Retry" button.

---

### 2.3 Form Validation & Input States
- **Text Inputs (`TextFormField`)**:
  - Focus state: 2px `#A64B2A` brand border with 0.15 opacity focus ring.
  - Error state: 1.5px `#B3261E` crimson border with helper text rendered beneath.
  - Floating label: Animated to 12px label style on focus or value entry.
- **Date Selector**:
  - Tapping opens native-styled Material 3 date picker modal.
  - Leap year validation: Allows Feb 29 selection with fallback logic for non-leap years.

---

### 2.4 Mobile Responsive Layout Adaptation
- **Small Screens (<360dp width)**:
  - Metric row stacks or wraps dynamically using `Wrap` or horizontal scroll.
  - Padding scales from `16dp` down to `12dp`.
  - Chip labels abbreviate where necessary.
- **Standard Screens (360dp - 440dp width)**:
  - Full 3-column KPI metric strip.
  - Standard `16dp` horizontal content margins.
- **Large Phones / Foldables / Tablets (>440dp width)**:
  - Max content width constrained to `720dp` centered on screen.
  - Grid layouts expand from 1-column list to 2-column card masonry.
- **Accessibility Text Scaling (up to 2.0x)**:
  - All text containers avoid fixed `height: ...` constraints to prevent text truncation and render overflows.
  - Multi-line chips expand vertically with dynamic layout calculations.
