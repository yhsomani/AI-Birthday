## 2025-02-18 - Optimize synchronous list filtering with useDeferredValue

**Learning:** React Native Performance Pattern: When performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar), wrapping the search query state with `useDeferredValue` before using it in the array filter delegates the heavy computation to a background task and prevents blocking the main UI thread during typing.
**Action:** Apply `useDeferredValue` to text input queries that drive expensive synchronous local filtering operations.
