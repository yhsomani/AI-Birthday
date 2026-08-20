## 2024-08-20 - Prevent ReDoS by Bounding Regex Quantifiers

**Vulnerability:** Found unbounded quantifiers (`*`, `+`) inside `PHONE_NUMBER` and `TRACKING_OR_AFFILIATE` regexes which can lead to ReDoS vulnerabilities.
**Learning:** Overly broad quantifiers, particularly inside repetitive structures, can cause catastrophic backtracking when matching malicious input strings.
**Prevention:** Always explicitly bound quantifiers (e.g., `{0,254}`) when matching user input to avoid severe performance impact and Denial of Service.
