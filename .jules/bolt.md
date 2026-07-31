## 2026-07-31 - [Deferred Search Input]

**Learning:** Filtering arrays on the main thread in React Native using `useMemo` based on rapidly changing search queries can cause UI blocking during fast typing, particularly when the search string length dictates complex matching.
**Action:** Use `useDeferredValue` for the search query passed to the array filter so the component remains responsive during typing without forcing expensive recalculations immediately.
