import 'package:ai_birthday/core/security/credential_storage.dart';

/// In-memory SecureStoreDriver test double for E2E requirement testing.
class FakeStoreDriver implements SecureStoreDriver {
  final Map<String, String> _storage = {};

  Map<String, String> get entries => Map.unmodifiable(_storage);

  @override
  Future<void> delete(String key) async {
    _storage.remove(key);
  }

  @override
  Future<String?> read(String key) async {
    return _storage[key];
  }

  @override
  Future<void> write(String key, String value) async {
    _storage[key] = value;
  }

  void clear() {
    _storage.clear();
  }
}
