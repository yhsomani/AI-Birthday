1. **Memory Vault UX Issue:** The `MemoryNoteCard` in `MemoryVaultScreen.kt` currently executes the delete action immediately upon pressing the delete `IconButton`.
2. **UX Standard:** As per memory (`.Jules/palette.md`), destructive actions like deletions should have a confirmation step using `AlertDialog` and local state tracker instead of executing immediately.
3. **Plan:**
   - Update `strings.xml` to include `memory_vault_delete_confirm_title` and `memory_vault_delete_confirm_text`.
   - Update `MemoryVaultScreen.kt` to introduce a `itemToDelete` mutable state.
   - When the delete `IconButton` in `MemoryNoteCard` is clicked, set `itemToDelete` to that note instead of calling `viewModel.deleteNote()`.
   - Add an `AlertDialog` in the `MemoryVaultScreen` that shows up when `itemToDelete` is not null. Upon confirmation, execute the delete and set state to null.
   - Or alternatively, pass a local confirmation state inside `MemoryVaultScreen` items block.
