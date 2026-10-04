/// FlutterSecureStorage driver implementation of [SecureStoreDriver].
library;

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';

class FlutterSecureStorageDriver implements SecureStoreDriver {
  const FlutterSecureStorageDriver([
    this._storage = const FlutterSecureStorage(),
    this._logger,
  ]);

  final FlutterSecureStorage _storage;
  final AppLogger? _logger;

  @override
  Future<String?> read(String key) async {
    try {
      return await _storage.read(key: key);
    } catch (e, st) {
      _logger?.error(
        'SecureStore',
        'Failed to read key: $key',
        error: e,
        stackTrace: st,
      );
      return null;
    }
  }

  @override
  Future<void> write(String key, String value) async {
    try {
      await _storage.write(key: key, value: value);
    } catch (e, st) {
      _logger?.error(
        'SecureStore',
        'Failed to write secure key: $key',
        error: e,
        stackTrace: st,
      );
      rethrow;
    }
  }

  @override
  Future<void> delete(String key) async {
    try {
      await _storage.delete(key: key);
    } catch (e, st) {
      _logger?.error(
        'SecureStore',
        'Failed to delete secure key: $key',
        error: e,
        stackTrace: st,
      );
      rethrow;
    }
  }
}
