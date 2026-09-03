## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2026-09-03 - Accessible disabled state for TextInput

**Learning:** React Native's `TextInput` does not automatically announce its disabled state to screen readers when `editable={false}` is passed. This can lead to confusion for visually impaired users who may try to interact with a locked field.
**Action:** When wrapping or creating text input components, explicitly compute `disabled: inputProps.editable === false` and merge it with `inputProps.accessibilityState` to guarantee screen readers properly announce the disabled state. Ensure `{...inputProps}` is placed first on the element before redefining `accessibilityState` so any other explicitly passed accessibility states aren't overwritten.
