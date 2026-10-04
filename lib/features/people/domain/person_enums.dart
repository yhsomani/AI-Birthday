/// Enumeration of closeness relationships. Controls tone choices and message
/// depth, never duplicate-detection merging.
enum RelationshipCloseness {
  family,
  close,
  goodFriend,
  friend,
  acquaintance,
  colleague,
  other;

  static RelationshipCloseness parse(String value) =>
      RelationshipCloseness.values.firstWhere(
        (v) => v.name == value,
        orElse: () => RelationshipCloseness.other,
      );
}

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
