/// Persisted evidence that a message was handed off to an external app
/// (audit 03 P1-1).
///
/// This is the ONLY durable record that a launch happened. The app must
/// never infer "opened" from birthday/draft status alone; [channel] names
/// the exact external app (WhatsApp, SMS, Share Sheet) so History can be
/// truthful about where the message went.
library;

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';

class DeliveryHandoff {
  const DeliveryHandoff({
    required this.birthdayId,
    required this.channel,
    required this.at,
  });

  final String birthdayId;
  final DeliveryChannel channel;
  final DateTime at;
}
