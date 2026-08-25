## 2024-08-25 - Prevent UI blocking during search filtering

**Learning:** Performing expensive synchronous array filtering in `useMemo` based on rapidly changing text input state can block the main UI thread in React Native, leading to dropped frames and typing lag.
**Action:** When filtering lists based on a search input, wrap the search query state with `useDeferredValue` before using it in the array filter to delegate the heavy computation to a background task and keep the UI responsive.
