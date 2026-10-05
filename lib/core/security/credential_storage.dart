/// Secure storage for user credentials (SSOT §5, §20).
///
/// Security rules:
/// - Secrets are never logged.
/// - Secrets are never synchronized to Firestore or remote backends.
/// - The user's Gemini API key is stored securely locally and can be tested,
///   replaced, or deleted at any time.
library;

/// Key-value storage abstraction for secure storage drivers.
abstract interface class SecureStoreDriver {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// Contract for credential storage.
abstract interface class CredentialStorage {
  Future<String?> getGeminiApiKey();
  Future<void> saveGeminiApiKey(String apiKey);
  Future<void> deleteGeminiApiKey();
  Future<bool> hasGeminiApiKey();
  Future<bool> hasCompletedOnboarding();
  Future<void> setCompletedOnboarding(bool completed);
}

/// Implementation using a [SecureStoreDriver].
class SecureCredentialStorage implements CredentialStorage {
  const SecureCredentialStorage(this._driver);

  final SecureStoreDriver _driver;

  static const String _geminiApiKeyKey = 'ai_birthday_user_gemini_api_key';
  static const String _onboardingKey = 'ai_birthday_onboarding_completed';

  @override
  Future<String?> getGeminiApiKey() async {
    final key = await _driver.read(_geminiApiKeyKey);
    return (key != null && key.trim().isNotEmpty) ? key.trim() : null;
  }

  @override
  Future<void> saveGeminiApiKey(String apiKey) async {
    final trimmed = apiKey.trim();
    if (trimmed.isEmpty) {
      await deleteGeminiApiKey();
    } else {
      await _driver.write(_geminiApiKeyKey, trimmed);
    }
  }

  @override
  Future<void> deleteGeminiApiKey() async {
    await _driver.delete(_geminiApiKeyKey);
  }

  @override
  Future<bool> hasGeminiApiKey() async {
    final key = await getGeminiApiKey();
    return key != null && key.isNotEmpty;
  }

  @override
  Future<bool> hasCompletedOnboarding() async {
    final flag = await _driver.read(_onboardingKey);
    return flag == 'true';
  }

  @override
  Future<void> setCompletedOnboarding(bool completed) async {
    if (completed) {
      await _driver.write(_onboardingKey, 'true');
    } else {
      await _driver.delete(_onboardingKey);
    }
  }
}

/// In-memory storage for unit and widget testing.
class InMemoryCredentialStorage implements CredentialStorage {
  InMemoryCredentialStorage({
    String? initialKey,
    bool initialOnboardingCompleted = false,
  }) : _key = initialKey,
       _onboardingCompleted = initialOnboardingCompleted;

  String? _key;
  bool _onboardingCompleted;

  @override
  Future<String?> getGeminiApiKey() async => _key;

  @override
  Future<void> saveGeminiApiKey(String apiKey) async {
    _key = apiKey.trim().isEmpty ? null : apiKey.trim();
  }

  @override
  Future<void> deleteGeminiApiKey() async {
    _key = null;
  }

  @override
  Future<bool> hasGeminiApiKey() async => _key != null && _key!.isNotEmpty;

  @override
  Future<bool> hasCompletedOnboarding() async => _onboardingCompleted;

  @override
  Future<void> setCompletedOnboarding(bool completed) async {
    _onboardingCompleted = completed;
  }
}
