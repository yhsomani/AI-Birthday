## 2025-02-25 - Fix Native Exception Leakage in Gemini Nano Platform
**Vulnerability:** Information leakage. Catching `PlatformException` in the `MethodChannelGeminiNanoPlatform` and exposing its `message` property directly to a user-facing error model (AppFailure.providerError) could leak internal native path configurations, underlying network implementation details, or execution context from the host OS into the UI.
**Learning:** Generic catch blocks or platform exceptions often contain internal stack or state details intended for developers, not users. Exposing them in production builds creates an information leakage surface.
**Prevention:** When catching `PlatformException` (or other generic exceptions), never expose raw `.message` or `.toString()` directly to user-facing state. Log the raw exception securely, and return a sanitized, static error message to the UI.

## 2025-02-25 - Backend Production Dependency Security Vulnerabilities
**Vulnerability:** Outdated NPM packages in backend cloud functions introduced high-severity vulnerabilities (e.g. body-parser DoS, zod memory exhaustion, protobuf.js arbitrary code execution/prototype pollution) blocking CI audits.
**Learning:** Firebase functions dependencies, especially transitive ones (`firebase-admin`, `firebase-functions`, `zod`), require careful version bumping (`npm update`, `npm audit fix --force`, explicitly installing updated versions) to maintain a zero high-severity CVE footprint for production without breaking compatibility constraints (like `firebase-functions` peer dependency on `firebase-admin`).
**Prevention:** Regularly audit (`npm audit`) and explicitly upgrade backend control plane dependencies, ensuring peer-dependency compatibility when relying on `--force` fixes.
