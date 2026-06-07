## 2024-06-07 - Refactoring prompt strings to buildString
**Learning:** In large templates with heavy interpolation and multiple dynamic variables, using `.trimIndent()` with triple-quotes creates unnecessary intermediate objects. Converting to `buildString` minimizes temporary allocations and reduces GC pressure in low-memory environments.
**Action:** Use `buildString { appendLine(...) }` instead of multiline raw string interpolation for dynamically building large configuration text and AI prompt inputs.
