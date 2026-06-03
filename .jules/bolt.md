## 2026-06-03 - LazyColumn Performance in Compose
**Learning:** `LazyColumn` without stable keys can cause significant performance degradation (dropping below 60fps) during scrolling, especially with complex list items like images or formatted text. This happens because Compose cannot easily identify which items have moved, been added, or been removed, leading to unnecessary recompositions. Using `LazyPagingItems` makes it slightly less obvious how to add a key compared to a standard `List`, but it's critical.
**Action:** Always provide a stable, unique `key` parameter to `items` or `itemsIndexed` within a `LazyColumn` or `LazyRow`. For `LazyPagingItems`, use `items.itemKey { it.uniqueId }`.

## 2026-06-03 - Room DB Auto Migration Crash
**Learning:** If a manual migration adds an index using `CREATE INDEX`, the corresponding  annotation must explicitly define the `name` of the index to match what was created in the migration script. If the `name` is omitted in the annotation, Room may auto-generate a different name, causing a schema mismatch exception during database initialization because the expected auto-generated name does not match the explicitly named index found in the database.
**Action:** Always provide explicit `name` attributes for  definitions in Room  classes, especially when manual migrations are involved or might be involved in the future, to ensure schema validation passes.


## 2026-06-03 - Room DB Auto Migration Crash
**Learning:** If a manual migration adds an index using `CREATE INDEX`, the corresponding `@Entity` annotation must explicitly define the `name` of the index to match what was created in the migration script. If the `name` is omitted in the annotation, Room may auto-generate a different name, causing a schema mismatch exception during database initialization because the expected auto-generated name does not match the explicitly named index found in the database.
**Action:** Always provide explicit `name` attributes for `@Index` definitions in Room `@Entity` classes, especially when manual migrations are involved or might be involved in the future, to ensure schema validation passes.
