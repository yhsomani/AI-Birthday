## 2025-02-14 - Optimize synchronous list filtering with useDeferredValue

**Learning:** Performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar) can block the main UI thread during typing, causing UI stuttering.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to a background task and prevent blocking the main UI thread.
