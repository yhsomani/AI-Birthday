## 2026-06-09 - Adding 'Clear Search' to OutlinedTextField
**Learning:** Used a `trailingIcon` conditionally inside Jetpack Compose's `OutlinedTextField` to only show a 'Clear' (`IconButton` + `Close` icon) when the query is not empty. Added proper `contentDescription` to improve screen reader accessibility.
**Action:** When implementing searchable inputs in Compose, remember to include conditionally rendered clear buttons with descriptive ARIA-like labels.

## 2026-06-09 - Confirmation Dialog for Deletions
**Learning:** Users can accidentally delete items when a simple `IconButton` triggers an immediate delete operation. This is especially risky in scrollable lists.
**Action:** Use a local state tracker (e.g., `var itemToDelete by remember { mutableStateOf<Type?>(null) }`) to show an `AlertDialog` for confirmation before executing destructive actions.
