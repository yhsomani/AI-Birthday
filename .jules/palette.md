## 2024-05-14 - Add Tooltips and Haptics to Icon-Only Buttons

**Learning:** Icon-only buttons lacking a `tooltip` property are inaccessible to screen readers. Adding tooltips to these elements is a critical accessibility requirement, and accompanying state changes with subtle haptic feedback (e.g. `HapticFeedback.lightImpact()`) improves the tactile UX.
**Action:** Always check `IconButton` usages for missing `tooltip` properties, and add `HapticFeedback` for state toggles.
