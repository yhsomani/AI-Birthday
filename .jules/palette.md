## 2026-07-18 - Search Field Clear Button

**Learning:** When conditionally rendering elements based on string properties (e.g., showing a clear button when text length > 0), use safe truthiness checks (like `!!value`) rather than unsafe property access (like `value.length > 0`) to prevent runtime crashes if the value is uncontrolled or undefined.
**Action:** Always use safe truthiness checks (`!!value`) for conditional rendering based on string lengths.
