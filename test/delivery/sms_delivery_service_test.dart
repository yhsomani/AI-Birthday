import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/delivery/data/sms_delivery_service.dart';

void main() {
  group('SmsDeliveryService (A2-018)', () {
    test('keeps the + prefix and percent-encodes spaces as %20', () {
      final uri = SmsDeliveryService.buildUri(
        phoneNumber: '+1 (415) 555-2671',
        message: 'Hi there',
      );
      expect(uri.toString(), 'sms:+14155552671?body=Hi%20there');
    });

    test('encodes reserved characters and keeps a literal + in the body', () {
      final uri = SmsDeliveryService.buildUri(
        phoneNumber: '+14155552671',
        message: 'A+B & C=D?',
      );
      expect(uri.toString(), 'sms:+14155552671?body=A%2BB%20%26%20C%3DD%3F');
    });

    test('national numbers stay digits-only; 00 prefix becomes +', () {
      expect(
        SmsDeliveryService.buildUri(
          phoneNumber: '415-555-2671',
          message: 'Hi',
        ).toString(),
        'sms:4155552671?body=Hi',
      );
      expect(
        SmsDeliveryService.buildUri(
          phoneNumber: '0044 20 7946 0958',
          message: 'Hi',
        ).toString(),
        'sms:+442079460958?body=Hi',
      );
    });

    test('national-format numbers and short codes are accepted', () {
      expect(
        SmsDeliveryService.buildUri(
          phoneNumber: '0123456789',
          message: 'Hi',
        ).toString(),
        'sms:0123456789?body=Hi',
      );
      expect(
        SmsDeliveryService.buildUri(
          phoneNumber: '12345',
          message: '',
        ).toString(),
        'sms:12345',
      );
    });

    test('invalid or empty numbers are rejected and never launched', () async {
      var launches = 0;
      final service = SmsDeliveryService(
        launcher: (_) async {
          launches++;
          return true;
        },
      );
      for (final bad in <String?>[null, '', '12', 'abc', '1234567890123456']) {
        expect(await service.sendSms(phoneNumber: bad, message: 'Hi'), isFalse);
      }
      expect(launches, 0);
    });

    test(
      'sendSms launches the built URI and reports launcher failure',
      () async {
        Uri? launched;
        final ok = SmsDeliveryService(
          launcher: (uri) async {
            launched = uri;
            return true;
          },
        );
        expect(
          await ok.sendSms(phoneNumber: '+14155552671', message: 'Hi there'),
          isTrue,
        );
        expect(launched.toString(), 'sms:+14155552671?body=Hi%20there');

        final throwing = SmsDeliveryService(
          launcher: (_) => throw Exception('x'),
        );
        expect(
          await throwing.sendSms(phoneNumber: '+14155552671', message: 'Hi'),
          isFalse,
        );
      },
    );
  });
}
