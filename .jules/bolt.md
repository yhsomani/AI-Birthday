## 2024-08-27 - Deferring text input state for synchronous array filtering

**Learning:** Using synchronous array filtering in `useMemo` that directly depends on a fast-changing text input state (like a search bar) can block the main UI thread during typing, leading to a sluggish user experience, especially in React Native.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate the heavy computation to a background task and prevent blocking the main UI thread.
