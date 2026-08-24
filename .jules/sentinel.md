## 2024-08-24 - Fix ReDoS in URL_DOMAIN regex

**Vulnerability:** The URL_DOMAIN regex contained an unbounded repeating group (?:...)+ which caused catastrophic backtracking (ReDoS) when processing maliciously crafted large invalid strings.
**Learning:** Unbounded or nested quantifiers in regular expressions applied to uncontrolled text inputs can severely degrade performance and cause Denial of Service.
**Prevention:** Always bound repeating groups in regular expressions (e.g., using {1,254} instead of +) when parsing or validating unbounded user input.
