## 2026-07-29 - [Deferred Search Query]

**Learning:** [React Native Performance Pattern: When performing expensive synchronous list filtering in useMemo based on rapidly changing text input state, delegating the computation to a background task prevents blocking the main UI thread during typing.]
**Action:** [Use useDeferredValue to wrap the search query state before using it in the array filter.]
