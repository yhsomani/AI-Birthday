## 2024-09-12 - Prevent UI Blocking with useDeferredValue

**Learning:** Performing expensive synchronous list filtering in useMemo based on rapidly changing text input state (like a search bar) can block the main UI thread during typing, causing a laggy user experience in React Native.
**Action:** Wrap the search query state with useDeferredValue before using it in the array filter to delegate the heavy computation to a background task and keep the UI responsive.
