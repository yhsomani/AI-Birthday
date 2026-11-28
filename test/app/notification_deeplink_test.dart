import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/app_scaffold.dart';
import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/onboarding/presentation/onboarding_screen.dart';
import 'package:ai_birthday/features/reminders/application/notification_scheduler_gateway.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_schedule.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';

import '../e2e/harness/test_harness.dart';

class _FakeNotificationGateway extends NotificationSchedulerGateway
    implements ClearableNotificationSchedulerGateway {
  _FakeNotificationGateway({this.initialPersonId});

  final String? initialPersonId;
  void Function(String personId)? openedHandler;
  int clearHandlerCalls = 0;

  @override
  Future<bool> hasPermission() async => true;

  @override
  Future<bool> requestPermission() async => true;

  @override
  Future<bool> apply(ReminderPlan plan, {bool hideNames = false}) async => true;

  @override
  Future<bool> cancelAll() async => true;

  @override
  Future<String?> getInitialNotificationPersonId() async => initialPersonId;

  @override
  void setNotificationOpenedHandler(void Function(String personId) onOpened) {
    openedHandler = onOpened;
  }

  @override
  void clearNotificationOpenedHandler() {
    clearHandlerCalls++;
    openedHandler = null;
  }
}

void main() {
  late E2ETestHarness harness;

  setUp(() {
    harness = E2ETestHarness()..setUp();
    addTearDown(() async => harness.tearDown());
  });

  testWidgets('cold-start notification opens MessageStudioScreen directly', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await harness.credentialStorage.setCompletedOnboarding(true);
    final gateway = _FakeNotificationGateway(initialPersonId: 'p-cold');

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ...harness.providerOverrides,
          notificationSchedulerGatewayProvider.overrideWithValue(gateway),
        ],
        child: const AiBirthdayApp(),
      ),
    );
    await tester.pumpAndSettle();

    final studio = tester.widget<MessageStudioScreen>(
      find.byType(MessageStudioScreen),
    );
    expect(studio.personId, 'p-cold');

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 10));
    expect(gateway.clearHandlerCalls, 1);
    expect(gateway.openedHandler, isNull);
  });

  testWidgets(
    'cold-start notification during onboarding queues until onboarding completes',
    (tester) async {
      tester.view.physicalSize = const Size(800, 1400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final gateway = _FakeNotificationGateway(initialPersonId: 'p-queued');

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            notificationSchedulerGatewayProvider.overrideWithValue(gateway),
          ],
          child: const AiBirthdayApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(OnboardingScreen), findsOneWidget);
      expect(find.byType(MessageStudioScreen), findsNothing);

      await tester.tap(find.text('Skip'));
      await tester.pumpAndSettle();

      final studio = tester.widget<MessageStudioScreen>(
        find.byType(MessageStudioScreen),
      );
      expect(studio.personId, 'p-queued');

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 10));
    },
  );

  testWidgets('live notification tap routes to studio even when on /settings', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await harness.credentialStorage.setCompletedOnboarding(true);
    final gateway = _FakeNotificationGateway();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ...harness.providerOverrides,
          notificationSchedulerGatewayProvider.overrideWithValue(gateway),
        ],
        child: const AiBirthdayApp(),
      ),
    );
    await tester.pumpAndSettle();

    final router = GoRouter.of(tester.element(find.byType(AppScaffold)));
    router.push(AppRoutes.settings);
    await tester.pumpAndSettle();
    expect(find.byType(SettingsScreen), findsOneWidget);

    gateway.openedHandler!.call('p-live');
    await tester.pumpAndSettle();

    final studio = tester.widget<MessageStudioScreen>(
      find.byType(MessageStudioScreen),
    );
    expect(studio.personId, 'p-live');

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 10));
    expect(gateway.clearHandlerCalls, 1);
  });
}
