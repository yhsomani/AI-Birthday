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

  /// Records the handoff and moves the birthday to handed off in one
  /// transaction, so the evidence row and the status can never diverge (F20).
  Future<void> recordHandoffAndMarkHandedOff({
    required String birthdayId,
    required DeliveryChannel channel,
    required DateTime at,
  });

  /// The most recent known handoff for a birthday, or null if none.
  Future<DeliveryHandoff?> latestHandoffForBirthday(String birthdayId);

  /// Live stream of every persisted handoff, newest first order agnostic
  /// (consumers resolve "latest per birthday" themselves).
  Stream<List<DeliveryHandoff>> watchHandoffs();

  /// Live stream of the most recent handoff per birthday, restricted to
  /// [birthdayIds] (query audit: "batched related data"). Consumers with a
  /// bounded set of birthdays — e.g. History's draft list — use this instead
  /// of materializing the full, unbounded event log.
  Stream<List<DeliveryHandoff>> watchLatestHandoffs(Set<String> birthdayIds);
}
