## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2025-02-14 - Fix Insecure Database Encryption Key Derivation
**Vulnerability:** The local Room database encryption key was being generated deterministically using predictable or static values (`ANDROID_ID` and the app signature hash).
**Learning:** This is insecure because an attacker who obtains the `ANDROID_ID` (which can be queried or leaked) and the app signature (which is public) could recreate the encryption key and access sensitive data stored in the local database.
**Prevention:** Always use a strong, true random source like `java.security.SecureRandom()` to generate cryptographic keys for encrypting local databases, and store the derived key securely (e.g., using `EncryptedSharedPreferences`).
