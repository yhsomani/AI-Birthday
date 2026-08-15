## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2026-08-14 - Map TextInput editable prop to accessibilityState disabled

**Learning:** Screen readers do not automatically infer `disabled` state from `TextInput` components when `editable={false}` is provided. It leads to poor accessibility as users aren't properly informed that the input is disabled.
**Action:** Always map the `editable` prop of a TextInput to `accessibilityState={{ disabled: !editable }}` so that screen readers announce the element as disabled.
