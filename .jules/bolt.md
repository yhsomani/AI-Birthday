## 2024-08-10 - Defer Search Query state in List Filtering
**Learning:** Performing synchronous list filtering based on rapidly changing text input state inside `useMemo` can block the main UI thread during typing.
**Action:** Use `useDeferredValue` to wrap the rapidly changing state before passing it to the expensive filtering logic to delegate heavy computation to a background task and prevent blocking the main UI thread during typing.
