## 2024-06-09 - Insecure Fallback to Plaintext SharedPreferences
**Vulnerability:** `SecurePrefs` and `DatabaseKeyDerivation` classes were failing silently by reverting to `Context.MODE_PRIVATE` (plaintext SharedPreferences) when `EncryptedSharedPreferences` failed to initialize.
**Learning:** This is a silent security degradation pattern that undermines the intent of encrypted storage by letting the application run in an insecure state. Instead of falling back to plaintext, applications must "fail securely" by throwing a `SecurityException` when encrypted storage cannot be set up.
**Prevention:** Avoid `Context.MODE_PRIVATE` fallbacks within encrypted preference classes. Always throw exceptions when secure storage primitives fail to initialize.

## 2024-06-09 - Deterministic Database Key Generation
**Vulnerability:** The application used deterministic device values (`ANDROID_ID` and App Signature) mixed as a seed for `PBEKeySpec` to derive the database encryption key instead of secure randomness.
**Learning:** This approach enables offline attackers to predictably derive the encryption key by extracting the app package and capturing or guessing device attributes. Predictable cryptography negates the value of database encryption. Because the key is cached in `EncryptedSharedPreferences` securely, a randomized key won't break existing encrypted database schemas on restart.
**Prevention:** Always use `java.security.SecureRandom()` to generate symmetric encryption keys or salts. Never rely on deterministic device properties (like IDs, IMEIs, or MAC addresses) for cryptographic seeding.
