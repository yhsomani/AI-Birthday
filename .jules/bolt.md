## 2025-02-23 - Use useDeferredValue for performant React Native search filters

**Learning:** When performing expensive synchronous list filtering in useMemo based on rapidly changing text input state (like a search bar), it blocks the main thread causing lag.
**Action:** Wrap the search query state with useDeferredValue before using it in the array filter to delegate the heavy computation and prevent blocking the main UI thread during typing.
