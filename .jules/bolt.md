## 2026-08-20 - Main Thread Blocking on Search

**Learning:** React Native's main UI thread can be blocked by expensive synchronous array filtering in `useMemo` when triggered by rapid state updates from text inputs (like search bars).
**Action:** Use `useDeferredValue` on the rapid-updating state (e.g., `query`) before passing it to the heavy `useMemo` computation. This delegates the computation to a background-like priority, keeping the UI thread responsive during fast typing.
