## 2024-05-23 - React Native Performance Pattern: useDeferredValue for synchronous list filtering

**Learning:** When performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar), the main thread can be blocked during typing.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter. This delegates the heavy computation to a background task and prevents blocking the main UI thread during typing.
