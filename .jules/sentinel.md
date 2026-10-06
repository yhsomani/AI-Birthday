## 2023-10-25 - Domain-Specific PII Redaction

**Vulnerability:** Domain-specific PII fields like 'email' were missing from the logging redaction list, exposing them in console output.
**Learning:** Generic security lists (passwords, tokens) are insufficient for applications handling sensitive user content.
**Prevention:** Always maintain a domain-specific list of sensitive terms for log redaction, but be careful not to include overly generic terms (like 'message' or 'body') that would redact non-sensitive diagnostic info.

## 2024-05-18 - Missing PII Redaction

**Vulnerability:** Email addresses were not redacted from logging output.
**Learning:** PII definitions can sometimes be incomplete in global redaction lists.
**Prevention:** Regularly review sensitive data lists to ensure all PII fields (like email) are covered.

## 2024-10-03 - Information Leakage in Generic Error Handlers

**Vulnerability:** Generic catch blocks were exposing internal exception strings (`e.toString()`) to the UI and user-facing error details.
**Learning:** Catch-all error handlers should log the full exception internally but only return safe, sanitized messages to the user to prevent exposing internal state or implementation details.
**Prevention:** When catching generic exceptions, always use the secure logger with the exception and stack trace, and throw/display a static, generic error message.

## 2025-03-10 - Fix uninitialized memory exposure in opaque.ts

**Vulnerability:** Use of Buffer.allocUnsafe exposes uninitialized memory which could lead to information leakage if the buffer is read before being fully overwritten.
**Learning:** While Buffer.allocUnsafe can be faster, it skips zeroing out memory. Unless there is a strict, proven performance bottleneck requiring it, always default to Buffer.alloc to ensure memory hygiene.
**Prevention:** Use Buffer.alloc() instead of Buffer.allocUnsafe() as a secure default for memory allocation in Node.js applications.
## 2024-10-27 - Raw Exception Leakage to UI States
**Vulnerability:** Raw exceptions (`e.toString()` or string interpolated `$e`) were being caught in providers/gateways and directly passed into error domains (`AppFailure`, `SignInFailed`, `GeminiConnectionResult.error`) which propagate to the user interface.
**Learning:** Returning exception stack traces or raw messages can expose internals (like internal network details, missing files, model path, or underlying library details).
**Prevention:** Always log the full exception (`error: e, stackTrace: st`) internally but return a static, sanitized string to domain models.
