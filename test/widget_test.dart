import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';

class _FakeStoreDriver implements SecureStoreDriver {
  final Map<String, String> _data = {};
  @override
  Future<void> delete(String key) async => _data.remove(key);
  @override
  Future<String?> read(String key) async => _data[key];
  @override
  Future<void> write(String key, String value) async => _data[key] = value;
}

void main() {
  testWidgets('App root redirects to onboarding on first run', (
    WidgetTester tester,
  ) async {
    final driver = _FakeStoreDriver();
    final storage = SecureCredentialStorage(driver);

    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [credentialStorageProvider.overrideWithValue(storage)],
        child: const AiBirthdayApp(),
      ),
    );
    await tester.pumpAndSettle();

    // Verify onboarding screen is shown
    expect(find.text('Never miss a birthday that matters.'), findsOneWidget);
    expect(find.text('Next'), findsOneWidget);
    expect(find.text('Skip'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 10));
  });

  testWidgets(
    'App root initializes and renders unified dashboard shell when onboarding completed',
    (WidgetTester tester) async {
      final driver = _FakeStoreDriver();
      final storage = SecureCredentialStorage(driver);
      await storage.setCompletedOnboarding(true);

      tester.view.physicalSize = const Size(800, 1400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [credentialStorageProvider.overrideWithValue(storage)],
          child: const AiBirthdayApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Verify app title in AppBar
      expect(find.text('AI-Birthday'), findsWidgets);

      // Verify system theme enforcement
      final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(materialApp.themeMode, ThemeMode.system);

      // Verify navigation destinations
      expect(find.text('Dashboard'), findsOneWidget);
      expect(find.text('People'), findsOneWidget);
      expect(find.text('Settings'), findsOneWidget);

      // Drain StreamProvider cancellation timers before test concludes
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 10));
    },
  );
}
