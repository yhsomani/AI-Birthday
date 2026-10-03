## 2024-05-18 - Missing PII Redaction
**Vulnerability:** Email addresses were not redacted from logging output.
**Learning:** PII definitions can sometimes be incomplete in global redaction lists.
**Prevention:** Regularly review sensitive data lists to ensure all PII fields (like email) are covered.

## 2024-10-03 - Information Leakage in Generic Error Handlers
**Vulnerability:** Generic catch blocks were exposing internal exception strings (`e.toString()`) to the UI and user-facing error details.
**Learning:** Catch-all error handlers should log the full exception internally but only return safe, sanitized messages to the user to prevent exposing internal state or implementation details.
**Prevention:** When catching generic exceptions, always use the secure logger with the exception and stack trace, and throw/display a static, generic error message.
