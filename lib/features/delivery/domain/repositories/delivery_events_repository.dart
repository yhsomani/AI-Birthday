/// Repository of persisted external-app launch events (audit 03 P1-1).
library;

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';

abstract interface class DeliveryEventsRepository {
  /// Persists that a message was successfully handed off to [channel].
  Future<void> recordHandoff({
    required String birthdayId,
    required DeliveryChannel channel,
    required DateTime at,
  });

  /// The most recent known handoff for a birthday, or null if none.
  Future<DeliveryHandoff?> latestHandoffForBirthday(String birthdayId);

  /// Live stream of every persisted handoff, newest first order agnostic
  /// (consumers resolve "latest per birthday" themselves).
  Stream<List<DeliveryHandoff>> watchHandoffs();
}