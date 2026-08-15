## 2024-05-20 - React Native Text Input Filtering Performance

**Learning:** Performing expensive synchronous list filtering in `useMemo` directly based on a rapidly changing text input state (like a search bar) can block the main UI thread during typing in React Native.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to a background task and keep typing responsive.
