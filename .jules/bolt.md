## 2025-02-26 - Implement useDeferredValue for React Native List Filtering

**Learning:** Performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar) can block the main UI thread during typing, causing a sluggish user experience in React Native apps.
**Action:** Wrap the fast-changing search query state with `useDeferredValue` before using it in the array filter. This delegates the heavy computation to a background task, keeping the main thread free for UI updates.
