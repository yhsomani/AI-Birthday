## 2026-07-19 - AccessibleTextInput disabled state

**Learning:** When editable is set to false on a TextInput, it does not automatically announce as disabled to screen readers.
**Action:** Always map editable={false} to accessibilityState={{ disabled: true }} for text inputs.
