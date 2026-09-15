## 2025-02-24 - React Native Search Performance

**Learning:** Synchronous list filtering inside `useMemo` during rapid text input (like a search bar) blocks the main UI thread in React Native, leading to dropped frames and laggy typing.
**Action:** Always wrap the search query state with `useDeferredValue` before using it in the array filter to delegate the heavy computation to a background task.
