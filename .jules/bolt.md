## 2026-07-26 - Use useDeferredValue for React Native search filters

**Learning:** In React Native, filtering a large list synchronously during text input (like a search bar) can block the main UI thread and cause typing lag. Wrapping the search query state with `useDeferredValue` delegates the heavy computation to a background task and prevents blocking the main UI thread, without needing to debounce the actual input state update.
**Action:** Use `useDeferredValue` on the query state inside `useMemo` blocks for client-side list filtering to improve typing responsiveness on search fields.
