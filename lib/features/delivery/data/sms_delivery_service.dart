/// SMS Delivery Service via standard Android URI scheme (SSOT §10).
library;

import 'package:url_launcher/url_launcher.dart';

class SmsDeliveryService {
  const SmsDeliveryService();

  /// Launches the native SMS messaging app with pre-filled [message] and [phoneNumber].
  Future<bool> sendSms({
    required String? phoneNumber,
    required String message,
  }) async {
    final cleanPhone = phoneNumber?.replaceAll(RegExp(r'\D'), '') ?? '';
    final uri = Uri(
      scheme: 'sms',
      path: cleanPhone,
      queryParameters: message.isNotEmpty ? {'body': message} : null,
    );
    try {
      return await launchUrl(uri);
    } catch (_) {
      return false;
    }
  }
}
