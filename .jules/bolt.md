## 2024-05-18 - Pre-compiled RegExp for Logging Performance
**Learning:** The logging global redaction loop iterated dynamically over a Set of strings on every log parameter, executing `.toLowerCase()` and `.replaceAll()` multiple times.
**Action:** Replace the loop with a pre-compiled, case-insensitive `RegExp` to drastically speed up text matching.

## 2024-05-18 - Single Loop Over Collections with Expensive Predicates
**Learning:** Calling `.where().toList()` multiple times on a collection with an expensive predicate (like `DateTime` instantiations) scales poorly and causes redundant processing.
**Action:** Replace multiple `.where()` filters with a single manual `for` loop that evaluates the condition once per item and partitions it into the target lists.
