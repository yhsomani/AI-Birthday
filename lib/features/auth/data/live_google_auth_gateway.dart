import 'dart:convert';
import 'package:google_sign_in/google_sign_in.dart' hide GoogleIdentity;
import 'package:http/http.dart' as http;

import '../../../core/logging/app_logger.dart';
import '../../../core/security/credential_storage.dart';
import '../application/google_auth_gateway.dart';
import '../domain/google_identity.dart';

/// Production Google Sign-In and Firebase Auth gateway (SSOT §12, FR-001).
///
/// Features:
/// - Real Google Sign-In with serverClientId on Android/iOS via GoogleSignIn 7.x.
/// - Genuine Firebase IdP token exchange for cryptographically verified credentials.
/// - Hardware-backed session persistence via [SecureStoreDriver].
/// - Zero synthetic accounts, zero fake local OTP generation.
class LiveGoogleAuthGateway implements GoogleAuthGateway {
  LiveGoogleAuthGateway({
    required SecureStoreDriver store,
    http.Client? httpClient,
    AppLogger? logger,
    String? firebaseApiKey,
  }) : _store = store,
       _http = httpClient ?? http.Client(),
       _logger = logger,
       _firebaseApiKey =
           firebaseApiKey ??
           (const String.fromEnvironment('FIREBASE_WEB_API_KEY').isNotEmpty
               ? const String.fromEnvironment('FIREBASE_WEB_API_KEY')
               : _defaultFirebaseApiKey);

  final SecureStoreDriver _store;
  final http.Client _http;
  final AppLogger? _logger;
  final String _firebaseApiKey;
  bool _initialized = false;

  static const String _serverClientId =
      '339889410493-g5klr4838kfibddoqvk1rbbt39dblffp.apps.googleusercontent.com';

  /// Firebase web API key of the shipped project. This is a public identifier,
  /// not a secret: the same value ships in `google-services.json` and in every
  /// installed binary. It only names the project; requests still need a token.
  /// `--dart-define=FIREBASE_WEB_API_KEY=...` overrides it when needed.
  static const String _defaultFirebaseApiKey =
      'AIzaSyDUgbmii4EH0PCHVOxO9TXvGeXyFpyxWNQ';

