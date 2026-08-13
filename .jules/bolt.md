## 2025-02-27 - [Debounce Search Input]

**Learning:** [React Native Performance Pattern: When performing expensive synchronous list filtering in useMemo based on rapidly changing text input state, wrap the search query state with useDeferredValue before using it in the array filter to prevent blocking the main UI thread during typing.]
**Action:** [Use useDeferredValue for rapidly changing text input state to prevent blocking the main UI thread during typing.]
