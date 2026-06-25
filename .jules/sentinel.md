## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.
## 2024-06-25 - Insecure Encryption Key Derivation
**Vulnerability:** The application was deriving the encryption key for the database using predictable device identifiers like `ANDROID_ID` and the app signature.
**Learning:** Using predictable attributes to derive encryption keys bypasses proper cryptographic security since the "secret" material is deterministic and can be obtained by a motivated attacker.
**Prevention:** Always use a true cryptographically secure random number generator, like `java.security.SecureRandom()`, to generate unpredictable bytes when deriving or generating cryptographic keys.
