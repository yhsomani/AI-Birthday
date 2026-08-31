## 2024-05-24 - Defer heavy search list filtering

**Learning:** Performing synchronous list filtering based on rapidly changing input state (like a search query) in `useMemo` blocks the main UI thread during typing, degrading performance.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the list filter `useMemo`. This delegates the heavy computation to a background task and keeps typing responsive.
