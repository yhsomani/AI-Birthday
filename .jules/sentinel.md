## 2023-10-25 - Domain-Specific PII Redaction
**Vulnerability:** Domain-specific PII fields like 'email' were missing from the logging redaction list, exposing them in console output.
**Learning:** Generic security lists (passwords, tokens) are insufficient for applications handling sensitive user content.
**Prevention:** Always maintain a domain-specific list of sensitive terms for log redaction, but be careful not to include overly generic terms (like 'message' or 'body') that would redact non-sensitive diagnostic info.
