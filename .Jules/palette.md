## 2026-06-09 - Adding 'Clear Search' to OutlinedTextField
**Learning:** Used a `trailingIcon` conditionally inside Jetpack Compose's `OutlinedTextField` to only show a 'Clear' (`IconButton` + `Close` icon) when the query is not empty. Added proper `contentDescription` to improve screen reader accessibility.
**Action:** When implementing searchable inputs in Compose, remember to include conditionally rendered clear buttons with descriptive ARIA-like labels.
## 2026-06-13 - Added Confirmation Dialog for Deletions\n**Learning:** Destructive actions like deletions in Jetpack Compose should require a confirmation  triggered by a state variable to prevent accidental data loss. Immediate execution on click is poor UX.\n**Action:** When implementing any delete function, add a local state variable to track the item to delete and show a confirmation dialog before proceeding.
## 2026-06-13 - Added Confirmation Dialog for Deletions
**Learning:** Destructive actions like deletions in Jetpack Compose should require a confirmation AlertDialog triggered by a state variable to prevent accidental data loss. Immediate execution on click is poor UX.
**Action:** When implementing any delete function, add a local state variable to track the item to delete and show a confirmation dialog before proceeding.
