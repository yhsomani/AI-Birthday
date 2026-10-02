## 2023-10-02 - Optimize AppLogger redaction
**Learning:** Using a loop that iterates over a constant set of string fragments and performs `.toLowerCase().replaceAll()` inside high-frequency loggers causes significant overhead.
**Action:** Pre-compile a case-insensitive `RegExp` (e.g., `RegExp(r'(...)', caseSensitive: false)`) to match sensitive keys. This drastically improves string matching performance.