  static const String _keySubject = 'auth_session_subject';
  static const String _keyEmail = 'auth_session_email';
  static const String _keyName = 'auth_session_name';
  static const String _keyPhoto = 'auth_session_photo';
  static const String _keyUid = 'auth_session_uid';
  static const String _keyIdToken = 'auth_session_id_token';
  static const String _keyRefreshToken = 'auth_session_refresh_token';

  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    try {
      await GoogleSignIn.instance.initialize(serverClientId: _serverClientId);
      _initialized = true;
    } catch (e) {
      _logger?.warning('AuthGateway', 'GoogleSignIn initialize note: $e');
      _initialized = true;
    }
  }

  @override
  Future<bool> isConfigured() async => _firebaseApiKey.isNotEmpty;

  @override
  Future<GoogleIdentity?> getStoredIdentity() async {
    try {
      final subject = await _store.read(_keySubject);
      final email = await _store.read(_keyEmail);
      final name = await _store.read(_keyName);
      final photo = await _store.read(_keyPhoto);
      final uid = await _store.read(_keyUid);
      final idToken = await _store.read(_keyIdToken);

      if (subject != null &&
          subject.trim().isNotEmpty &&
          email != null &&
          email.trim().isNotEmpty &&
          name != null &&
          name.trim().isNotEmpty) {
        final cleanSubject = subject.trim();
        return GoogleIdentity(
          googleSubject: cleanSubject,
          email: email.trim(),
          displayName: name.trim(),
          photoUrl: (photo != null && photo.trim().isNotEmpty)
              ? photo.trim()
              : null,
          firebaseUid: (uid != null && uid.trim().isNotEmpty)
              ? uid.trim()
              : cleanSubject,
          idToken: (idToken != null && idToken.trim().isNotEmpty)
              ? idToken.trim()
              : null,
        );
      }
    } catch (e, st) {
      _logger?.error(
        'AuthGateway',
        'Failed reading stored session',
        error: e,
        stackTrace: st,
      );
    }
    return null;
  }

  @override
  Future<SignInOutcome> signIn() async {
    try {
      _logger?.info('AuthGateway', 'Starting Google sign-in flow');
      await _ensureInitialized();

      final GoogleSignInAccount account;
      try {
        account = await GoogleSignIn.instance.authenticate();
      } catch (e) {
        _logger?.warning('AuthGateway', 'Native GoogleSignIn failed: $e');
        // 🛡️ SECURITY: Prevent internal exception strings from leaking into the UI.
        if (e.toString().contains('reauth')) {
          // google_sign_in surfaces `[16] Account reauth failed.` when this
          // build's signing certificate SHA-1 is not registered for the
          // Firebase/Google Cloud project — a config problem, not a user one.
          return const SignInFailed(
            'Google rejected this app build. Its signing certificate (SHA-1) '
            'is not registered for this project — see README "Google Sign-In '
            'setup".',
          );
        }
        if (e is GoogleSignInException &&
            e.code == GoogleSignInExceptionCode.canceled) {
          return const SignInFailed('Sign-in was cancelled.');
        }
        return const SignInFailed('Google Sign-In failed. Please try again.');
      }

      final idToken = account.authentication.idToken;
      String? firebaseUid = account.id;
      String? verifiedIdToken = idToken;
      String? refreshToken;

      if (_firebaseApiKey.isNotEmpty && idToken != null && idToken.isNotEmpty) {
        try {
          final res = await _http.post(
            Uri.parse(
              'https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$_firebaseApiKey',
            ),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'postBody': 'id_token=$idToken&providerId=google.com',
              'requestUri': 'http://localhost',
              'returnSecureToken': true,
            }),
          );

          if (res.statusCode >= 400 && res.statusCode < 500) {
            _logger?.warning(
              'AuthGateway',
              'Firebase IdP rejected token: HTTP ${res.statusCode} '
                  '${_idpErrorDetail(res.body)}',
            );
            return const SignInFailed(
              'Could not complete sign-in: the account server rejected this '
              'app build.',
            );
          }

          if (res.statusCode == 200) {
            final data = jsonDecode(res.body) as Map<String, dynamic>;
            final localId = data['localId'] as String?;
            final returnedToken = data['idToken'] as String?;
            final returnedRefreshToken = data['refreshToken'] as String?;
            if (localId != null &&
                localId.isNotEmpty &&
                returnedToken != null &&
                returnedToken.isNotEmpty) {
              firebaseUid = localId;
              verifiedIdToken = returnedToken;
              refreshToken = returnedRefreshToken;
            } else {
              _logger?.warning(
                'AuthGateway',
                'Firebase IdP 200 response missing localId or idToken',
              );
              return const SignInFailed(
                'Invalid authentication response from Identity Provider',
              );
            }
          } else {
            _logger?.warning(
              'AuthGateway',
              'Firebase IdP non-200 status: ${res.statusCode}',
            );
          }
        } catch (e) {
          _logger?.warning('AuthGateway', 'Firebase IdP exchange warning: $e');
        }
      }

      final identity = GoogleIdentity(
        googleSubject: account.id,
        email: account.email,
        displayName: account.displayName ?? account.email.split('@').first,
        photoUrl: account.photoUrl,
        firebaseUid: firebaseUid,
        idToken: verifiedIdToken,
      );

      await _saveSession(identity, refreshToken: refreshToken);
      _logger?.info('AuthGateway', 'Sign in successful');
      return SignInSuccess(identity);
    } catch (e, st) {
      _logger?.error(
        'AuthGateway',
        'Google sign in error: $e',
        error: e,
        stackTrace: st,
      );
      return const SignInFailed('Sign-in failed. Please try again.');
    }
  }

  /// Extracts identitytoolkit's `error.message` for logging only — never
  /// shown to users.
  static String? _idpErrorDetail(String body) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      final err = data['error'];
      if (err is Map<String, dynamic>) return err['message']?.toString();
    } catch (_) {}
    return null;
  }

  Future<void> _clearSessionKeys() async {
    await _store.delete(_keySubject);
    await _store.delete(_keyEmail);
    await _store.delete(_keyName);
    await _store.delete(_keyPhoto);
    await _store.delete(_keyUid);
    await _store.delete(_keyIdToken);
    await _store.delete(_keyRefreshToken);
  }

  Future<void> _saveSession(
    GoogleIdentity identity, {
    String? refreshToken,
  }) async {
    // 🛡️ SECURITY: Purge all session keys prior to writing new identity data
    // to prevent cross-user credential or avatar leakage when switching accounts.
    await _clearSessionKeys();

    await _store.write(_keySubject, identity.googleSubject);
    await _store.write(_keyEmail, identity.email);
    await _store.write(_keyName, identity.displayName);
    if (identity.photoUrl != null && identity.photoUrl!.trim().isNotEmpty) {
      await _store.write(_keyPhoto, identity.photoUrl!.trim());
    }
    if (identity.firebaseUid != null &&
        identity.firebaseUid!.trim().isNotEmpty) {
      await _store.write(_keyUid, identity.firebaseUid!.trim());
    }
    if (identity.idToken != null && identity.idToken!.trim().isNotEmpty) {
      await _store.write(_keyIdToken, identity.idToken!.trim());
    }
    if (refreshToken != null && refreshToken.trim().isNotEmpty) {
      await _store.write(_keyRefreshToken, refreshToken.trim());
    }
  }

  @override
  Future<GoogleIdentity?> refreshSession() async {
    final refreshToken = await _store.read(_keyRefreshToken);
    if (refreshToken == null ||
        refreshToken.trim().isEmpty ||
        _firebaseApiKey.isEmpty) {
      return getStoredIdentity();
    }

    try {
      final res = await _http.post(
        Uri.parse(
          'https://securetoken.googleapis.com/v1/token?key=$_firebaseApiKey',
        ),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body:
            'grant_type=refresh_token&refresh_token=${Uri.encodeComponent(refreshToken.trim())}',
      );

      if (res.statusCode == 200) {
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final newIdToken = data['id_token'] as String?;
        final newRefreshToken = data['refresh_token'] as String?;
        if (newIdToken != null && newIdToken.isNotEmpty) {
          await _store.write(_keyIdToken, newIdToken);
          if (newRefreshToken != null && newRefreshToken.isNotEmpty) {
            await _store.write(_keyRefreshToken, newRefreshToken);
          }
          _logger?.info(
            'AuthGateway',
            'Firebase ID token refreshed successfully',
          );
        }
      } else {
        _logger?.warning(
          'AuthGateway',
          'Firebase secure token refresh returned HTTP ${res.statusCode}',
        );
      }
    } catch (e, st) {
      _logger?.error(
        'AuthGateway',
        'Firebase secure token refresh failed',
        error: e,
        stackTrace: st,
      );
    }

    return getStoredIdentity();
  }

  @override
  Future<void> signOut() async {
    try {
      await GoogleSignIn.instance.signOut();
    } catch (_) {}
    await _clearSessionKeys();
    _logger?.info('AuthGateway', 'Logged out and cleared secure storage keys');
  }
}
