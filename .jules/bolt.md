## 2024-08-09 - Deferred Search Query Filtering
**Learning:** Using `useDeferredValue` to wrap a search query state before using it in a heavy synchronous `useMemo` filter array prevents blocking the main thread during rapid typing on React Native.
**Action:** Use `useDeferredValue` for fast-changing search inputs that drive expensive list filtering operations.
