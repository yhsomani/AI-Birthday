## 2024-05-24 - React Native Performance Pattern
**Learning:** When performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar), wrap the search query state with `useDeferredValue` before using it in the array filter. This delegates the heavy computation to a background task and prevents blocking the main UI thread during typing.
**Action:** Use `useDeferredValue` for query states used in `useMemo` filters.
