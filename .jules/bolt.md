## YYYY-MM-DD - Initial
**Learning:** Initialize Bolt's journal.
**Action:** Let's keep making things faster.
## 2024-08-12 - Prevent main thread blocking on People search
**Learning:** React Native Performance Pattern: When performing expensive synchronous list filtering in useMemo based on rapidly changing text input state, wrap the search query state with useDeferredValue before using it in the array filter to prevent blocking the main UI thread during typing.
**Action:** Always consider useDeferredValue for text-based filtering of large lists in React Native to keep typing responsive.
