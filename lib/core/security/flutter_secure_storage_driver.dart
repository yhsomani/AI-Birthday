/// FlutterSecureStorage driver implementation of [SecureStoreDriver].
library;

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';

class FlutterSecureStorageDriver implements SecureStoreDriver {
  const FlutterSecureStorageDriver([
    this._storage = const FlutterSecureStorage(),
  ]);

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) async {
    try {
      return await _storage.read(key: key);
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> write(String key, String value) async {
    try {
      await _storage.write(key: key, value: value);
    } catch (_) {}
  }

  @override
  Future<void> delete(String key) async {
    try {
      await _storage.delete(key: key);
    } catch (_) {}
  }
}
