## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2024-05-15 - Explicitly Mapping editable=false to disabled for screen readers

**Learning:** React Native's TextInput does not automatically map the `editable={false}` prop to the accessibility `disabled` state. This means screen readers will not announce the input as disabled unless explicitly told to.
**Action:** When wrapping TextInput, always compute `disabled: editable === false` and merge it into `accessibilityState` after spreading `{...props}` to ensure screen readers correctly interpret the disabled state.
