## 2023-10-27 - Deferred Value for Expensive Filtering

**Learning:** Performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state blocks the main UI thread during typing.
**Action:** Use `useDeferredValue` to wrap the search query state before using it in the array filter, delegating the heavy computation to a background task and keeping the UI responsive.
