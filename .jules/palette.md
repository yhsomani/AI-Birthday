## 2024-05-14 - Add Tooltips and Haptics to Icon-Only Buttons

**Learning:** Icon-only buttons lacking a `tooltip` property are inaccessible to screen readers. Adding tooltips to these elements is a critical accessibility requirement, and accompanying state changes with subtle haptic feedback (e.g. `HapticFeedback.lightImpact()`) improves the tactile UX.
**Action:** Always check `IconButton` usages for missing `tooltip` properties, and add `HapticFeedback` for state toggles.

## 2024-10-04 - Haptic Feedback on List Items

**Learning:** List items that navigate or open modals (e.g., in `PeopleScreen`) are primary interactive elements. Missing haptic feedback on these actions breaks the physical response expected from primary UI components.
**Action:** Ensure `HapticFeedback.lightImpact()` is included in the `onTap` handlers of list items that trigger state changes or navigation to maintain a consistent, responsive feel.

## 2024-11-20 - Adding Loading Spinners to Asynchronous Form Actions
**Learning:** Users often experience a moment of uncertainty when clicking a primary "Save" or "Submit" button if the only feedback is a text change (e.g., from 'Save' to 'Saving…'). According to Flutter UX micro-improvement patterns, adding a visual spinner (like a `CircularProgressIndicator`) alongside the text significantly improves perceived responsiveness and reassures the user that an async operation is underway.
**Action:** When implementing or updating primary action buttons that trigger async form submissions, always include a visual loading indicator (e.g., using `FilledButton.icon` or a `Row` with a `CircularProgressIndicator`) instead of relying solely on text changes.

## 2026-10-11 - Haptic Feedback on High-Frequency Calendar Navigation
**Learning:** Month shifting in interactive calendar views represents a primary, high-frequency interaction. Without tactile feedback, month navigation `IconButton` controls feel physically disconnected and unresponsive compared to other primary UI elements in the application.
**Action:** Include `HapticFeedback.lightImpact()` on month navigation button actions (Previous and Next month) to deliver consistent tactile response and improve accessibility across devices.
