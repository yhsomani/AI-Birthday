## 2024-05-24 - React Native text input list filtering optimization

**Learning:** When performing expensive synchronous list filtering in `useMemo` based on rapidly changing text input state (like a search bar), React Native's main thread can become blocked, causing text input lag.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter. This delegates the heavy computation to a background task and prevents blocking the main UI thread during typing.
