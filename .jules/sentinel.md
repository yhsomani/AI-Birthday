## 2024-08-14 - Fix ReDoS vulnerabilities in content classification regexes

**Vulnerability:** Unbounded nested quantifiers (`+`, `*`) in URL, Email, Tracking, and Phone regular expressions (e.g., `(?: ... )+` in URL_DOMAIN) allowed catastrophic backtracking (ReDoS) against deliberately crafted long payloads.
**Learning:** Performance overhead of deep backtracking when processing untrusted strings can cause application blocking/DoS.
**Prevention:** Always bound iteration limits (e.g., `{1,126}`, `{1,254}`) on regexes used to validate external input, avoiding unbounded indefinite quantifiers like `*` and `+` over variable-length groups.
