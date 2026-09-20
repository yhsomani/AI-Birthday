## 2025-02-14 - Add clear button to SearchField

**Learning:** React Native's `TextInput` becomes more usable and accessible when paired with a conditionally rendered trailing clear button if text is present. It allows quick reset of complex queries like contact searches, reducing friction. The clear button should have proper accessibility roles and `hitSlop` to meet minimum touch target standards.
**Action:** When creating or modifying a searchable or editable text component, check if a clear action would benefit the user, especially when the input is used as a filter or search field. Apply conditional rendering `!!value && (<Pressable ...>...</Pressable>)`. Always remember to ensure translations are covered for `accessibilityLabel` of the clear button.

## 2024-05-18 - Ensure text input accessibility state reflects disabled prop

**Learning:** React Native TextInputs do not automatically announce their `editable={false}` state to screen readers. We must explicitly set `accessibilityState={{ disabled: true }}`.
**Action:** When creating wrapper components for `TextInput`, always explicitly map the `editable` prop to `accessibilityState.disabled`, merging with any existing `accessibilityState` passed by the consumer.
