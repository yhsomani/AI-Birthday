## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2025-02-14 - Map editable to disabled accessibility state

**Learning:** In React Native, setting `editable={false}` on a `TextInput` prevents user input but does not automatically communicate the disabled state to screen readers. We must explicitly map this to `accessibilityState={{ disabled: true }}` to ensure accessibility.
**Action:** When wrapping or creating `TextInput` components, explicitly compute `disabled: inputProps.editable === false` and merge it with the incoming `accessibilityState`. Ensure `{...inputProps}` is spread before overwriting `accessibilityState`.
