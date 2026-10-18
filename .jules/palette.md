## 2024-10-02 - Add Haptic Feedback to Primary Interactive Elements
**Learning:** Adding subtle physical feedback (like haptic impact) to primary interactive elements (such as `FloatingActionButton` and other high-frequency buttons) provides a more satisfying, responsive feel, especially on mobile devices. This enhances the overall intuitive feel of the UI by acknowledging user actions immediately.
**Action:** Implement `HapticFeedback.lightImpact()` inside the `onPressed` handlers of primary interactive elements to consistently provide physical acknowledgement of user interaction.

## 2024-10-03 - Eradicate UI Slop and Generic AI Templates
**Learning:** Overusing simple gradients, "Inter-everywhere" typography, and missing cause-and-effect motion makes an application feel like a generic SaaS template rather than a thoughtful, intent-driven product.
**Action:** Replace arbitrary default Material parameters. Introduce distinctive typography sets (e.g. `google_fonts` using Playfair Display for headers and Nunito for text) combined with solid structural cards and consistent `HapticFeedback.lightImpact()` on all data-mutating buttons to elevate the perceived product fit, clarity, and hierarchy.

## 2024-10-13 - Add Haptic Feedback to Calendar and Settings
**Learning:** Consistent haptic feedback across list items and grid items ensures the application feels responsive and intuitive.
**Action:** Always include HapticFeedback.lightImpact() in primary list tile and grid taps.
## 2026-10-13 - Comprehensive Haptic Feedback Integration & ESM Test Fixes
 **Learning:** Primary action buttons within bottom sheets, specifically actions that mutate state or open core workflows (like `Draft Message with AI`, `Edit Contact Details`, and `Delete Contact` in `PeopleScreen`) often lack physical confirmation natively. Furthermore, testing these physical cues is critical and can be reliably verified using the `SystemChannels.platform` mock method call handler to capture `HapticFeedback.vibrate` calls. On the backend side, webcrypto polyfill is necessary for dynamic resolution under pure ESM node environment.
 **Action:** Proactively inspect custom modal sheets, popups, and high-frequency touch targets (like list tiles opening modals) for missing `HapticFeedback.lightImpact()`. Always mock `SystemChannels.platform` in widget tests to formally assert haptic occurrences. Use dynamic fallback logic to `node:crypto`'s `webcrypto` when `globalThis.crypto?.subtle` is unavailable to fix `node --test` failures under missing ESM support.

## 2024-10-18 - Style Destructive Action Buttons
**Learning:** Primary action buttons for destructive actions (like Delete or Discard) should have a distinct visual style to prevent accidental clicks. Using only a red background can sometimes lead to poor contrast.
**Action:** Always style destructive action buttons using both `backgroundColor: Theme.of(context).colorScheme.error` and `foregroundColor: Theme.of(context).colorScheme.onError` in the `styleFrom` configuration.

## 2024-10-18 - Style Destructive Action Buttons Consistently
**Learning:** The 'Sign out' button in the settings modal was missing the distinct visual style for destructive actions, which could lead to accidental clicks.
**Action:** Always style destructive action buttons using both `backgroundColor: Theme.of(context).colorScheme.error` and `foregroundColor: Theme.of(context).colorScheme.onError` in the `styleFrom` configuration, specifically for buttons like 'Sign out' that have significant consequences.

## 2024-10-18 - Add Haptic Feedback to PopupMenuButton Actions
**Learning:** PopupMenuButton options are primary interactive elements, and adding subtle physical feedback when they are selected (via `onSelected`) makes the interface feel much more responsive and satisfying.
**Action:** Implement `HapticFeedback.lightImpact()` inside the `onSelected` handlers of `PopupMenuButton` widgets to consistently provide physical acknowledgement of user interaction.
