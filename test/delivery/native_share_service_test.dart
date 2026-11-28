import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/delivery/data/native_share_service.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const service = NativeShareService();
  const channel = MethodChannel('com.yashsomani.ai_birthday/share');

  testWidgets('targets each selected social app with the complete message', (
    tester,
  ) async {
    final capturedCalls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      capturedCalls.add(call);
      return true;
    });
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        channel,
        null,
      ),
    );

    const message = 'Happy Birthday!\nHope your day is wonderful.';
    const title = 'Birthday greeting for Alex';
    for (final platform in [
      DeliveryChannel.instagram,
      DeliveryChannel.snapchat,
      DeliveryChannel.linkedin,
    ]) {
      final opened = await service.shareToPlatform(
        channel: platform,
        text: message,
        title: title,
      );

      expect(opened, isTrue);
    }

    expect(capturedCalls.map((call) => call.arguments), [
      {'platform': 'instagram', 'text': message, 'title': title},
      {'platform': 'snapchat', 'text': message, 'title': title},
      {'platform': 'linkedin', 'text': message, 'title': title},
    ]);
  });

  test('rejects channels without targeted social sharing', () async {
    await expectLater(
      service.shareToPlatform(
        channel: DeliveryChannel.share,
        text: 'Happy Birthday!',
      ),
      throwsArgumentError,
    );
  });
}
