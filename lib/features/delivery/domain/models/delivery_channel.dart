/// Supported delivery channels and delivery states (legacy SSOT §9, §10 (PRODUCT.md §16.7)).
library;

/// Outbound channel for sending or sharing the message.
enum DeliveryChannel {
  whatsapp,
  sms,
  instagram,
  snapchat,
  linkedin,
  clipboard,
  share;

  String get displayName => switch (this) {
    DeliveryChannel.whatsapp => 'WhatsApp',
    DeliveryChannel.sms => 'Messages / SMS',
    DeliveryChannel.instagram => 'Instagram',
    DeliveryChannel.snapchat => 'Snapchat',
    DeliveryChannel.linkedin => 'LinkedIn',
    DeliveryChannel.clipboard => 'Copy to Clipboard',
    DeliveryChannel.share => 'Share Sheet',
  };

  static DeliveryChannel fromString(String? value) {
    if (value == null) {
      throw const FormatException('Delivery channel cannot be null.');
    }
    final target = value.toLowerCase();
    for (final channel in DeliveryChannel.values) {
      if (channel.name.toLowerCase() == target) return channel;
    }
    throw FormatException('Unknown delivery channel: $value');
  }
}

/// Note (legacy SSOT §9 (PRODUCT.md §16.7)): The app must NEVER claim that a message was sent merely
/// because WhatsApp or SMS opened. The user must return and confirm delivery.
/// Successful launches are recorded as persisted [DeliveryHandoff] events;
/// see `DeliveryEventsRepository`.
