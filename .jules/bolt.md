## 2024-05-30 - Optimize Log Redaction Performance
**Learning:** Using `String.replaceAll` and `String.toLowerCase()` inside a loop against a large constant set of string fragments is significantly slower than using a single pre-compiled, case-insensitive `RegExp`.
**Action:** Use pre-compiled `RegExp` objects for matching known patterns against strings rather than iterating over string collections with continuous normalizations.
