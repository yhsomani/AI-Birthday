import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/reminders/application/android_notification_scheduler_gateway.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_schedule.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('AndroidNotificationSchedulerGateway', () {
    test(
      'non-live Android returns true for permission and completes without error',
      () async {
        const gateway = AndroidNotificationSchedulerGateway();

        expect(await gateway.hasPermission(), isTrue);
        expect(await gateway.requestPermission(), isTrue);

        final plan = ReminderPlan(
          active: [
            ReminderTrigger(
              personId: 'p1',
              personName: 'Alice',
              kind: ReminderKind.birthday,
              at: DateTime.utc(2026, 10, 4, 9),
            ),
          ],
          suppressed: const [],
        );

        await gateway.apply(plan);

        // On non-live Android, apply, cancelAll and sendTestNotification complete safely as no-ops
        await gateway.cancelAll();
        await gateway.sendTestNotification(title: 'Test', body: 'Body');
      },
    );
  });

  group('MethodChannelGeminiNanoPlatform', () {
    test('returns unavailable on non-live Android', () async {
      final platform = MethodChannelGeminiNanoPlatform();

      final state = await platform.currentState();
      expect(state, equals(NanoState.unavailable));

      final dlState = await platform.startDownload();
      expect(dlState, equals(NanoState.unavailable));

      await platform.dispose();
    });
  });
}
