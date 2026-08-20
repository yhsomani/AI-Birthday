## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2025-02-12 - TextInput disabled accessibility state

**Learning:** VoiceOver does not automatically announce un-editable (`editable={false}`) TextInputs as disabled.
**Action:** Always compute and merge `disabled: inputProps.editable === false` into `accessibilityState` when wrapping TextInput components.
