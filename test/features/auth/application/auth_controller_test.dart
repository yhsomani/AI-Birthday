import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/application/google_auth_gateway.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_auth_gateway.dart';

void main() {
  test(
    'unavailable gateway reports unavailable and never invents a session',
    () async {
      final container = ProviderContainer(
        overrides: [
          googleAuthGatewayProvider.overrideWithValue(
            const UnavailableGoogleAuthGateway(),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        await container.read(authControllerProvider.future),
        const AuthState(status: AuthStatus.unavailable),
      );
      expect(
        await container.read(authControllerProvider.notifier).signIn(),
        isA<SignInUnavailable>(),
      );
      expect(
        await container.read(authControllerProvider.future),
        const AuthState(status: AuthStatus.unavailable),
      );
    },
  );

  test('configured gateway signs in to a session and back out', () async {
    final gateway = FakeAuthGateway();
    final container = ProviderContainer(
      overrides: [googleAuthGatewayProvider.overrideWithValue(gateway)],
    );
    addTearDown(container.dispose);

    final notifier = container.read(authControllerProvider.notifier);
    expect(
      await container.read(authControllerProvider.future),
      const AuthState(status: AuthStatus.signedOut),
    );

    final outcome = await notifier.signIn();
    expect(outcome, isA<SignInSuccess>());
    final state = await container.read(authControllerProvider.future);
    expect(state.isSignedIn, isTrue);
    expect(state.identity?.email, 'ana@example.com');
    expect(state.identity?.displayName, 'Ana');

    await notifier.signOut();
    expect(
      await container.read(authControllerProvider.future),
      const AuthState(status: AuthStatus.signedOut),
    );
  });

  test('a failed sign-in keeps the signed-out state', () async {
    final gateway = FakeAuthGateway(outcome: const SignInFailed('cancelled'));
    final container = ProviderContainer(
      overrides: [googleAuthGatewayProvider.overrideWithValue(gateway)],
    );
    addTearDown(container.dispose);

    final outcome = await container
        .read(authControllerProvider.notifier)
        .signIn();
    expect(outcome, isA<SignInFailed>());
    expect(
      await container.read(authControllerProvider.future),
      const AuthState(status: AuthStatus.signedOut),
    );
  });
}
