## 2023-10-02 - Optimize AppLogger redaction
**Learning:** Using a loop that iterates over a constant set of string fragments and performs `.toLowerCase().replaceAll()` inside high-frequency loggers causes significant overhead.
**Action:** Pre-compile a case-insensitive `RegExp` (e.g., `RegExp(r'(...)', caseSensitive: false)`) to match sensitive keys. This drastically improves string matching performance.
## 2024-05-18 - Pre-compiled RegExp for Logging Performance
**Learning:** The logging global redaction loop iterated dynamically over a Set of strings on every log parameter, executing `.toLowerCase()` and `.replaceAll()` multiple times.
**Action:** Replace the loop with a pre-compiled, case-insensitive `RegExp` to drastically speed up text matching.

## 2024-05-18 - Single Loop Over Collections with Expensive Predicates
**Learning:** Calling `.where().toList()` multiple times on a collection with an expensive predicate (like `DateTime` instantiations) scales poorly and causes redundant processing.
**Action:** Replace multiple `.where()` filters with a single manual `for` loop that evaluates the condition once per item and partitions it into the target lists.
