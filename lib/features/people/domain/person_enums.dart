// The closeness set has one definition (models/relationship.dart). It is
// re-exported here so existing imports keep resolving to the same type (F10).
export 'models/relationship.dart' show RelationshipCloseness;
export '../../delivery/domain/models/delivery_channel.dart'
    show DeliveryChannel;

/// Preferred message tone for a recipient.
enum PreferredTone {
  warm,
  funny,
  emotional,
  casual,
  professional,
  neutral;

  static PreferredTone parse(String value) => PreferredTone.values.firstWhere(
    (v) => v.name == value,
    orElse: () => PreferredTone.warm,
  );
}

/// Policy governing sending.
///
/// Existing recipients default to [AutoSendPolicy.manualOnly].
/// In the personal sideload distribution, users can explicitly opt in to
/// [AutoSendPolicy.automaticSmsApproved] for a specific recipient whose message
/// has been reviewed and approved.
enum AutoSendPolicy {
  manualOnly,
  automaticSmsApproved;

  static AutoSendPolicy parse(String value) {
    // ⚡ Bolt: Hoist invariant .toLowerCase() outside the loop to avoid redundant string allocations
    final lowerValue = value.toLowerCase();
    return AutoSendPolicy.values.firstWhere(
      (v) => v.name.toLowerCase() == lowerValue,
      orElse: () => AutoSendPolicy.manualOnly,
    );
  }
}
