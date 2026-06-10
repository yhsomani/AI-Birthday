## 2026-06-09 - Adding 'Clear Search' to OutlinedTextField
**Learning:** Used a `trailingIcon` conditionally inside Jetpack Compose's `OutlinedTextField` to only show a 'Clear' (`IconButton` + `Close` icon) when the query is not empty. Added proper `contentDescription` to improve screen reader accessibility.
**Action:** When implementing searchable inputs in Compose, remember to include conditionally rendered clear buttons with descriptive ARIA-like labels.
## 2026-06-10 - Adding Confirmation Dialog for Destructive Actions
**Learning:** Found that a destructive action (`deleteNote`) in a Jetpack Compose UI was missing a confirmation dialog, which caused immediate and unrecoverable deletion on click. It can be solved elegantly using Jetpack Compose's `AlertDialog` triggered by local state pointing to the object to delete.
**Action:** When creating destructive actions (such as delete) in Jetpack Compose, always use a local state tracker (e.g. `var itemToDelete by remember { mutableStateOf<Type?>(null) }`) coupled with an `AlertDialog` to prompt for user confirmation rather than executing the deletion immediately.
