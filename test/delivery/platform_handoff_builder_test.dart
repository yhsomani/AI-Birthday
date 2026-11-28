import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/delivery/data/platform_handoff_builder.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';

void main() {
  const builder = PlatformHandoffBuilder();

  test('WhatsApp builds pre-filled wa.me link with phone number', () {
    final plan = builder.build(
      channel: DeliveryChannel.whatsapp,
      message: 'Happy Birthday!',
      phoneNumber: '+1 555-123-4567',
    );
    expect(plan.supportsDirectChatPrefill, isTrue);
    expect(plan.requiresNativeShareFallback, isFalse);
    expect(
      plan.primaryUri.toString(),
      'https://wa.me/15551234567?text=Happy%20Birthday!',
    );
  });

  test('SMS builds pre-filled sms: link with body', () {
    final plan = builder.build(
      channel: DeliveryChannel.sms,
      message: 'Happy Birthday!',
      phoneNumber: '+15551234567',
    );
    expect(plan.supportsDirectChatPrefill, isTrue);
    expect(plan.requiresNativeShareFallback, isFalse);
    expect(
      plan.primaryUri.toString(),
      'sms:+15551234567?body=Happy%20Birthday!',
    );
  });

  test(
    'social platforms use targeted native share instead of fake chat links',
    () {
      for (final channel in [
        DeliveryChannel.instagram,
        DeliveryChannel.snapchat,
        DeliveryChannel.linkedin,
      ]) {
        final plan = builder.build(
          channel: channel,
          message: 'Happy Birthday!',
        );
        expect(plan.primaryUri, isNull);
        expect(plan.supportsDirectChatPrefill, isFalse);
        expect(plan.requiresNativeShareFallback, isTrue);
      }
    },
  );
}
