## 2024-08-28 - Defer List Filtering

**Learning:** Filtering long lists in React Native directly on rapid text input (search bars) blocks the main thread, leading to UI stuttering and unresponsiveness.
**Action:** Wrap the rapidly changing state (e.g., search query) with `useDeferredValue` before using it in expensive synchronous computations like array filters inside `useMemo`. This defers the computation to a background task, ensuring the UI (especially text input) remains buttery smooth.
