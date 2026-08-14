## 2024-05-24 - React Native Search Performance

**Learning:** When performing list filtering in `useMemo` based on rapidly changing text input state, blocking the main UI thread causes input lag.
**Action:** Wrap the search query state with `useDeferredValue` before using it in the array filter to delegate heavy computation to a background task and maintain UI responsiveness during typing.
