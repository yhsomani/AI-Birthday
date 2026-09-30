## 2023-10-27 - [Logging Redaction Logic Fails for Nested Objects]
**Vulnerability:** The `_redactValue` method in `AppLogger` only checks the top-level keys for redaction. If a sensitive value is passed inside a nested Map or Iterable, it is not redacted and gets printed to the logs.
**Learning:** Redaction logic must be recursive. When logging structured data, we cannot assume that the sensitive data is only at the top level of the parameters object.
**Prevention:** Implement recursive redaction logic to ensure nested objects are thoroughly scanned. When dealing with maps, properly handle non-string keys to avoid type errors.
