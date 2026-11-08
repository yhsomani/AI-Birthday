## 2025-03-05 - Prevent PlatformException details from leaking to UI
**Vulnerability:** The native `PlatformException.message` was being directly passed into the `AppFailure.providerError` detail string, which could leak internal native path or system exception strings directly to the UI layer.
**Learning:** Exception details caught on the platform boundary must not be propagated directly to the UI models without sanitization, as they bypass typical view-layer filtering.
**Prevention:** Catch the native exception, log it internally via a secure logger if necessary, and throw a standardized domain error with a generic, sanitized string message for the user.
