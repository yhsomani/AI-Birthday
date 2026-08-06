## 2024-08-06 - Deferred Value Pattern specific to Birthday Autopilot's Large Lists
**Learning:** In the `PeopleScreen.tsx`, wrapping the rapid-changing search `query` with `useDeferredValue` prevents the main UI thread from blocking during the synchronous evaluation of `fixturePeople.filter()`.
**Action:** When filtering potentially large arrays derived from memory (like `fixturePeople` or offline database sync records) inside `useMemo`, apply `useDeferredValue` to the search input state to delegate the expensive computation and maintain a 60fps UI experience during fast typing.
