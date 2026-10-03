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

  static DeliveryChannel fromString(String? value) {
    if (value == null) return DeliveryChannel.whatsapp;
    return DeliveryChannel.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
      orElse: () => DeliveryChannel.whatsapp,
    );
  }
}

/// Lifecycle state of a delivery attempt.
///
/// Note (SSOT §9): The app must NEVER claim that a message was sent merely
/// because WhatsApp or SMS opened. The user must return and confirm delivery.
enum DeliveryState {
  prepared,
  handedOff,
  confirmedSent,
  failed;

  String get displayName => switch (this) {
    DeliveryState.prepared => 'Ready to Send',
    DeliveryState.handedOff => 'Handed Off to App',
    DeliveryState.confirmedSent => 'Confirmed Sent',
    DeliveryState.failed => 'Delivery Failed',
  };

  static DeliveryState fromString(String? value) {
    if (value == null) return DeliveryState.prepared;
    return DeliveryState.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
      orElse: () => DeliveryState.prepared,
    );
  }
}

/// Record of an attempted or completed message delivery.
class DeliveryRecord {
  const DeliveryRecord({
    required this.id,
    required this.draftId,
    required this.channel,
    required this.state,
    this.detail,
    required this.timestamp,
  });

  final String id;
  final String draftId;
  final DeliveryChannel channel;
  final DeliveryState state;
  final String? detail;
  final DateTime timestamp;

  Map<String, dynamic> toJson() => {
    'id': id,
    'draftId': draftId,
    'channel': channel.name,
    'state': state.name,
    if (detail != null) 'detail': detail,
    'timestamp': timestamp.toIso8601String(),
  };

  factory DeliveryRecord.fromJson(Map<String, dynamic> json) {
    return DeliveryRecord(
      id: json['id'] as String,
      draftId: json['draftId'] as String,
      channel: DeliveryChannel.fromString(json['channel'] as String?),
      state: DeliveryState.fromString(json['state'] as String?),
      detail: json['detail'] as String?,
      timestamp: DateTime.parse(json['timestamp'] as String),
    );
  }
}
