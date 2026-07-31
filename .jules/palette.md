## 2023-11-20 - SearchField Clear Button

**Learning:** For React Native text inputs (like SearchField), conditionally rendering a trailing 'clear' button enhances usability significantly. Ensure to use safe truthiness checks (`!!value`) to prevent runtime crashes, and if a clear icon (like `×` or `close`) is missing in the design system icons, fallback to using `AppText` with text (`×`) rather than hallucinating paths.
**Action:** When adding clear buttons, always wrap the clear condition with `!!value` and ensure `hitSlop`, `accessibilityRole="button"`, and `accessibilityLabel` are applied.
