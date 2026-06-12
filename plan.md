1. **Analyze:** We need to find ONE small (< 50 lines) performance improvement for Bolt.
2. **Finding the issue:** Examining the memory, we see "Extract `Regex` pattern objects to class properties or `companion object`s instead of instantiating them inline within loops or frequently called functions to prevent unnecessary recompilation overhead."
3. **Investigation:** Looking at `core/domain/src/main/kotlin/com/example/domain/usecase/StyleAnalysisUseCase.kt`, we can see multiple `Regex` patterns instantiated inside loops or functions:
   - Line 52: `Regex("[^a-zA-Z0-9\\s\u0900-\u097F]")` and `Regex("\\s+")` inside a `forEach` loop.
   - Line 65: `Regex("\\s+")` inside a `mapNotNull` loop.
   - Line 75: `Regex("\\s+")` inside a `mapNotNull` loop.
   - Line 172: `Regex("\\s+")` inside a `forEach` loop.
   - Line 173: `Regex("[^a-zA-Z0-9]")` inside a nested `forEach` loop.
4. **Fix:** Extract these `Regex` objects into a `companion object` in `StyleAnalysisUseCase` to prevent recompiling them on every iteration.
5. **Pre-commit:** Run formatting, lint, and tests.
6. **Submit:** Submit with title "⚡ Bolt: Extract Regex patterns to companion object" and appropriate description.
