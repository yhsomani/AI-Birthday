## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2024-09-11 - React Native TextInput Disabled State Announcement

**Learning:** In React Native, setting `editable={false}` on a `TextInput` does not automatically translate to an accessible disabled state for screen readers.
**Action:** When wrapping `TextInput`, explicitly compute `disabled: inputProps.editable === false` and merge it with `inputProps.accessibilityState` (placing it after spreading `inputProps`) so screen readers announce it properly without overriding other states.
