import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../auth/support/fake_auth_gateway.dart';

void main() {
  Future<void> pumpSettings(
    WidgetTester tester, {
    List<Override> overrides = const [],
    Size size = const Size(800, 1600),
    double textScale = 1.0,
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1.0;
    tester.platformDispatcher.textScaleFactorTestValue = textScale;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: overrides,
        child: const MaterialApp(home: SettingsScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('account tile reports unavailability truthfully on host builds', (
    tester,
  ) async {
    await pumpSettings(
      tester,
      overrides: [
        googleAuthGatewayProvider.overrideWithValue(
          FakeAuthGateway(configured: false),
        ),
      ],
    );

    expect(find.text('Sign in with Google'), findsOneWidget);
    expect(find.text('Not configured for this build'), findsOneWidget);
    expect(find.text('Signed in'), findsNothing);
  });

  testWidgets('sign in and sign out round-trip through the gateway', (
    tester,
  ) async {
    final gateway = FakeAuthGateway();
    await pumpSettings(
      tester,
      overrides: [googleAuthGatewayProvider.overrideWithValue(gateway)],
    );

    expect(find.text('Sign in with Google'), findsOneWidget);

    // Row and button both open the sheet; the sheet owns the sign-in action.
    await tester.tap(find.text('Sign in with Google'));
    await tester.pumpAndSettle();
    expect(find.text('Continue with Google'), findsOneWidget);

    await tester.tap(find.text('Continue with Google'));
    await tester.pumpAndSettle();

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('ana@example.com'), findsOneWidget);
    expect(find.text('Sign out'), findsOneWidget);

    // Sign out now asks for confirmation first.
    await tester.tap(find.text('Sign out'));
    await tester.pumpAndSettle();
    expect(find.text('Sign out?'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Sign out'));
    await tester.pumpAndSettle();

    expect(gateway.signOutCalls, 1);
    expect(find.text('Sign in with Google'), findsOneWidget);
  });

  testWidgets('canceling the sign-out dialog keeps the session', (
    tester,
  ) async {
    final gateway = FakeAuthGateway();
    await pumpSettings(
      tester,
      overrides: [googleAuthGatewayProvider.overrideWithValue(gateway)],
    );

    await tester.tap(find.text('Sign in with Google'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Continue with Google'));
    await tester.pumpAndSettle();
    expect(find.text('Sign out'), findsOneWidget);

    await tester.tap(find.text('Sign out'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(gateway.signOutCalls, 0); // no sign-out ever reached the gateway
    expect(find.text('Sign out'), findsOneWidget); // still signed in
    expect(find.text('ana@example.com'), findsOneWidget);
  });

  testWidgets('a failed sign-in keeps the signed-out tile', (tester) async {
    final gateway = FakeAuthGateway(outcome: const SignInFailed('cancelled'));
    await pumpSettings(
      tester,
      overrides: [googleAuthGatewayProvider.overrideWithValue(gateway)],
    );

    await tester.tap(find.text('Sign in with Google'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Continue with Google'));
    await tester.pumpAndSettle();

    expect(find.text('Sign in with Google'), findsOneWidget);
    expect(find.text('cancelled'), findsOneWidget); // error stays in the sheet
    expect(find.text('Ana'), findsNothing);
  });

  testWidgets('sign-in sheet never overflows at 200% on a 360dp screen', (
    tester,
  ) async {
    // audit 05 P2-3: the Google button used a fixed 52px height and a
    // non-flexible label row; the sheet itself was unscrollable. At 200% on a
    // small phone the sheet must scroll and the action stay reachable.
    final gateway = FakeAuthGateway();
    await pumpSettings(
      tester,
      overrides: [googleAuthGatewayProvider.overrideWithValue(gateway)],
      size: const Size(360, 640),
      textScale: 2.0,
    );

    await tester.tap(find.text('Sign in with Google'));
    await tester.pumpAndSettle();

    expect(find.text('Continue with Google'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
