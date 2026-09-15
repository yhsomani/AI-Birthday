## 2024-05-31 - [ReDoS Vulnerability in Obfuscated Domain Regex]

**Vulnerability:** Found a ReDoS (Regular Expression Denial of Service) vulnerability in `URL_OBFUSCATED_DOMAIN` regex in `src/domain/validation/templateDraft.ts`. The pattern `\s*(?:\[\s*dot\s*\]|\(\s*dot\s*\)|\s+dot\s+)\s*` causes catastrophic backtracking because the trailing `\s*` of the first part overlaps with the leading/trailing spaces inside the alternation.
**Learning:** Overlapping quantifiers (like `\s*` next to `\s+` inside the alternation) cause catastrophic backtracking on long strings of whitespace when a match fails.
**Prevention:** Avoid putting optional space matchers outside an alternation if the components inside the alternation also match spaces on their boundaries. Restructure to distribute the outer `\s*` inside the non-capturing group.
