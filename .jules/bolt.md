## 2024-07-24 - Initializing Bolt Journal

**Learning:** Initializing journal for critical performance learnings.
**Action:** Append to this file when critical performance learnings are discovered.

## 2024-07-24 - Defer expensive list filtering

**Learning:** Synchronous list filtering in `useMemo` based on rapidly changing text input state can block the main UI thread during typing.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate the computation to a background task.
