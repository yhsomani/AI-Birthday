/// Supported delivery channels and delivery states (SSOT §9, §10).
library;

/// Outbound channel for sending or sharing the message.
enum DeliveryChannel {
  whatsapp,
  sms,
  clipboard,
  share;

  String get displayName => switch (this) {
    DeliveryChannel.whatsapp => 'WhatsApp',
    DeliveryChannel.sms => 'SMS',
    DeliveryChannel.clipboard => 'Copy to Clipboard',
    DeliveryChannel.share => 'Share Sheet',
  };

  static final _byName = {
    for (var e in DeliveryChannel.values) e.name.toLowerCase(): e,
  };

  static DeliveryChannel fromString(String? value) {
    if (value == null) return DeliveryChannel.whatsapp;
    return _byName[value.toLowerCase()] ?? DeliveryChannel.whatsapp;
  }
}

/// Note (SSOT §9): The app must NEVER claim that a message was sent merely
/// because WhatsApp or SMS opened. The user must return and confirm delivery.
/// Successful launches are recorded as persisted [DeliveryHandoff] events;
/// see `DeliveryEventsRepository`.
