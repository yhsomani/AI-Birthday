## 2024-08-23 - Prevent main thread blocking during search

**Learning:** React Native's main thread can be easily blocked by expensive synchronous operations inside `useMemo`, like filtering a large list based on a rapidly changing text input state.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to a background task.
