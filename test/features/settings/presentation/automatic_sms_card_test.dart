import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/providers.dart' show credentialStorageProvider;
import 'package:ai_birthday/core/core_providers.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/delivery/application/birthday_sms_service.dart';
import 'package:ai_birthday/features/delivery/data/native_sms_gateway.dart';
import 'package:ai_birthday/features/settings/presentation/widgets/automatic_sms_card.dart';

class TestNativeSmsGateway implements NativeSmsGateway {
  TestNativeSmsGateway({
    this.supported = true,
    this.permission = true,
    this.logs = '[]',
  });

  bool supported;
  bool permission;
  String logs;
  final List<ScheduledSmsJob> jobs = [];

  @override
  Future<bool> isSupported() async => supported;

  @override
  Future<bool> hasSmsPermission() async => permission;

  @override
  Future<bool> requestSmsPermission() async => permission;

  @override
  Future<bool> scheduleSmsJob(ScheduledSmsJob job) async {
    jobs.add(job);
    return true;
  }

  @override
  Future<bool> cancelSmsJob(String jobId) async => true;

  @override
  Future<bool> cancelAllSmsJobs() async {
    jobs.clear();
    return true;
  }

  @override
  Future<String> getSmsAttemptsLog() async => logs;
}

void main() {
  testWidgets(
    'AutomaticSmsCard shows Play Store info banner when unsupported',
    (tester) async {
      final gateway = TestNativeSmsGateway(supported: false);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            nativeSmsGatewayProvider.overrideWithValue(gateway),
            loggerProvider.overrideWithValue(ConsoleAppLogger(sink: (_) {})),
          ],
          child: const MaterialApp(home: Scaffold(body: AutomaticSmsCard())),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Automatic SMS at Midnight (00:00)'), findsOneWidget);
      expect(
        find.textContaining('This build is the Play Store distribution'),
        findsOneWidget,
      );
      expect(find.byType(Switch), findsNothing);
    },
  );

  testWidgets(
    'AutomaticSmsCard shows switch when supported in personal distribution',
    (tester) async {
      final gateway = TestNativeSmsGateway(supported: true, permission: true);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            nativeSmsGatewayProvider.overrideWithValue(gateway),
            credentialStorageProvider.overrideWithValue(
              InMemoryCredentialStorage(),
            ),
            loggerProvider.overrideWithValue(ConsoleAppLogger(sink: (_) {})),
          ],
          child: const MaterialApp(home: Scaffold(body: AutomaticSmsCard())),
        ),
      );

      await tester.pump(const Duration(milliseconds: 50));
      await tester.pumpAndSettle();

      expect(find.text('Automatic SMS at Midnight (00:00)'), findsOneWidget);
      expect(find.text('Enable Automatic SMS'), findsOneWidget);
      expect(find.byType(Switch), findsOneWidget);
    },
  );
}
