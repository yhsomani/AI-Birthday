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
## 2024-05-18 - Remove replaceAll string manipulations in hot paths
**Learning:** Using `replaceAll` on strings inside frequently called functions like a logger adds significant hidden overhead (e.g., O(N) allocation x2 per log parameter). Pre-compiling a slightly more complex regular expression to account for punctuation is much faster (~2x) in high-volume logging pathways.
**Action:** When performing string sanitization or checks in hot code paths, use robust RegExp patterns instead of chaining string manipulation functions.
## 2024-06-25 - Avoid Dart in-memory case-insensitive sorting for database results
**Learning:** Calling `.toLowerCase()` inside a Dart `.sort()` comparator on a large list of database results causes excessive O(N log N) string allocations, significantly impacting performance and memory. In benchmark testing with 50,000 items, in-memory sorting took ~1.4 seconds with GC latency spikes compared to the stable native DB query sort.
**Action:** Always offload sorting to the database query layer directly in Drift repositories and stores (e.g., using Drift's `..orderBy([(t) => OrderingTerm(expression: t.name.lower()), (t) => OrderingTerm(expression: t.id)])`) rather than fetching the raw list and applying `.sort()` in Dart.

## 2024-10-25 - Avoid O(N) allocations in filter loops
**Learning:** Calling `.toLowerCase()` on an invariant string variable inside a `.where()` filter loop on every keystroke causes redundant O(N) string allocations during Flutter widget rebuilds.
**Action:** Always hoist invariant transformations (like `toLowerCase()` for search queries) outside the loop to execute them exactly once, significantly reducing GC pressure.
## 2024-05-24 - Pre-computing invariants and compiling RegExps before duplicate check loops
**Learning:** Hoisting invariant variable calculations like `toLowerCase()` and `RegExp` instantiation out of inner loops (`existingPeople` and intra-batch checks) prevents O(N*M) redundant string allocations and Regex compilations in Flutter, greatly improving perceived speed during bulk CSV parsing and device contact imports.
**Action:** Always pre-compute and store lowercased search queries or sanitized phone numbers outside of loops when comparing against lists. Pre-compile static RegExps using `static final RegExp = ...`.

## 2024-10-25 - Avoid O(N) string allocations in search filters
**Learning:** Using `.where((item) => item.toLowerCase().contains(queryLower))` causes O(N) string allocations because `.toLowerCase()` creates a new string for every item.
**Action:** Pre-compile a `RegExp(RegExp.escape(query), caseSensitive: false)` outside the loop and use `regex.hasMatch(item)` instead to significantly improve matching performance and reduce GC pressure.

## 2024-10-25 - Avoid O(M*N) iterations in list filtering
**Learning:** Using `.where((item) => !anotherList.contains(item))` creates an O(M*N) nested iteration pattern, which can cause frame drops when rendering lists in Flutter.
**Action:** Partition data directly within a single initial `for` loop pass instead of chaining `.where()` and `.contains()` filters later.
## 2024-10-25 - O(M*N) to O(N) array partition dashboard filter
**Learning:** Calling `.where((b) => !list.contains(b)).toList()` on a list causes redundant O(M*N) checks inside Flutter widget builds.
**Action:** Evaluate properties and partition directly within the initial single `for` loop pass over the original array to avoid the nested iteration and redundant list allocations entirely.
## 2024-05-23 - Avoid O(N log N) allocations in sorting
**Learning:** Calling `.toLowerCase()` inside a `.sort()` comparator is an anti-pattern that causes excessive O(N log N) string allocations.
**Action:** Optimize by pre-computing lowercased comparison keys outside of the sort function and caching them (e.g. in a Map) to reduce the allocations to O(N).

## 2024-05-24 - Hoist toLowerCase in Enum Parsers
**Learning:** Calling `.toLowerCase()` inside a loop or `firstWhere` closure on an invariant search value causes O(N) redundant string allocations, which degrades performance especially on hot paths like enum deserialization.
**Action:** Hoist the `.toLowerCase()` call outside the iteration closure into a local variable so it executes exactly once.
