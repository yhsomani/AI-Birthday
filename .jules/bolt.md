## 2026-07-23 - Optimizing React Native List Filtering
**Learning:** Performing expensive synchronous operations like list filtering directly inside useMemo based on rapidly changing state (e.g. text input during search) can block the main UI thread in React Native, leading to sluggish keyboard responsiveness.
**Action:** Wrap the rapidly changing state in useDeferredValue before using it within the useMemo dependency array for the list filter. This tells React to defer the expensive re-rendering computation and prioritize keeping the UI (like text inputs) responsive.
