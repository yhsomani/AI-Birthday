## 2026-07-21 - Add clear button to SearchField
**Learning:** Adding a trailing "clear" button inside a search or text input field is a valuable accessibility and UX enhancement in this React Native project, but care must be taken to assign appropriate `accessibilityRole`, `accessibilityLabel`, and touch targets like `hitSlop`. Do not hallucinate translated strings for `accessibilityLabel` keys.
**Action:** When working on editable text inputs (`SearchField`, `TextInput`), always check if a conditional `Pressable` clear button should be rendered when the input has length > 0.
