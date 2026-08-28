## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2025-02-14 - Add accessible disabled state to TextInput

**Learning:** Screen readers might not automatically announce the disabled state of a React Native `TextInput` when `editable={false}` is used. Setting `editable={false}` visually and functionally disables the input, but passing `accessibilityState={{ disabled: true }}` explicitly is required to ensure users with assistive technologies are properly informed of the input's status.
**Action:** When wrapping or configuring `TextInput` components, explicitly compute `disabled: inputProps.editable === false` and merge it with `inputProps.accessibilityState` (placing it after `...inputProps` in the JSX spread) to guarantee screen readers properly announce the disabled state without overwriting other explicit accessibility states.
