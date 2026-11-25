import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';

void main() {
  test(
    'unknown delivery channels fail closed instead of selecting WhatsApp',
    () {
      expect(DeliveryChannel.fromString('SMS'), DeliveryChannel.sms);
      expect(
        () => DeliveryChannel.fromString('futureChannel'),
        throwsFormatException,
      );
      expect(() => DeliveryChannel.fromString(null), throwsFormatException);
    },
  );

  test('unknown outcomes are not interpreted as successful launches', () {
    expect(
      DeliveryOutcome.fromString('launchFailed'),
      DeliveryOutcome.launchFailed,
    );
    expect(
      () => DeliveryOutcome.fromString('futureOutcome'),
      throwsFormatException,
    );
    expect(() => DeliveryOutcome.fromString(null), throwsFormatException);
  });
}
