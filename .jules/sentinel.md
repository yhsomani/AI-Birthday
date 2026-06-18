## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2024-06-18 - Deterministic Key Derivation
**Vulnerability:** `DatabaseKeyDerivation.kt` was using predictable device identifiers (`ANDROID_ID`) and app signatures to derive a deterministic database encryption key.
**Learning:** Using deterministic data like `ANDROID_ID` for cryptographic keys makes them susceptible to offline attacks if the algorithm is known, as these values are static and easily guessable or obtainable.
**Prevention:** Always use a cryptographically secure random number generator (e.g., `java.security.SecureRandom()`) to generate initial database keys or secrets, and rely on secure keystore-backed mechanisms to persist them.
