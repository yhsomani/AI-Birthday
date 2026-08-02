## 2024-05-18 - React Native Search Performance

**Learning:** Heavy synchronous list filtering inside a useMemo hook based on rapidly changing text input blocks the main UI thread, causing lag while typing.
**Action:** Use `useDeferredValue` to wrap the search query state before using it in the array filter to delegate heavy computation to a background task.
