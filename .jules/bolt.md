## 2024-05-24 - React Native search filter performance pattern
**Learning:** Performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar) can block the main UI thread during typing, leading to laggy input.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate the heavy computation and prevent blocking the main thread during rapid typing.
