## 2024-09-23 - Deferred Value for Search Filtering

**Learning:** Synchronous list filtering in useMemo based on text input state can block the main UI thread during typing.
**Action:** When filtering lists based on a rapidly changing search query in React Native, wrap the query state in useDeferredValue before passing it to useMemo to keep the UI thread responsive.
