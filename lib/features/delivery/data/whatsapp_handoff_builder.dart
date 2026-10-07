/// WhatsApp Click-to-Chat handoff builder adhering strictly to SSOT §9.
///
/// Principles:
/// - Uses official WhatsApp Click-to-Chat URLs (`https://wa.me/<number>?text=<encoded_text>`).
/// - Never attempts background automation, tap simulation, or unofficial APIs.
/// - Requires explicit user review before generating the handoff.
/// - Every successful launch is recorded as a persisted delivery event
///   (channel + timestamp); the app NEVER claims sent until the user returns
///   and confirms (audit 03 P1-1).
library;

import 'package:ai_birthday/core/errors/app_failure.dart';

/// Structured result of a WhatsApp handoff preparation.
class WhatsAppHandoffResult {
  const WhatsAppHandoffResult({
    required this.uri,
    required this.formattedPhone,
    required this.message,
  });

  /// The official `https://wa.me/...` URI ready to be launched.
  final Uri uri;

  /// The cleaned international phone number.
  final String formattedPhone;

  /// The prefilled message body.
  final String message;
}

class WhatsAppHandoffBuilder {
  const WhatsAppHandoffBuilder();

  /// Sanitizes a phone number to standard international format required by WhatsApp.
  ///
  /// WhatsApp `wa.me` format requires numbers without '+', brackets, hyphens, or spaces.
  /// E.g. "+1 (555) 123-4567" -> "15551234567"
  static String? sanitizePhoneNumber(String? rawPhone) {
    if (rawPhone == null) return null;
    var digitsOnly = rawPhone.replaceAll(RegExp(r'\D'), '');
    if (digitsOnly.startsWith('00')) {
      digitsOnly = digitsOnly.substring(2);
    }
    if (digitsOnly.length < 7 ||
        digitsOnly.length > 15 ||
        digitsOnly.startsWith('0')) {
      return null;
    }
    return digitsOnly;
  }

  /// Builds the official WhatsApp Click-to-Chat URI for [phoneNumber] and [message].
  ///
  /// Throws [AppFailure.validation] if the phone number is invalid or message is empty.
  WhatsAppHandoffResult buildHandoff({
    required String? rawPhoneNumber,
    required String message,
  }) {
    final cleanPhone = sanitizePhoneNumber(rawPhoneNumber);
    if (cleanPhone == null) {
      throw const AppFailure.validation(
        detail:
            'A valid phone number with country code is required for WhatsApp.',
      );
    }

    final trimmedMessage = message.trim();
    if (trimmedMessage.isEmpty) {
      throw const AppFailure.validation(detail: 'Message cannot be empty.');
    }

    // Build standard wa.me URL with properly percent-encoded text query
    final uri = Uri.https('wa.me', '/$cleanPhone', {'text': trimmedMessage});

    return WhatsAppHandoffResult(
      uri: uri,
      formattedPhone: cleanPhone,
      message: trimmedMessage,
    );
  }
}
