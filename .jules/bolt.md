## 2026-08-22 - Optimize search filtering with useDeferredValue

**Learning:** React Native Performance Pattern: When performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar), wrapping the search query state with `useDeferredValue` before using it in the array filter delegates the heavy computation to a background task and prevents blocking the main UI thread during typing.
**Action:** Always wrap text input state with `useDeferredValue` when passing it to expensive synchronous `useMemo` computations.
