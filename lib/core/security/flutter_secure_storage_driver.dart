/// FlutterSecureStorage driver implementation of [SecureStoreDriver].
library;

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';

class FlutterSecureStorageDriver implements SecureStoreDriver {
  const FlutterSecureStorageDriver([this._storage = const FlutterSecureStorage()]);

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) => _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}
