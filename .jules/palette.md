## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2025-02-14 - Fix disabled state for text inputs

**Learning:** When using React Native `TextInput` components, if they are disabled using `editable={false}`, screen readers will not natively announce the disabled state unless explicitly provided.
**Action:** When wrapping or configuring `TextInput` components, check `editable === false` and merge `{ disabled: true }` into `accessibilityState` ensuring it doesn't overwrite other properties.
