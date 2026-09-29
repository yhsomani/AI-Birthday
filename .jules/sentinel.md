## 2025-02-14 - Redact nested structures in logging
**Vulnerability:** Deeply nested sensitive data (e.g., inside lists or maps) was logged in plaintext, exposing secrets, API keys, passwords, and phone numbers in application logs.
**Learning:** The initial implementation only redacted sensitive strings when they were directly associated with sensitive keys at the top level of the `params` map. This is a common oversight where developers assume logging utilities only receive flat objects, missing the fact that complex object graphs can leak data if not recursively traversed.
**Prevention:** Always ensure logging redaction and data sanitization libraries traverse nested objects recursively, and handle different data types (like Maps and Iterables) explicitly.
