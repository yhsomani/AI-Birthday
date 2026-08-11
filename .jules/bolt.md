## 2025-02-18 - Use deferred value for search filtering

**Learning:** Expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar) can block the main UI thread during typing.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to a background task.
