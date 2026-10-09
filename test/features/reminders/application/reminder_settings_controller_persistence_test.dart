import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/core_providers.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';

import '../../../e2e/harness/test_harness.dart';

void main() {
  group('ReminderSettingsController optimistic persistence (audit 06)', () {
    testWidgets(
      'flips the toggle instantly, then reverts when the write fails',
      (tester) async {
        final harness = E2ETestHarness()..setUp();
        addTearDown(() => harness.db.close());

        final container = ProviderContainer(
          overrides: harness.providerOverrides,
        );
        addTearDown(container.dispose);

        final notifier = container.read(reminderSettingsProvider.notifier);
        await tester.pump();
        await tester.pump();

        expect(container.read(reminderSettingsProvider).enabled, isFalse);

        // Break persistence: a closed DB makes the next insert throw.
        await harness.db.close();
        notifier.setEnabled(true);
        await tester.pump();
        await tester.pump();

        final state = container.read(reminderSettingsProvider);
        expect(
          state.enabled,
          isFalse,
          reason: 'the UI must not keep a toggle the store rejected',
        );
        expect(state.syncError, contains('Could not save'));
      },
    );

    testWidgets('keeps the flipped value when persistence succeeds', (
      tester,
    ) async {
      final harness = E2ETestHarness()..setUp();
      addTearDown(() => harness.db.close());

      final container = ProviderContainer(overrides: harness.providerOverrides);
      addTearDown(container.dispose);

      final notifier = container.read(reminderSettingsProvider.notifier);
      await tester.pump();
      await tester.pump();

      notifier.setEnabled(true);
      await tester.pump();
      await tester.pump();
      await tester.pump();

      final state = container.read(reminderSettingsProvider);
      expect(state.enabled, isTrue);

      final db = container.read(databaseProvider);
      final rows = await (db.select(db.reminderSettingsEntries)).get();
      expect(rows, hasLength(1));
      expect(rows.single.enabled, isTrue);
    });

    testWidgets('an all-off milestone selection survives a restart (F11)', (
      tester,
    ) async {
      final harness = E2ETestHarness()..setUp();
      addTearDown(() => harness.db.close());

      final first = ProviderContainer(overrides: harness.providerOverrides);
      addTearDown(first.dispose);
      final notifier = first.read(reminderSettingsProvider.notifier);
      await tester.pump();
      await tester.pump();

      for (final kind in ReminderKind.values) {
        notifier.setKind(kind, false);
      }
      await tester.pump();
      await tester.pump();
      await tester.pump();

      // Simulated restart: a fresh container reads the same database.
      final second = ProviderContainer(overrides: harness.providerOverrides);
      addTearDown(second.dispose);
      second.read(reminderSettingsProvider);
      await tester.pump();
      await tester.pump();
      await tester.pump();

      expect(second.read(reminderSettingsProvider).kinds, isEmpty);
    });
  });
}
