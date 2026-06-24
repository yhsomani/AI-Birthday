## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2026-06-24 - Predictable Key Material in Database Encryption
**Vulnerability:** `DatabaseKeyDerivation` was generating database encryption keys using predictable identifiers (`ANDROID_ID` and the app's signing certificate hash).
**Learning:** Using device-specific identifiers as the base for cryptographic keys is insecure because these values are often static, predictable, or readable by other applications on the device. An attacker who knows the device ID and the app's signature hash could pre-compute or derive the key.
**Prevention:** Always use a Cryptographically Secure Pseudorandom Number Generator (CSPRNG), such as `java.security.SecureRandom()` in Java/Kotlin, to generate encryption keys, IVs, or salt values.
