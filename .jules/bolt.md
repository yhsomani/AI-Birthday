## 2024-05-18 - React Native Search Filter Performance

**Learning:** Performing a synchronous `.filter()` over a list in a `useMemo` based on rapid string input (like `SearchField` typing) blocks the main UI thread during typing, making input feel sluggish on large lists.
**Action:** Wrap the `query` search state in `useDeferredValue` before using it inside the `useMemo` filter array. This tells React to defer the expensive array filtering computation until after the main UI update, preventing blocking during typing.
