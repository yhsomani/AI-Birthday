## 2024-09-01 - Use deferred value for search filtering

**Learning:** Performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state can block the main UI thread during typing.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to a background task and keep typing responsive.
