## 2026-06-09 - Adding 'Clear Search' to OutlinedTextField
**Learning:** Used a `trailingIcon` conditionally inside Jetpack Compose's `OutlinedTextField` to only show a 'Clear' (`IconButton` + `Close` icon) when the query is not empty. Added proper `contentDescription` to improve screen reader accessibility.
**Action:** When implementing searchable inputs in Compose, remember to include conditionally rendered clear buttons with descriptive ARIA-like labels.
## 2026-06-09 - Adding confirmation dialog for destructive actions
**Learning:** When adding a destructive action like deleting a record from a list in Jetpack Compose, an immediate execution from an `IconButton` is poor UX. It's safer to implement a confirmation step. Used a local state tracker `giftToDelete` and a Material `AlertDialog` to handle the confirmation workflow before performing the deletion.
**Action:** When implementing destructive interactions such as deletion, always pair it with a confirmation dialog by managing a local null-able state variable representing the item to be acted upon.
