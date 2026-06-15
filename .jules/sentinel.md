## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2025-06-15 - [Database Encryption Key Determinism]
**Vulnerability:** The SQLite database encryption key was being generated deterministically from predictable system values (`ANDROID_ID` and app signature) rather than using a secure random value.
**Learning:** Hardcoding or deterministically generating cryptographic keys from public or derivable device attributes severely undermines encryption. If an attacker can determine the inputs, they can independently derive the key and compromise the data.
**Prevention:** Always use `java.security.SecureRandom()` to generate true cryptographic keys and rely on secure keystore-backed mechanisms (like `EncryptedSharedPreferences`) to store them, rather than re-deriving them from predictable state.
