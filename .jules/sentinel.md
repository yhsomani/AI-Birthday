## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2024-06-16 - Deterministic Database Key Generation
**Vulnerability:** `DatabaseKeyDerivation` was generating the database encryption key deterministically using `ANDROID_ID` and the app's signature hash. This makes the key predictable and susceptible to derivation if the `ANDROID_ID` is known, compromising the database encryption.
**Learning:** Cryptographic keys should never be based on predictable or stable device identifiers. While the device IDs are unique, they are not a source of cryptographically secure entropy.
**Prevention:** Always use `java.security.SecureRandom()` to generate cryptographic keys, nonces, or any other sensitive random values.
