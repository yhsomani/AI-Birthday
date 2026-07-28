## 2024-05-14 - React Native search filter optimization
**Learning:** Performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar) can block the main UI thread during typing, causing stutter.
**Action:** Use `useDeferredValue` to wrap the search query state before using it in the array filter to delegate heavy computation and prevent UI blocking.
