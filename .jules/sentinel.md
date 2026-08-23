## 2024-05-16 - Prevent ReDoS with Bounded Quantifiers

**Vulnerability:** The `URL_DOMAIN` regex `\b(?:...)+\b` used an unbounded repeating group quantifier (`+`) inside another group, allowing for exponential catastrophic backtracking (ReDoS) against deliberately crafted repeating strings that fail at the end (e.g. `'a.a.a.a.a...X'`).
**Learning:** Overlapping repetition patterns can severely freeze Node.js/Regex threads and cause Denial of Service when validating user input.
**Prevention:** Avoid unbounded quantifiers (`+`, `*`) on sub-patterns that can match varying lengths of similar content. Always bound quantifiers to reasonable realistic limits (e.g., `{1,127}` for domain depth, or `{1,254}` for length) to tightly control maximum evaluation time.
