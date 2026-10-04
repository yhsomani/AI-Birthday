/// Abstraction over OS-backed secure, local storage for secrets.
///
/// Used for the user's optional Gemini API credential. Contract:
/// - store only in secure local storage,
/// - never log it,
/// - never sync it,
/// - never send it to the backend by default,
/// - keep in-memory lifetime minimal.
library;

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// A minimal, injectable secret store.
abstract interface class SecretStore {
  /// Stores [value] for [key]. Overwrites any previous value.
  Future<void> write(String key, String value);

  /// Reads the value for [key], or `null` when absent.
  Future<String?> read(String key);

  /// Removes the value for [key].
  Future<void> delete(String key);

  /// Whether [key] currently holds a value.
  Future<bool> contains(String key);
}

/// Concrete [SecretStore] backed by flutter_secure_storage.
///
/// AndroidOptions use encrypted shared preferences on supported devices; the
/// value remains stored by the OS in its credential-backed secure storage.
class SecureStorageSecretStore implements SecretStore {
  SecureStorageSecretStore({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(encryptedSharedPreferences: true),
          );

  final FlutterSecureStorage _storage;

  @override
  Future<void> write(String key, String value) =>
      _storage.write(key: key, value: value);

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);

  @override
  Future<bool> contains(String key) async =>
      await _storage.read(key: key) != null;
}
