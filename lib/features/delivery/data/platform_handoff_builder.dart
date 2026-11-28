/// Direct messaging & native sharing handoff builder for all supported platforms.
library;

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';

/// Result of building a platform handoff URI.
class PlatformHandoffPlan {
  const PlatformHandoffPlan({
    required this.channel,
    required this.primaryUri,
    required this.supportsDirectChatPrefill,
    required this.requiresNativeShareFallback,
  });

  final DeliveryChannel channel;
  final Uri? primaryUri;
  final bool supportsDirectChatPrefill;
  final bool requiresNativeShareFallback;
}

/// Builds direct-to-chat deep links with pre-filled text where supported,
/// or marks the closest native sharing fallback where platforms restrict it.
class PlatformHandoffBuilder {
  const PlatformHandoffBuilder();

  PlatformHandoffPlan build({
    required DeliveryChannel channel,
    required String message,
    String? phoneNumber,
  }) {
    final encoded = Uri.encodeComponent(message);
    final digits = (phoneNumber ?? '').replaceAll(RegExp(r'[^\d+]'), '');

    return switch (channel) {
      DeliveryChannel.whatsapp => PlatformHandoffPlan(
        channel: channel,
        primaryUri: Uri.parse(
          digits.isNotEmpty
              ? 'https://wa.me/${digits.replaceAll('+', '')}?text=$encoded'
              : 'whatsapp://send?text=$encoded',
        ),
        supportsDirectChatPrefill: true,
        requiresNativeShareFallback: false,
      ),
      DeliveryChannel.sms => PlatformHandoffPlan(
        channel: channel,
        primaryUri: Uri.parse(
          digits.isNotEmpty
              ? 'sms:$digits?body=$encoded'
              : 'sms:?body=$encoded',
        ),
        supportsDirectChatPrefill: true,
        requiresNativeShareFallback: false,
      ),
      DeliveryChannel.linkedin ||
      DeliveryChannel.instagram ||
      DeliveryChannel.snapchat => PlatformHandoffPlan(
        channel: channel,
        primaryUri: null,
        supportsDirectChatPrefill: false,
        requiresNativeShareFallback: true,
      ),
      DeliveryChannel.clipboard || DeliveryChannel.share => PlatformHandoffPlan(
        channel: channel,
        primaryUri: null,
        supportsDirectChatPrefill: false,
        requiresNativeShareFallback: channel == DeliveryChannel.share,
      ),
    };
  }
}
