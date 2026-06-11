## 2026-06-09 - Adding 'Clear Search' to OutlinedTextField
**Learning:** Used a `trailingIcon` conditionally inside Jetpack Compose's `OutlinedTextField` to only show a 'Clear' (`IconButton` + `Close` icon) when the query is not empty. Added proper `contentDescription` to improve screen reader accessibility.
**Action:** When implementing searchable inputs in Compose, remember to include conditionally rendered clear buttons with descriptive ARIA-like labels.
## 2026-06-11 - Add confirmation dialog for Memory Vault deletion
**Learning:** Found that deleting memories was happening immediately on click. Implemented an `AlertDialog` confirmation to prevent accidental deletions. Required updating `strings.xml` and `values-hi/strings.xml` to maintain translation parity.
**Action:** Always wrap destructive actions (like delete) with an `AlertDialog` using a local state tracker (`noteToDelete`) to confirm user intent before execution. Don't forget to update all localization files for any new strings.
