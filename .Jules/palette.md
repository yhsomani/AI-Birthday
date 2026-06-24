## 2026-06-09 - Adding 'Clear Search' to OutlinedTextField
**Learning:** Used a `trailingIcon` conditionally inside Jetpack Compose's `OutlinedTextField` to only show a 'Clear' (`IconButton` + `Close` icon) when the query is not empty. Added proper `contentDescription` to improve screen reader accessibility.
**Action:** When implementing searchable inputs in Compose, remember to include conditionally rendered clear buttons with descriptive ARIA-like labels.
## 2026-06-24 - Adding 'Clear Search' to OutlinedTextField
**Learning:** Added a trailing 'clear text' `IconButton` inside an `OutlinedTextField` in `WishPreviewScreen.kt`. Updated English and Hindi string files for accurate `contentDescription`.
**Action:** When working on editable text fields, conditionally displaying a 'Clear text' icon improves usability, and we must provide accurate string resources for accessibility.
