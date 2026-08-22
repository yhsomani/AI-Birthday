## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2024-08-22 - Add disabled state announcement for screen readers on text inputs

**Learning:** VoiceOver does not automatically announce un-editable `TextInput` inputs as disabled.
**Action:** When wrapping React Native `TextInput` components, explicitly compute `disabled: inputProps.editable === false` and merge it with `inputProps.accessibilityState` to guarantee screen readers properly announce the disabled state.
