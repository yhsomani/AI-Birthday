import 'package:ai_birthday/features/auth/application/google_auth_gateway.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';

/// Test double only; the product ships the abstract gateway, never a fake.
class FakeAuthGateway implements GoogleAuthGateway {
  FakeAuthGateway({this.configured = true, this.outcome, this.storedIdentity});

  bool configured;
  SignInOutcome? outcome;
  GoogleIdentity? storedIdentity;
  int signOutCalls = 0;

  static const identity = GoogleIdentity(
    googleSubject: 'sub-123',
    email: 'ana@example.com',
    displayName: 'Ana',
  );

  @override
  Future<bool> isConfigured() async => configured;

  @override
  Future<SignInOutcome> signIn() async =>
      outcome ?? const SignInSuccess(identity);

  @override
  Future<void> signOut() async {
    signOutCalls++;
  }

  @override
  Future<GoogleIdentity?> getStoredIdentity() async => storedIdentity;
}
