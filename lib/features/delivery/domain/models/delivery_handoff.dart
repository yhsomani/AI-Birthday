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
    this.outcome = DeliveryOutcome.launched,
  });

  final String birthdayId;
  final String? draftId;
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
    return DeliveryOutcome.values.firstWhere(
      (outcome) => outcome.name.toLowerCase() == value?.toLowerCase(),
      orElse: () => DeliveryOutcome.launched,
    );
  }
}
