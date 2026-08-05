## 2025-08-05 - Optimize Search Input with useDeferredValue

**Learning:** Heavy synchronous array filtering triggered by rapidly changing search input (like `SearchField` updates) can block the main UI thread in React Native, leading to sluggish typing and poor responsiveness.
**Action:** Wrap the fast-updating search state with `useDeferredValue` before using it as a dependency in expensive `useMemo` filters to defer the computation and keep the UI thread unblocked.
