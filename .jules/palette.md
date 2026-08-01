## 2024-08-01 - Add clear button to SearchField

**Learning:** UX Code Convention for React Native: Enhance the usability of searchable or editable text fields (e.g., `SearchField`) by conditionally rendering a trailing 'clear' button (such as a `Pressable` with an icon) when the input string is not empty. Ensure it has an appropriate `hitSlop`, `accessibilityRole="button"`, and a clear `accessibilityLabel`.
**Action:** Always add a clear button with `close` icon (a simple cross path) for inputs when the value is not empty, specifically when making UX enhancements as Palette.
