## 2024-09-26 - Recursive Logging Redaction
**Vulnerability:** Sensitive nested objects in log parameters were not being redacted.
**Learning:** Log redaction must recursively traverse dictionaries and lists.
**Prevention:** Always ensure data sanitization functions handle arbitrarily nested data structures.
