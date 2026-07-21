## 2024-07-21 - Prevent Main Thread Blocking in React Native Text Inputs

**Learning:** Performing synchronous list filtering within `useMemo` that directly depends on a fast-changing text input state (like a search bar `query`) blocks the main JS thread during typing. This causes visible stuttering for the user.
**Action:** Always wrap text input state tied to heavy filtering with `useDeferredValue` before passing it to `useMemo`. This allows React Native to prioritize UI updates (like rendering the typed characters) and process the heavy list filtering in the background.
