## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2026-06-21 - Deterministic Database Key Generation Vulnerability
**Vulnerability:** `DatabaseKeyDerivation` was generating database encryption keys deterministically using `Settings.Secure.ANDROID_ID` and the app's signature hash instead of a cryptographically secure random number generator.
**Learning:** Generating cryptographic keys from predictable device identifiers compromises encryption, as an attacker with knowledge of the device ID and the public app signature can perfectly recreate the encryption key offline.
**Prevention:** Always use `java.security.SecureRandom()` to generate unpredictable, cryptographically strong key material when initializing new encryption keys.
