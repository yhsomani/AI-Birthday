## 2023-10-27 - [Debounce fast-changing list filtering inputs in React Native]
**Learning:** Performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar) blocks the main UI thread.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to a background task and prevent blocking the main UI thread.
