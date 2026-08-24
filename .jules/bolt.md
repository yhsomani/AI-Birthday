## 2026-08-24 - Defer Expensive List Filtering

**Learning:** Performing synchronous list filtering inside `useMemo` based on rapid typing (e.g., from a search bar) can block the main UI thread in React Native, leading to dropped frames and laggy input.
**Action:** Wrap rapidly changing text input state with `useDeferredValue` before using it as a dependency in expensive array filtering operations to delegate computation to a background task.
