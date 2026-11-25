import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/app_scaffold.dart';
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/core/security/flutter_secure_storage_driver.dart';

import '../e2e/harness/test_harness.dart';

class _StrictDriver implements StrictReadSecureStoreDriver {
  final Map<String, String> data = {};
  Object? failure;
  int reads = 0;

  @override
  Future<String?> readOrThrow(String key) async {
    reads++;
    if (failure != null) throw failure!;
    return data[key];
  }

  @override
  Future<String?> read(String key) async {
    try {
      return await readOrThrow(key);
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> write(String key, String value) async => data[key] = value;

  @override
  Future<void> delete(String key) async => data.remove(key);
}

class _ThrowingOnboardingStorage extends InMemoryCredentialStorage {
  @override
  Future<bool> hasCompletedOnboarding() async =>
      throw PlatformException(code: 'keystore');
}

void main() {
  group('SecureCredentialStorage onboarding flag (A3-010)', () {
    test(
      'missing is false, read error is an error (not "not onboarded")',
      () async {
        final driver = _StrictDriver();
        final storage = SecureCredentialStorage(driver);

        expect(await storage.hasCompletedOnboarding(), isFalse);

        await storage.setCompletedOnboarding(true);
        expect(await storage.hasCompletedOnboarding(), isTrue);

        driver.failure = PlatformException(code: 'keystore');
        await expectLater(
          storage.hasCompletedOnboarding(),
          throwsA(isA<PlatformException>()),
        );
      },
    );
  });

  group('OnboardingGate (A3-010)', () {
    test(
      'a positive result is cached: no further secure-storage reads',
      () async {
        final driver = _StrictDriver()
          ..data['ai_birthday_onboarding_completed'] = 'true';
        final gate = OnboardingGate(SecureCredentialStorage(driver));

        expect(await gate.isCompleted(), isTrue);
        expect(await gate.isCompleted(), isTrue);
        expect(await gate.isCompleted(), isTrue);
        expect(driver.reads, 1);
      },
    );

    test(
      'a read error never traps the user in onboarding and is retried',
      () async {
        final driver = _StrictDriver()
          ..data['ai_birthday_onboarding_completed'] = 'true'
          ..failure = PlatformException(code: 'keystore');
        final gate = OnboardingGate(SecureCredentialStorage(driver));

        expect(await gate.isCompleted(), isTrue); // error -> let through
        driver.failure = null;
        expect(await gate.isCompleted(), isTrue);
        expect(driver.reads, 2); // the failure was not cached
      },
    );

    test('not completed is re-read so finishing onboarding is seen', () async {
      final driver = _StrictDriver();
      final storage = SecureCredentialStorage(driver);
      final gate = OnboardingGate(storage);

      expect(await gate.isCompleted(), isFalse);
      await storage.setCompletedOnboarding(true);
      expect(await gate.isCompleted(), isTrue);
    });
  });

  group('router redirect with a failing secure store (A3-010)', () {
    late E2ETestHarness harness;
    setUp(() {
      harness = E2ETestHarness()..setUp();
      addTearDown(() async => harness.tearDown());
    });

    testWidgets('a Keystore read failure lands on the dashboard, not '
        'onboarding', (tester) async {
      tester.view.physicalSize = const Size(800, 1400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            credentialStorageProvider.overrideWithValue(
              _ThrowingOnboardingStorage(),
            ),
          ],
          child: const AiBirthdayApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(AppScaffold), findsOneWidget);

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 10));
    });
  });

  group('FlutterSecureStorageDriver read errors (A3-010/A3-020)', () {
    const channel = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );

    setUp(() {
      TestWidgetsFlutterBinding.ensureInitialized();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            throw PlatformException(code: 'keystore', message: 'boom');
          });
    });
    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('read degrades to null, readOrThrow surfaces the error, and the '
        'key name is never logged', () async {
      final logger = RecordingLogger();
      final driver = FlutterSecureStorageDriver(
        const FlutterSecureStorage(),
        logger,
      );

      expect(await driver.read('purchase_binding_google-sub-999'), isNull);
      await expectLater(
        driver.readOrThrow('purchase_binding_google-sub-999'),
        throwsA(isA<PlatformException>()),
      );

      expect(logger.records, hasLength(2));
      for (final record in logger.records) {
        expect(record.message, isNot(contains('google-sub-999')));
      }
    });
  });
}
