## 2025-02-18 - Fix Catastrophic Backtracking (ReDoS) in URL Domain Regex

**Vulnerability:** A ReDoS vulnerability was found in the `URL_DOMAIN` regular expression (`\b(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,62}[\p{L}\p{N}])?\.)+...`) used to validate template drafts. The unconstrained `+` quantifier could cause the regex engine to hang on specially crafted input strings.
**Learning:** Nested quantifiers, especially inside unconstrained repetition loops, can lead to exponential backtracking times, presenting a Denial-of-Service risk if an attacker inputs extremely long, non-matching but "near-match" strings.
**Prevention:** Always use explicit bounding for quantifiers (e.g., `{1,254}` instead of `+`) when evaluating user inputs with regular expressions to avoid indefinite repetition.
