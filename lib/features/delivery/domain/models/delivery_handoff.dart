/// One append-only record of a manual delivery action and its outcome.
///
/// The app never infers that a launch happened from birthday/draft status;
/// [channel] identifies the action target and [outcome] states what occurred.
library;

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';

class DeliveryHandoff {
  const DeliveryHandoff({
    required this.birthdayId,
    required this.channel,
    required this.at,
    this.draftId,
    this.draftRevision,
    this.bodyHash,
    this.outcome = DeliveryOutcome.launched,
  });

  final String birthdayId;
  final String? draftId;
  final int? draftRevision;
  final String? bodyHash;
  final DeliveryChannel channel;
  final DateTime at;
  final DeliveryOutcome outcome;
}

enum DeliveryOutcome {
  launched,
  copied,
  launchFailed,
  notSent,
  confirmed;

  static DeliveryOutcome fromString(String? value) {
    if (value == null) {
      throw const FormatException('Delivery outcome cannot be null.');
    }
    for (final outcome in DeliveryOutcome.values) {
      if (outcome.name.toLowerCase() == value.toLowerCase()) return outcome;
    }
    throw FormatException('Unknown delivery outcome: $value');
  }
}
