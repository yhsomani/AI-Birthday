import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/shared/design_system/countdown_chip.dart';

/// Single source of truth for countdown labels (audit 05 P2-6): the label
/// used by CountdownChip is the same one the People list tiles render, so the
/// edge-case mapping is pinned here once.
void main() {
  group('CountdownChip.labelFor', () {
    test('maps proximity buckets to labels', () {
      expect(CountdownChip.labelFor(daysUntil: 0), 'Today');
      expect(CountdownChip.labelFor(daysUntil: 1), 'Tomorrow');
      expect(CountdownChip.labelFor(daysUntil: 45), 'In 45 days');
      expect(CountdownChip.labelFor(daysUntil: 90), 'In 90 days');
      expect(CountdownChip.labelFor(daysUntil: 91), 'In 4 months');
      expect(CountdownChip.labelFor(daysUntil: 365), 'In 13 months');
    });

    test('isToday overrides a small day count', () {
      expect(CountdownChip.labelFor(daysUntil: 2, isToday: true), 'Today');
    });

    test('customLabel is honored verbatim', () {
      expect(
        CountdownChip.labelFor(daysUntil: 5, customLabel: 'Almost'),
        'Almost',
      );
    });
  });
}
