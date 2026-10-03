import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/features/delivery/data/whatsapp_handoff_builder.dart';

void main() {
  group('WhatsApp Handoff Builder Tests (SSOT §9)', () {
    const builder = WhatsAppHandoffBuilder();

    test('Sanitizes international phone numbers accurately', () {
      expect(
        WhatsAppHandoffBuilder.sanitizePhoneNumber('+1 (415) 555-2671'),
        '14155552671',
      );
      expect(
        WhatsAppHandoffBuilder.sanitizePhoneNumber('+91 98765 43210'),
        '919876543210',
      );
      expect(
        WhatsAppHandoffBuilder.sanitizePhoneNumber('44 20 7946 0958'),
        '442079460958',
      );
      // Too short
      expect(WhatsAppHandoffBuilder.sanitizePhoneNumber('123'), isNull);
      // Null
      expect(WhatsAppHandoffBuilder.sanitizePhoneNumber(null), isNull);
    });

    test('Builds valid official Click-to-Chat wa.me URI', () {
      final result = builder.buildHandoff(
        rawPhoneNumber: '+1 (415) 555-1234',
        message: 'Happy Birthday John! 🎉 Have an amazing day!',
      );

      expect(result.formattedPhone, '14155551234');
      expect(result.uri.scheme, 'https');
      expect(result.uri.host, 'wa.me');
      expect(result.uri.path, '/14155551234');
      expect(
        result.uri.queryParameters['text'],
        'Happy Birthday John! 🎉 Have an amazing day!',
      );
    });

    test('Throws validation failure when phone is missing or invalid', () {
      expect(
        () => builder.buildHandoff(
          rawPhoneNumber: 'invalid-number',
          message: 'Happy birthday!',
        ),
        throwsA(isA<AppFailure>().having(
          (e) => e.code,
          'code',
          AppFailureCode.validation,
        )),
      );
    });

    test('Throws validation failure when message is empty', () {
      expect(
        () => builder.buildHandoff(
          rawPhoneNumber: '+14155551234',
          message: '   ',
        ),
        throwsA(isA<AppFailure>().having(
          (e) => e.code,
          'code',
          AppFailureCode.validation,
        )),
      );
    });
  });
}
