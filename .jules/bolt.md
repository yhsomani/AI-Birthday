## 2024-10-24 - Optimized Logger Parameter Redaction
**Learning:** Frequent loop-based array iterations with redundant `toLowerCase()` calls and string allocations inside loggers create significant CPU overhead, specifically O(N) where N is number of iterations over key fragments.
**Action:** Replace string-matching loops for redactions with pre-compiled, case-insensitive Regular Expressions to collapse the matching logic to a single DFA traversal per logged parameter, reducing latency dramatically.
