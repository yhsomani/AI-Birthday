/// Shared harness for shell/navigation tests (Phase 3): pumps the real
/// application with a fake secure store and onboarding already completed,
/// so the shell (bottom navigation) is the first frame.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';

/// In-memory [SecureStoreDriver] (mirrors test/widget_test.dart).
class FakeStoreDriver implements SecureStoreDriver {
  final Map<String, String> _data = {};

  @override
  Future<void> delete(String key) async => _data.remove(key);

  @override
  Future<String?> read(String key) async => _data[key];

  @override
  Future<void> write(String key, String value) async => _data[key] = value;
}

/// Pumps the real app at [size] logical px (DPR 1.0) with onboarding
/// marked complete.
Future<void> pumpCompletedApp(
  WidgetTester tester, {
  Size size = const Size(800, 1400),
}) async {
  final storage = SecureCredentialStorage(FakeStoreDriver());
  await storage.setCompletedOnboarding(true);

  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [credentialStorageProvider.overrideWithValue(storage)],
      child: const AiBirthdayApp(),
    ),
  );
  await tester.pumpAndSettle();
}

/// Replaces the app with an empty tree so StreamProviders cancel, then pumps
/// their cancellation timers (required before a test concludes).
Future<void> drainApp(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(milliseconds: 10));
}
