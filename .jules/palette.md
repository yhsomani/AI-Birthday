## 2024-08-12 - UX Code Convention for React Native: Clear Search Pattern

**Learning:** Adding a "clear" button inside searchable/editable text inputs conditionally when length > 0 is an important UX pattern.
**Action:** Enhance the `SearchField` component in `src/design-system/components/Primitives.tsx` to include a clear button when text length > 0, ensuring `accessibilityRole="button"` and `hitSlop` are used. Also add string resources `common.clearSearch` to localization files.
