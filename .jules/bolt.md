## 2025-08-03 - Deferred List Filtering for Performance

**Learning:** When performing expensive synchronous list filtering in useMemo based on rapidly changing text input state (like a search bar) in React Native, the heavy computation blocks the main UI thread during typing, causing jank.
**Action:** Wrap the search query state with useDeferredValue before using it in the array filter to delegate the heavy computation to a background task.
