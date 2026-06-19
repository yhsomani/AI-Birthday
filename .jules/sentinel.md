## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2024-06-09 - Predictable Cryptographic Key Generation
**Vulnerability:** The application was using deterministic and predictable device characteristics (`Settings.Secure.ANDROID_ID` and app signature hashes) as key material for deriving the primary encryption key for the local database (`DatabaseKeyDerivation.kt`). This could allow an attacker with knowledge of the device ID to reproduce the key.
**Learning:** Hardcoded or predictable data streams are fundamentally insecure to use as entropy for cryptographic primitives or nonces, especially when securing databases holding sensitive PII.
**Prevention:** Always use `java.security.SecureRandom()` to generate truly random, unpredictable byte arrays when creating cryptographic keys, salts, or initialization vectors (IVs) from scratch.
