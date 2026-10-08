## 2024-05-14 - Add Tooltips and Haptics to Icon-Only Buttons

**Learning:** Icon-only buttons lacking a `tooltip` property are inaccessible to screen readers. Adding tooltips to these elements is a critical accessibility requirement, and accompanying state changes with subtle haptic feedback (e.g. `HapticFeedback.lightImpact()`) improves the tactile UX.
**Action:** Always check `IconButton` usages for missing `tooltip` properties, and add `HapticFeedback` for state toggles.

## 2024-10-04 - Haptic Feedback on List Items

**Learning:** List items that navigate or open modals (e.g., in `PeopleScreen`) are primary interactive elements. Missing haptic feedback on these actions breaks the physical response expected from primary UI components.
**Action:** Ensure `HapticFeedback.lightImpact()` is included in the `onTap` handlers of list items that trigger state changes or navigation to maintain a consistent, responsive feel.
## 2024-10-08 - Add visual spinner to async form save buttons
**Learning:** Changing button text alone during an async submit is often missed by users; combining text with a visual spinner like `CircularProgressIndicator` improves perceived responsiveness and clarifies the blocked state.
**Action:** Always include a visual indicator alongside text updates in primary action buttons that trigger async operations.
