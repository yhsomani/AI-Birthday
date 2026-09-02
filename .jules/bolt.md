## 2025-01-01 - Add useDeferredValue to PeopleScreen search

**Learning:** Performing synchronous list filtering based on rapid text input state (like a search bar) can block the main UI thread during typing and cause jank.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computations to a background task.
