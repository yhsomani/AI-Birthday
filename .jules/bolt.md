
## 2024-10-01 - Optimize string matching in log redaction
**Learning:** Repeated `toLowerCase()` and `replaceAll()` operations within loops over string fragments in high-frequency loggers introduce significant overhead.
**Action:** Pre-compile a single, case-insensitive `RegExp` to replace constant sets of string fragments.
