## 2024-05-14 - Optimize list filtering using useDeferredValue

**Learning:** Synchronous array filtering based on rapidly changing text input (like a search bar) can block the main UI thread during typing, causing stutter or dropped frames.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate the heavy computation to a background task and prevent blocking the UI.
