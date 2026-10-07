import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/reminders/application/notification_scheduler_gateway.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_schedule.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import '../../../e2e/harness/fake_store_driver.dart';
import '../../../e2e/harness/test_harness.dart';

class _FakeGateway implements NotificationSchedulerGateway {
  bool permission = true;
  int requestCount = 0;

  @override
  Future<bool> hasPermission() async => permission;

  @override
  Future<bool> requestPermission() async {
    requestCount++;
    return permission;
  }

  @override
  Future<bool> apply(ReminderPlan plan) async => true;

  @override
  Future<bool> cancelAll() async => true;

  @override
  Future<bool> hasExactAlarmPermission() async => true;

  @override
  Future<bool> requestExactAlarmPermission() async => true;

  @override
  Future<bool> sendTestNotification({
    required String title,
    required String body,
  }) async => true;

  @override
  Future<String?> getInitialNotificationPersonId() async => null;

  @override
  void setNotificationOpenedHandler(void Function(String personId) onOpened) {}
}

void main() {
  group('Settings revoked-notification visibility (audit scenario T)', () {
    late E2ETestHarness harness;
    late _FakeGateway gateway;

    setUp(() {
      harness = E2ETestHarness()..setUp();
      gateway = _FakeGateway()..permission = true;
    });

    tearDown(() => harness.tearDown());

    // A fresh notifier per pump: Riverpod disposes the notifier when its tree
    // unmounts, so tests that "relaunch" the app must not reuse one notifier.
    SubscriptionNotifier freshNotifier() => SubscriptionNotifier(
      initial: UserEntitlement.free,
      store: FakeStoreDriver(),
      inAppPurchase: FakeUnavailableIap(),
      logger: const NoopLogger(),
    );

    Future<void> pumpSettings(WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            // Last override wins: keep the DB/repos, replace the notifier.
            subscriptionNotifierProvider.overrideWith((ref) => freshNotifier()),
            notificationSchedulerGatewayProvider.overrideWithValue(gateway),
          ],
          child: const MaterialApp(home: SettingsScreen()),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets(
      'permission revoked after enable shows recovery and re-enable clears it',
      (tester) async {
        // 1. Enable reminders while permission is granted → no warning.
        await pumpSettings(tester);
        final switchFinder = find.widgetWithText(
          SwitchListTile,
          'Birthday reminders',
        );
        await tester.ensureVisible(switchFinder);
        await tester.pumpAndSettle();
        await tester.tap(switchFinder);
        await tester.pumpAndSettle();

        expect(find.textContaining('Notifications are blocked'), findsNothing);
        final enabled = ProviderScope.containerOf(
          tester.element(find.byType(SettingsScreen)),
        ).read(reminderSettingsProvider).enabled;
        expect(enabled, isTrue);

        // 2. OS revokes permission; the app relaunches and detects the block.
        gateway.permission = false;
        await tester.pumpWidget(const SizedBox.shrink());
        await pumpSettings(tester);

        expect(
          find.textContaining('Notifications are blocked'),
          findsOneWidget,
        );
        expect(find.text('Re-enable notifications'), findsOneWidget);

        // 3. Re-enable restores access; the warning disappears.
        gateway.permission = true;
        final reEnable = find.text('Re-enable notifications');
        await tester.ensureVisible(reEnable);
        await tester.pumpAndSettle();
        await tester.tap(reEnable);
        await tester.pumpAndSettle();

        expect(gateway.requestCount, greaterThan(0));
        expect(find.textContaining('Notifications are blocked'), findsNothing);
      },
    );

    testWidgets('reminders off shows no revoked-permission warning', (
      tester,
    ) async {
      gateway.permission = false;
      await pumpSettings(tester);

      expect(find.text('Re-enable notifications'), findsNothing);
      expect(find.textContaining('Notifications are blocked'), findsNothing);
    });
  });
}
