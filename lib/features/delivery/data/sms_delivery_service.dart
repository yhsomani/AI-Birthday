/// SMS Delivery Service via standard Android URI scheme (legacy SSOT §10 (PRODUCT.md §16.7)).
library;

import 'package:url_launcher/url_launcher.dart';

class SmsDeliveryService {
  const SmsDeliveryService({this.launcher});

  /// Test seam; defaults to `url_launcher`.
  final Future<bool> Function(Uri uri)? launcher;

  /// Builds the `sms:` composer URI, or null when [phoneNumber] is not a usable
  /// number.
  ///
  /// Unlike WhatsApp (which needs an international number), the SMS composer
  /// accepts national-format numbers and short codes, so a stored number the
  /// person form accepts (for example `0123456789`) still opens the composer.
  /// A leading `+` or `00` marks an international number and becomes `+`; the
  /// body is percent-encoded (`%20`, never form-style `+`).
  static Uri? buildUri({
    required String? phoneNumber,
    required String message,
  }) {
    if (phoneNumber == null) return null;
    final trimmed = phoneNumber.trim();
    final international = trimmed.startsWith('+') || trimmed.startsWith('00');
    var digits = trimmed.replaceAll(RegExp(r'\D'), '');
    if (trimmed.startsWith('00')) digits = digits.substring(2);
    if (digits.length < 3 || digits.length > 15) return null;
    final number = international ? '+$digits' : digits;
    final query = message.isEmpty
        ? ''
        : '?body=${Uri.encodeComponent(message)}';
    return Uri.parse('sms:$number$query');
  }

  /// Launches the native SMS messaging app with pre-filled [message] and [phoneNumber].
  ///
  /// Returns false when the number is unusable or the composer cannot launch.
  Future<bool> sendSms({
    required String? phoneNumber,
    required String message,
  }) async {
    final uri = buildUri(phoneNumber: phoneNumber, message: message);
    if (uri == null) return false;
    try {
      return await (launcher ?? launchUrl)(uri);
    } catch (_) {
      return false;
    }
  }
}
