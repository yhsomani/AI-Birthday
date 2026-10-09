// The closeness set has one definition (models/relationship.dart). It is
// re-exported here so existing imports keep resolving to the same type (F10).
export 'models/relationship.dart' show RelationshipCloseness;

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

/// Preferred personal delivery channel.
enum DeliveryChannel {
  whatsapp,
  sms,
  none;

  static DeliveryChannel parse(String value) => DeliveryChannel.values
      .firstWhere((v) => v.name == value, orElse: () => DeliveryChannel.none);
}

/// Policy governing sending.
///
/// AI-Birthday never automates personal-account sending: automatic personal
/// WhatsApp sending is not part of the product (SSOT §9). The only supported
/// policy is manual confirmation before delivery.
enum AutoSendPolicy {
  manualOnly;

  static AutoSendPolicy parse(String value) => AutoSendPolicy.values.firstWhere(
    (v) => v.name == value,
    orElse: () => AutoSendPolicy.manualOnly,
  );
}
