## 2026-07-19 - Defer non-critical UI state updates

**Learning:** Using synchronous list filtering in `useMemo` triggered by every state update (e.g., from typing in a search input) can block the main thread and cause sluggish UI rendering for long lists in React Native.
**Action:** Use `useDeferredValue` on rapidly changing state variables to allow React to deprioritize expensive array filtering while keeping input components responsive.
