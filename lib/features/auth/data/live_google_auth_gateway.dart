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
/// - Firebase Auth REST exchange (Identity Toolkit) for token verification & UID binding.
/// - Direct Firebase Email/Password login and registration.
/// - Hardware-backed session persistence via [SecureStoreDriver].
class LiveGoogleAuthGateway implements GoogleAuthGateway {
  LiveGoogleAuthGateway({
    required SecureStoreDriver store,
    http.Client? httpClient,
    AppLogger? logger,
  })  : _store = store,
        _http = httpClient ?? http.Client(),
        _logger = logger;

  final SecureStoreDriver _store;
  final http.Client _http;
  final AppLogger? _logger;
  bool _initialized = false;

  static const String _firebaseApiKey =
      'AIzaSyDUgbmii4EH0PCHVOxO9TXvGeXyFpyxWNQ';

  static const String _serverClientId =
      '339889410493-g5klr4838kfibddoqvk1rbbt39dblffp.apps.googleusercontent.com';

  static const String _keySubject = 'auth_session_subject';
  static const String _keyEmail = 'auth_session_email';
  static const String _keyName = 'auth_session_name';
  static const String _keyPhoto = 'auth_session_photo';
  static const String _keyUid = 'auth_session_uid';
  static const String _keyIdToken = 'auth_session_id_token';

  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    try {
      await GoogleSignIn.instance.initialize(
        serverClientId: _serverClientId,
      );
      _initialized = true;
    } catch (e) {
      _logger?.warning('AuthGateway', 'GoogleSignIn initialize note: $e');
      _initialized = true;
    }
  }

  @override
  Future<bool> isConfigured() async => true;

  @override
  Future<GoogleIdentity?> getStoredIdentity() async {
    try {
      final subject = await _store.read(_keySubject);
      final email = await _store.read(_keyEmail);
      final name = await _store.read(_keyName);
      final photo = await _store.read(_keyPhoto);
      final uid = await _store.read(_keyUid);
      final idToken = await _store.read(_keyIdToken);

      if (subject != null && email != null && name != null) {
        return GoogleIdentity(
          googleSubject: subject,
          email: email,
          displayName: name,
          photoUrl: (photo != null && photo.isNotEmpty) ? photo : null,
          firebaseUid: (uid != null && uid.isNotEmpty) ? uid : subject,
          idToken: (idToken != null && idToken.isNotEmpty) ? idToken : null,
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
      final account = await GoogleSignIn.instance.authenticate();

      final idToken = account.authentication.idToken;
      String? firebaseUid = account.id;
      String? verifiedIdToken = idToken;

      if (idToken != null && idToken.isNotEmpty) {
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

          if (res.statusCode == 200) {
            final data = jsonDecode(res.body) as Map<String, dynamic>;
            firebaseUid = data['localId'] as String? ?? account.id;
            verifiedIdToken = data['idToken'] as String? ?? idToken;
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

      await _saveSession(identity);
      _logger?.info('AuthGateway', 'Sign in successful: ${identity.email}');
      return SignInSuccess(identity);
    } catch (e, st) {
      _logger?.error(
        'AuthGateway',
        'Google sign in error: $e',
        error: e,
        stackTrace: st,
      );
      final msg = e.toString();
      if (msg.contains('canceled') || msg.contains('cancelled')) {
        return const SignInFailed('Sign in was cancelled.');
      }
      return SignInFailed(msg);
    }
  }

  /// Direct Email/Password sign-in via Firebase Auth REST API.
  Future<SignInOutcome> signInWithEmail(String email, String password) async {
    try {
      final res = await _http.post(
        Uri.parse(
          'https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$_firebaseApiKey',
        ),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': email.trim(),
          'password': password,
          'returnSecureToken': true,
        }),
      );

      final data = jsonDecode(res.body) as Map<String, dynamic>;
      if (res.statusCode == 200) {
        final identity = GoogleIdentity(
          googleSubject: data['localId'] as String,
          email: data['email'] as String,
          displayName:
              data['displayName'] as String? ?? email.split('@').first,
          firebaseUid: data['localId'] as String,
          idToken: data['idToken'] as String,
        );

        await _saveSession(identity);
        return SignInSuccess(identity);
      } else {
        final err =
            (data['error'] as Map<String, dynamic>?)?['message'] as String? ??
            'Sign in failed';
        return SignInFailed(_mapFirebaseErrorMessage(err));
      }
    } catch (e) {
      return SignInFailed(e.toString());
    }
  }

  /// Direct Email/Password account registration via Firebase Auth REST API.
  Future<SignInOutcome> signUpWithEmail(String email, String password) async {
    try {
      final res = await _http.post(
        Uri.parse(
          'https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$_firebaseApiKey',
        ),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': email.trim(),
          'password': password,
          'returnSecureToken': true,
        }),
      );

      final data = jsonDecode(res.body) as Map<String, dynamic>;
      if (res.statusCode == 200) {
        final identity = GoogleIdentity(
          googleSubject: data['localId'] as String,
          email: data['email'] as String,
          displayName: email.split('@').first,
          firebaseUid: data['localId'] as String,
          idToken: data['idToken'] as String,
        );

        await _saveSession(identity);
        return SignInSuccess(identity);
      } else {
        final err =
            (data['error'] as Map<String, dynamic>?)?['message'] as String? ??
            'Sign up failed';
        return SignInFailed(_mapFirebaseErrorMessage(err));
      }
    } catch (e) {
      return SignInFailed(e.toString());
    }
  }

  /// Sends password reset email via Firebase Auth.
  Future<bool> sendPasswordReset(String email) async {
    try {
      final res = await _http.post(
        Uri.parse(
          'https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$_firebaseApiKey',
        ),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'requestType': 'PASSWORD_RESET',
          'email': email.trim(),
        }),
      );
      return res.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<void> _saveSession(GoogleIdentity identity) async {
    await _store.write(_keySubject, identity.googleSubject);
    await _store.write(_keyEmail, identity.email);
    await _store.write(_keyName, identity.displayName);
    if (identity.photoUrl != null) {
      await _store.write(_keyPhoto, identity.photoUrl!);
    }
    if (identity.firebaseUid != null) {
      await _store.write(_keyUid, identity.firebaseUid!);
    }
    if (identity.idToken != null) {
      await _store.write(_keyIdToken, identity.idToken!);
    }
  }

  static String _mapFirebaseErrorMessage(String code) {
    if (code.contains('EMAIL_EXISTS')) {
      return 'An account already exists with this email.';
    }
    if (code.contains('INVALID_PASSWORD') ||
        code.contains('INVALID_LOGIN_CREDENTIALS')) {
      return 'Invalid email or password.';
    }
    if (code.contains('USER_DISABLED')) {
      return 'This account has been disabled.';
    }
    if (code.contains('EMAIL_NOT_FOUND')) {
      return 'No account found with this email.';
    }
    if (code.contains('WEAK_PASSWORD')) {
      return 'Password must be at least 6 characters.';
    }
    if (code.contains('PASSWORD_LOGIN_DISABLED') ||
        code.contains('OPERATION_NOT_ALLOWED')) {
      return 'Email/Password sign-in is disabled in Firebase. Please use Google Sign-In.';
    }
    return code;
  }

  @override
  Future<void> signOut() async {
    try {
      await GoogleSignIn.instance.signOut();
    } catch (_) {}
    await _store.delete(_keySubject);
    await _store.delete(_keyEmail);
    await _store.delete(_keyName);
    await _store.delete(_keyPhoto);
    await _store.delete(_keyUid);
    await _store.delete(_keyIdToken);
    _logger?.info('AuthGateway', 'Logged out and cleared secure storage keys');
  }
}
