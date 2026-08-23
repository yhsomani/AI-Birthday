## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2025-03-05 - Add disabled accessibilityState to AccessibleTextInput

**Learning:** React Native's `TextInput` does not automatically communicate its `editable={false}` status as a disabled state to screen readers (like VoiceOver).
**Action:** When wrapping or creating a custom text input component, explicitly compute `disabled: inputProps.editable === false` and merge it into `accessibilityState` so screen readers correctly announce the input as disabled.
