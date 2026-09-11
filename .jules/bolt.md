## 2025-01-24 - React Native Filter Performance Pattern

**Learning:** Performing expensive synchronous list filtering inside a useMemo based on a rapidly changing text input (like a search bar) can block the main UI thread during typing in React Native.
**Action:** Wrap the search query state with useDeferredValue before using it in the array filter to delegate heavy computation to a background task and keep the UI responsive.
