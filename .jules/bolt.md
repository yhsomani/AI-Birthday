## 2024-10-24 - Optimized Logger Parameter Redaction

**Learning:** Frequent loop-based array iterations with redundant `toLowerCase()` calls and string allocations inside loggers create significant CPU overhead, specifically O(N) where N is number of iterations over key fragments.
**Action:** Replace string-matching loops for redactions with pre-compiled, case-insensitive Regular Expressions to collapse the matching logic to a single DFA traversal per logged parameter, reducing latency dramatically.

## 2024-05-24 - [Optimize Redaction Regex]

**Learning:** String `replaceAll` and `toLowerCase` operations inside loops during parameter redaction represent a significant logging overhead in Dart.
**Action:** Pre-compile `RegExp` with `caseSensitive: false` to optimize frequent string filtering logic.

## 2023-10-02 - Optimize AppLogger redaction

**Learning:** Using a loop that iterates over a constant set of string fragments and performs `.toLowerCase().replaceAll()` inside high-frequency loggers causes significant overhead.
**Action:** Pre-compile a case-insensitive `RegExp` (e.g., `RegExp(r'(...)', caseSensitive: false)`) to match sensitive keys. This drastically improves string matching performance.

## 2024-05-18 - Pre-compiled RegExp for Logging Performance

**Learning:** The logging global redaction loop iterated dynamically over a Set of strings on every log parameter, executing `.toLowerCase()` and `.replaceAll()` multiple times.
**Action:** Replace the loop with a pre-compiled, case-insensitive `RegExp` to drastically speed up text matching.

## 2024-05-18 - Single Loop Over Collections with Expensive Predicates

**Learning:** Calling `.where().toList()` multiple times on a collection with an expensive predicate (like `DateTime` instantiations) scales poorly and causes redundant processing.
**Action:** Replace multiple `.where()` filters with a single manual `for` loop that evaluates the condition once per item and partitions it into the target lists.

## 2026-10-03 - Optimize Logger Parameter Redaction

**Learning:** When checking strings against a fixed set of patterns or fragments in a hot code path (like a logger), iterating over an array and performing string manipulation (`toLowerCase()`, `replaceAll()`) on every iteration incurs heavy string allocation and matching overhead.
**Action:** Use a single, pre-compiled, case-insensitive `RegExp` instead of looping through a collection of fragments. This reduces O(N) operations with multiple intermediate string allocations to a single highly-optimized regex match.

## 2024-10-24 - Single Pass Predicate Counting

**Learning:** Calling `.where().length` multiple times on the same collection for mutually exclusive conditions (like `isPotentialDuplicate` and `!isPotentialDuplicate`) iterates over the collection redundantly.
**Action:** Iterate once to count one condition, and use arithmetic (e.g., `total - condition`) for the complement to halve the iterations and CPU cycles during widget builds.
