import 'package:ai_birthday/app/providers.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('durationUntilNextLocalMidnight (audit F09)', () {
    test('counts down to the next local midnight', () {
      expect(
        durationUntilNextLocalMidnight(DateTime(2026, 10, 9, 23, 30)),
        const Duration(minutes: 30),
      );
      expect(
        durationUntilNextLocalMidnight(DateTime(2026, 10, 9, 12)),
        const Duration(hours: 12),
      );
    });

    test('exactly at midnight, the next rollover is a full day away', () {
      expect(
        durationUntilNextLocalMidnight(DateTime(2026, 10, 10)),
        const Duration(hours: 24),
      );
    });

    test('rolls over month and year boundaries', () {
      expect(
        durationUntilNextLocalMidnight(DateTime(2026, 12, 31, 23, 0)),
        const Duration(hours: 1),
      );
    });
  });
}
