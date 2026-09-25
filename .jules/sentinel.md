## 2026-09-25 - Data leakage in nested map and iterable parameter logging
**Vulnerability:** The logger successfully redacted top-level keys like `api_key` and `password`, but failed to traverse nested structures. Deeply nested objects containing secrets would be printed as `[Map]` or `[List]` with their raw contents, exposing sensitive data to stdout.
**Learning:** Redaction must always consider data structures hierarchically rather than assuming a flat parameter mapping.
**Prevention:** Extend validation or redaction utility functions to recursively parse nested Collections (Iterables/Maps) ensuring parity across deeply nested user structures.
