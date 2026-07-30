## 2025-01-20 - React Native Search Filtering Performance

**Learning:** Performing synchronous list filtering inside `useMemo` based on rapidly changing text input state (like a search bar) can block the main UI thread during typing, causing stuttering input fields.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to the background.
