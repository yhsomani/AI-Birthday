## 2024-05-24 - Pre-compile RegExp for faster log redaction
**Learning:** Dart Performance Pattern: Replacing loops that iterate over a constant set of string fragments with `toLowerCase()` and `replaceAll()` operations with a single, pre-compiled, case-insensitive `RegExp` significantly improves string matching performance.
**Action:** Use pre-compiled `RegExp` for repeated string matching operations instead of looping through a set of substrings and performing manual normalizations inside the loop.
