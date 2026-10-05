/// Native Android Share Chooser service (SSOT §10).
library;

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

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
}
