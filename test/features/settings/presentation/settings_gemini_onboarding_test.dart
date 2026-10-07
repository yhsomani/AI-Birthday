import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class InMemoryCredentialStorage implements CredentialStorage {
  String? key;
  DateTime? verifiedAt;

  @override
  Future<void> deleteGeminiApiKey() async {
    key = null;
    verifiedAt = null;
  }

  @override
  Future<String?> getGeminiApiKey() async {
    return key;
  }

  @override
  Future<bool> hasGeminiApiKey() async {
    return key != null && key!.isNotEmpty;
  }

  @override
  Future<void> saveGeminiApiKey(String apiKey) async {
    key = apiKey;
  }

  @override
  Future<DateTime?> geminiKeyVerifiedAt() async => verifiedAt;

  @override
  Future<void> recordGeminiKeyVerifiedAt(DateTime at) async {
    verifiedAt = at;
  }

  @override
  Future<void> clearGeminiKeyVerifiedAt() async {
    verifiedAt = null;
  }

  @override
  Future<bool> hasCompletedOnboarding() async => false;

  @override
  Future<void> setCompletedOnboarding(bool completed)
      async {}
}

void main() {
  late InMemoryCredentialStorage storage;

  setUp(() {
    storage = InMemoryCredentialStorage();
  });

  Future<void> pumpSettings(
    WidgetTester tester, {
    UserGeminiApiProvider? customGeminiProvider,
  }) async {
    tester.view.physicalSize = const Size(800, 2000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          credentialStorageProvider.overrideWithValue(storage),
          if (customGeminiProvider != null)
            userGeminiApiProvider.overrideWithValue(customGeminiProvider),
        ],
        child: const MaterialApp(home: SettingsScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'renders guided onboarding controls, security footnote & AI status',
    (tester) async {
      await pumpSettings(tester);

      expect(find.text('Personal Gemini API Key'), findsOneWidget);
      expect(find.text('Not Configured'), findsOneWidget);
      expect(find.text('Get Gemini API key ↗'), findsOneWidget);
      expect(find.text('How to connect'), findsOneWidget);
      expect(find.text('Test Connection'), findsOneWidget);
      expect(find.text('Save Key'), findsOneWidget);
      expect(find.byIcon(Icons.bolt), findsOneWidget);
      expect(
        find.text(
          'Stored securely on this device in hardware-backed encrypted storage. Sent directly to Google Gemini API only when generating messages, and never to AI-Birthday servers. Never included in sync or application logs.',
        ),
        findsOneWidget,
      );
      expect(
        find.text('Gemini API billing, quotas & free limits ↗'),
        findsOneWidget,
      );
      expect(find.text('Gemini Nano (AICore)'), findsOneWidget);
    },
  );

  testWidgets(
    'tapping "How to connect" opens step-by-step setup guide bottom sheet',
    (tester) async {
      await pumpSettings(tester);

      await tester.tap(find.text('How to connect'));
      await tester.pumpAndSettle();

      expect(find.text('How to connect Gemini'), findsOneWidget);
      expect(
        find.text('Step-by-step setup in Google AI Studio'),
        findsOneWidget,
      );
      expect(find.text('Open Google AI Studio'), findsOneWidget);
      expect(find.text('Create an API Key'), findsOneWidget);
      expect(find.text('Copy your API Key'), findsOneWidget);
      expect(find.text('Paste and Test in Settings'), findsOneWidget);
      expect(find.text('Important Notes on Usage & Billing'), findsOneWidget);
      expect(find.text('Got it'), findsOneWidget);

      await tester.tap(find.text('Got it'));
      await tester.pumpAndSettle();

      expect(find.text('How to connect Gemini'), findsNothing);
    },
  );

  testWidgets('empty test connection triggers validation message', (
    tester,
  ) async {
    await pumpSettings(tester);

    await tester.tap(find.text('Test Connection'));
    await tester.pumpAndSettle();

    expect(find.text('Please enter an API key to test.'), findsOneWidget);
  });

  testWidgets('successful test connection marks connected and saves key', (
    tester,
  ) async {
    final customProvider = UserGeminiApiProvider(
      credentialStorage: storage,
      httpSender: (uri, headers, body) async {
        return const HttpResponsePayload(
          statusCode: 200,
          body: '{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}',
        );
      },
    );

    await pumpSettings(tester, customGeminiProvider: customProvider);

    await tester.enterText(
      find.widgetWithText(TextField, 'Paste your Gemini API key'),
      'AIzaSyTestValidKey',
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Test Connection'));
    await tester.pumpAndSettle();

    expect(find.text('Connected'), findsOneWidget);
    expect(
      find.text(
        'Gemini is ready. Messages will use your personal Gemini API access.',
      ),
      findsOneWidget,
    );
    expect(storage.key, 'AIzaSyTestValidKey');
  });

  testWidgets('invalid key test connection shows Google rejection banner', (
    tester,
  ) async {
    final customProvider = UserGeminiApiProvider(
      credentialStorage: storage,
      httpSender: (uri, headers, body) async {
        return const HttpResponsePayload(
          statusCode: 400,
          body: '{"error":{"message":"API key not valid"}}',
        );
      },
    );

    await pumpSettings(tester, customGeminiProvider: customProvider);

    await tester.enterText(
      find.widgetWithText(TextField, 'Paste your Gemini API key'),
      'AIzaSyInvalidKey',
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Test Connection'));
    await tester.pumpAndSettle();

    expect(find.text('Invalid Key'), findsOneWidget);
    expect(
      find.text(
        'Google rejected this key. Check the key in Google AI Studio and try again.',
      ),
      findsOneWidget,
    );
    expect(storage.key, isNull);
  });

  testWidgets('quota exceeded test connection shows quota warning banner', (
    tester,
  ) async {
    final customProvider = UserGeminiApiProvider(
      credentialStorage: storage,
      httpSender: (uri, headers, body) async {
        return const HttpResponsePayload(
          statusCode: 429,
          body: '{"error":{"message":"Resource exhausted"}}',
        );
      },
    );

    await pumpSettings(tester, customGeminiProvider: customProvider);

    await tester.enterText(
      find.widgetWithText(TextField, 'Paste your Gemini API key'),
      'AIzaSyQuotaKey',
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Test Connection'));
    await tester.pumpAndSettle();

    expect(find.text('Quota Exceeded'), findsOneWidget);
    expect(
      find.text(
        'Your Google Gemini project has reached its current quota. Check usage or billing in Google AI Studio.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('manual Save Key and Remove Key workflow', (tester) async {
    await pumpSettings(tester);

    await tester.enterText(
      find.widgetWithText(TextField, 'Paste your Gemini API key'),
      'AIzaSySavedManually',
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Save Key'));
    await tester.pumpAndSettle();

    expect(storage.key, 'AIzaSySavedManually');
    // Raw save is UNVERIFIED: the badge must not claim readiness (audit 02
    // P2-1), and the unvalidated save must not inherit older verifications.
    expect(find.text('Unverified'), findsOneWidget);
    expect(storage.verifiedAt, isNull);
    expect(find.text('Remove Key'), findsOneWidget);

    await tester.tap(find.text('Remove Key'));
    await tester.pumpAndSettle();

    expect(storage.key, isNull);
    expect(storage.verifiedAt, isNull);
    expect(find.text('Not Configured'), findsOneWidget);
  });

  testWidgets(
    'previously verified key shows durable "Verified <date>" badge on reopen',
    (tester) async {
      storage.key = 'AIzaSyVerifiedBefore';
      storage.verifiedAt = DateTime.now().subtract(const Duration(days: 3));

      await pumpSettings(tester);

      // Never a bare green "Connected" without a live test this session
      // (audit 02 P1-2); the durable badge carries the last-verified date.
      expect(find.textContaining('Verified ·'), findsOneWidget);
      expect(find.text('Connected'), findsNothing);
      expect(find.text('Unverified'), findsNothing);
    },
  );
}
