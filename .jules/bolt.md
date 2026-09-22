## 2024-05-18 - Deferring Search Query Evaluation

**Learning:** Performing expensive synchronous list filtering in useMemo based on rapidly changing text input state (like a search bar) blocks the main UI thread during typing.
**Action:** Wrap the search query state with useDeferredValue before using it in the array filter to delegate the computation to a background task and maintain UI responsiveness.
