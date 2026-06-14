## 2026-06-03 - LazyColumn Performance in Compose
**Learning:** `LazyColumn` without stable keys can cause significant performance degradation (dropping below 60fps) during scrolling, especially with complex list items like images or formatted text. This happens because Compose cannot easily identify which items have moved, been added, or been removed, leading to unnecessary recompositions. Using `LazyPagingItems` makes it slightly less obvious how to add a key compared to a standard `List`, but it's critical.
**Action:** Always provide a stable, unique `key` parameter to `items` or `itemsIndexed` within a `LazyColumn` or `LazyRow`. For `LazyPagingItems`, use `items.itemKey { it.uniqueId }`.

## 2026-06-03 - Room DB Auto Migration Crash
**Learning:** If a manual migration adds an index using `CREATE INDEX`, the corresponding  annotation must explicitly define the `name` of the index to match what was created in the migration script. If the `name` is omitted in the annotation, Room may auto-generate a different name, causing a schema mismatch exception during database initialization because the expected auto-generated name does not match the explicitly named index found in the database.
**Action:** Always provide explicit `name` attributes for  definitions in Room  classes, especially when manual migrations are involved or might be involved in the future, to ensure schema validation passes.


## 2026-06-03 - Room DB Auto Migration Crash
**Learning:** If a manual migration adds an index using `CREATE INDEX`, the corresponding `@Entity` annotation must explicitly define the `name` of the index to match what was created in the migration script. If the `name` is omitted in the annotation, Room may auto-generate a different name, causing a schema mismatch exception during database initialization because the expected auto-generated name does not match the explicitly named index found in the database.
**Action:** Always provide explicit `name` attributes for `@Index` definitions in Room `@Entity` classes, especially when manual migrations are involved or might be involved in the future, to ensure schema validation passes.

## 2024-06-07 - Refactoring prompt strings to buildString
**Learning:** In large templates with heavy interpolation and multiple dynamic variables, using `.trimIndent()` with triple-quotes creates unnecessary intermediate objects. Converting to `buildString` minimizes temporary allocations and reduces GC pressure in low-memory environments.
**Action:** Use `buildString { appendLine(...) }` instead of multiline raw string interpolation for dynamically building large configuration text and AI prompt inputs.

## 2024-06-07 - Lint compatibility for java.time on older API levels
**Learning:** In projects with `minSdk < 26` where `coreLibraryDesugaring` is not explicitly enabled, usage of modern Java Time APIs like `java.time.LocalDate` will result in `NewApi` lint errors and potentially fail the CI build.
**Action:** Use traditional `java.util.Calendar` or check for desugaring before using `java.time` APIs in worker or scheduled logic.

## 2026-06-09 - Compose List Performance without Keys
**Learning:** For dynamic lists such as chat histories or generated items, omitting the `key` parameter in `LazyColumn.items` can cause unnecessary UI recompositions. This is specifically relevant when updates occur frequently or lists grow long.
**Action:** Always provide stable `key` closures in `LazyColumn` and `LazyRow` items blocks using unique Entity IDs.
## 2024-06-14 - Regex Instantiation in Loops
**Learning:** `StyleAnalysisUseCase` instantiated multiple `Regex` objects inside `forEach` loops while processing texts. This creates unnecessary recompilation overhead and garbage collection pressure, especially given it analyzes large volumes of messages.
**Action:** Extract `Regex` objects into a `companion object` to ensure they are compiled exactly once when the class is loaded.
