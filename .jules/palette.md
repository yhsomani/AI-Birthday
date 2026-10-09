## 2024-05-14 - Add Tooltips and Haptics to Icon-Only Buttons

**Learning:** Icon-only buttons lacking a `tooltip` property are inaccessible to screen readers. Adding tooltips to these elements is a critical accessibility requirement, and accompanying state changes with subtle haptic feedback (e.g. `HapticFeedback.lightImpact()`) improves the tactile UX.
**Action:** Always check `IconButton` usages for missing `tooltip` properties, and add `HapticFeedback` for state toggles.

## 2024-10-04 - Haptic Feedback on List Items

**Learning:** List items that navigate or open modals (e.g., in `PeopleScreen`) are primary interactive elements. Missing haptic feedback on these actions breaks the physical response expected from primary UI components.
**Action:** Ensure `HapticFeedback.lightImpact()` is included in the `onTap` handlers of list items that trigger state changes or navigation to maintain a consistent, responsive feel.

## 2024-10-09 - Calendar Navigation Haptics

**Learning:** Essential navigation controls such as Previous/Next Month in calendar components (`IconButton`) lack haptic feedback, making them feel unresponsive or disconnected compared to primary action buttons.
**Action:** Ensure `HapticFeedback.lightImpact()` is added to icon buttons that trigger major state changes like pagination or month shifts to provide consistent physical feedback.
