## 2026-09-28 - Recursive Sanitization of Sensitive Data
**Vulnerability:** Logging infrastructure only redacted top-level keys in parameter maps, potentially leaking sensitive information passed within nested data structures (like Maps and Iterables) into application logs.
**Learning:** Data sanitization and redaction mechanisms must recursively traverse data structures to guarantee all keys are evaluated against sensitive patterns, rather than assuming flat structures.
**Prevention:** Always implement recursive traversal for any redaction, sanitization, or filtering logic applied to unbounded or nested data collections.
