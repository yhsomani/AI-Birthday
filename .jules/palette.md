## 2024-05-24 - TextInput accessibilityState

**Learning:** TextInput in React Native doesn't automatically map the `editable={false}` prop to `accessibilityState={{ disabled: true }}`. This leaves screen reader users unaware that an input is disabled.
**Action:** Always explicitly set `accessibilityState={{ ...inputProps.accessibilityState, disabled: inputProps.editable === false }}` on custom input wrappers like AccessibleTextInput to ensure disabled states are announced correctly.
