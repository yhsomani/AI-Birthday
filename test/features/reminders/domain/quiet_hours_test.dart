import 'package:ai_birthday/features/reminders/domain/quiet_hours.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('QuietHours', () {
    test('none never contains any time', () {
      const quiet = QuietHours.none();
      expect(quiet.isEmpty, isTrue);
      expect(quiet.contains(DateTime(2026, 3, 14, 0)), isFalse);
      expect(quiet.contains(DateTime(2026, 3, 14, 12)), isFalse);
      expect(quiet.contains(DateTime(2026, 3, 14, 23)), isFalse);
    });

    test(
      'contains within a same-day window inclusive start, exclusive end',
      () {
        const quiet = QuietHours(
          start: Duration(hours: 8),
          end: Duration(hours: 10),
        );
        expect(quiet.contains(DateTime(2026, 3, 14, 7, 59)), isFalse);
        expect(quiet.contains(DateTime(2026, 3, 14, 8, 0)), isTrue);
        expect(quiet.contains(DateTime(2026, 3, 14, 9, 0)), isTrue);
        expect(quiet.contains(DateTime(2026, 3, 14, 10, 0)), isFalse);
      },
    );

    test('night window wraps past midnight', () {
      const quiet = QuietHours.night();
      expect(quiet.start, const Duration(hours: 22));
      expect(quiet.end, const Duration(hours: 8));
      expect(quiet.contains(DateTime(2026, 3, 14, 23, 0)), isTrue);
      expect(quiet.contains(DateTime(2026, 3, 15, 3, 0)), isTrue);
      expect(quiet.contains(DateTime(2026, 3, 15, 8, 0)), isFalse);
      expect(quiet.contains(DateTime(2026, 3, 14, 12, 0)), isFalse);
    });

    test('empty window created from equal bounds disables the rule', () {
      const quiet = QuietHours(
        start: Duration(hours: 10),
        end: Duration(hours: 10),
      );
      expect(quiet.isEmpty, isTrue);
      expect(quiet.contains(DateTime(2026, 3, 14, 10)), isFalse);
    });

    test('nextPermissible returns same time when outside quiet hours', () {
      const quiet = QuietHours.night();
      final outside = DateTime(2026, 3, 14, 12, 30);
      expect(quiet.nextPermissible(outside), outside);
    });

    test('nextPermissible advances to window end for same-day window', () {
      const quiet = QuietHours(
        start: Duration(hours: 8),
        end: Duration(hours: 10),
      );
      final inside = DateTime(2026, 3, 14, 9, 15);
      expect(quiet.nextPermissible(inside), DateTime(2026, 3, 14, 10, 0));
    });

    test(
      'nextPermissible advances to next morning for pre-midnight overnight window',
      () {
        const quiet = QuietHours.night();
        final evening = DateTime(2026, 3, 14, 23, 15);
        expect(quiet.nextPermissible(evening), DateTime(2026, 3, 15, 8, 0));
      },
    );

    test(
      'nextPermissible advances to current morning for post-midnight overnight window',
      () {
        const quiet = QuietHours.night();
        final morning = DateTime(2026, 3, 14, 5, 45);
        expect(quiet.nextPermissible(morning), DateTime(2026, 3, 14, 8, 0));
      },
    );
  });
}
