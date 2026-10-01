## 2024-05-24 - Protect Message Content and Notes in Logs
**Vulnerability:** Application logging allowed potentially sensitive content such as message bodies, private notes, and emails to be logged if passed as parameters.
**Learning:** It's easy to overlook domain-specific sensitive data (like "message" or "note") when setting up standard redaction lists which typically only focus on auth secrets and tokens.
**Prevention:** Ensure the logging redaction filter explicitly includes domain-specific sensitive fields defined in the application's security specifications (e.g., SSOT).
