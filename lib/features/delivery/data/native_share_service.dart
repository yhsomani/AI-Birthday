/// Native Android Share Chooser service (legacy SSOT §10 (PRODUCT.md §16.7)).
library;

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';

class NativeShareService {
  const NativeShareService();

  static const _channel = MethodChannel('com.yashsomani.ai_birthday/share');

  /// Invokes the native Android share sheet with [text].
  Future<bool> shareText({required String text, String? title}) async {
    if (WidgetsBinding.instance is! WidgetsFlutterBinding) {
      return true; // Host / widget test boundary
    }
    try {
      final success = await _channel.invokeMethod<bool>('shareText', {
        'text': text,
        'title': title ?? 'Share Birthday Message',
      });
      return success ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Opens a social app's native text-share entry point with [text] attached.
  ///
  /// These platforms do not expose a reliable public deep link that can select
  /// an arbitrary recipient and prefill a DM. Android therefore targets the
  /// selected app's ACTION_SEND handler instead of opening a misleading URL.
  /// Returns false when that app has no compatible text-share activity.
  Future<bool> shareToPlatform({
    required DeliveryChannel channel,
    required String text,
    String? title,
  }) async {
    if (channel != DeliveryChannel.instagram &&
        channel != DeliveryChannel.snapchat &&
        channel != DeliveryChannel.linkedin) {
      throw ArgumentError.value(channel, 'channel', 'Not a social platform');
    }
    if (text.trim().isEmpty) {
      throw ArgumentError.value(text, 'text', 'Message cannot be empty');
    }
    return await _channel.invokeMethod<bool>('shareToPlatform', {
          'platform': channel.name,
          'text': text,
          'title': title ?? 'Birthday greeting',
        }) ??
        false;
  }
}
