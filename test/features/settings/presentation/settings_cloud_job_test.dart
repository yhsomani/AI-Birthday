/// Settings Cloud Backup durable-status behavior (background-jobs audit).
///
/// Backup/restore run as `cloud_sync` jobs. The screen follows the job's
/// transitions (a widget-bound spinner would lie about a job that outlives
/// the screen) and keeps failures visible inline with a retry affordance.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:ai_birthday/features/sync/data/cloud_sync_service.dart';

import '../../auth/support/fake_auth_gateway.dart';
import '../../../e2e/harness/test_harness.dart';

class InMemoryStoreDriver implements SecureStoreDriver {
  final Map<String, String> data = {};

  @override
  Future<void> delete(String key) async => data.remove(key);

  @override
  Future<String?> read(String key) async => data[key];

  @override
  Future<void> write(String key, String value) async => data[key] = value;
}

class GatedCloudSyncService extends CloudSyncService {
  GatedCloudSyncService({required super.db})
    : super(store: InMemoryStoreDriver());

  /// When set, `sync` waits on this before returning, so the test can observe
  /// the durable "running" state.
  Completer<void>? gate;

  CloudSyncResult result = CloudSyncResult(
    success: true,
    timestamp: DateTime.utc(2026, 1, 1),
  );

  @override
  Future<CloudSyncResult> sync(AuthState authState) async {
    final g = gate;
    if (g != null) await g.future;
    return result;
  }

  @override
  Future<CloudSyncResult> restore(AuthState authState) => Future.value(result);
}

void main() {
  group('Settings Cloud Backup durable status (background-jobs audit)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() async {
      // Riverpod owns the SubscriptionNotifier and disposes it exactly once
      // when a widget tree unmounts. Disposing it here AND letting a stale
      // tree unmount in the next test would double-dispose (StateError), so
      // only release the non-Riverpod resources.
      await harness.fakeAiCore.dispose();
      await harness.db.close();
    });

    Future<void> pumpSettings(
      WidgetTester tester, {
      required GatedCloudSyncService service,
    }) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            googleAuthGatewayProvider.overrideWithValue(FakeAuthGateway()),
            cloudSyncServiceProvider.overrideWithValue(service),
          ],
          child: const MaterialApp(home: SettingsScreen()),
        ),
      );
      await tester.pumpAndSettle();
    }

    Future<void> signIn(WidgetTester tester) async {
      await tester.tap(find.text('Sign in with Google'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Continue with Google'));
      await tester.pumpAndSettle();
      expect(find.text('Sign out'), findsOneWidget); // signed in

      // Let the "Successfully signed in!" snackbar expire so a completion
      // snackbar asserted later is not stuck in the messenger queue behind it.
      await tester.pump(const Duration(seconds: 5));
      await tester.pumpAndSettle();
    }

    Future<void> tapBackUpNow(WidgetTester tester) async {
      await tester.ensureVisible(find.text('Back Up Now'));
      await tester.tap(find.text('Back Up Now'));
    }

    testWidgets(
      'backup shows truthful running state, then completes with the snackbar',
      (tester) async {
        final service = GatedCloudSyncService(db: harness.db)
          ..gate = Completer();
        await pumpSettings(tester, service: service);
        await signIn(tester);

        await tapBackUpNow(tester);
        await tester.pump();

        // In-flight: the button is replaced by the durable running state.
        expect(find.text('Backing up...'), findsOneWidget);
        expect(find.text('Back Up Now'), findsNothing);

        // The job completes (even if the user never watched): durable status.
        service.gate!.complete();
        await tester.pumpAndSettle();

        expect(find.text('Cloud backup complete.'), findsOneWidget);
        expect(find.text('Back Up Now'), findsOneWidget);

        // The job row carries the durable verdict.
        final latest = await harness.db.select(harness.db.jobs).getSingle();
        expect(latest.status, 'succeeded');
      },
    );

    testWidgets(
      'permanent backup failure stays visible inline with a retry affordance',
      (tester) async {
        final service = GatedCloudSyncService(db: harness.db);
        service.result = CloudSyncResult(
          success: false,
          error: 'Sign in to your cloud account and try again.',
          errorCode: 'auth',
          timestamp: DateTime.utc(2026, 1, 1),
        );
        await pumpSettings(tester, service: service);
        await signIn(tester);

        await tapBackUpNow(tester);
        await tester.pumpAndSettle();

        // The failure is not swallowed: it stays on screen (durable) with the
        // tap-to-retry hint, and the button is enabled again.
        expect(find.textContaining('to retry'), findsOneWidget);
        expect(find.text('Back Up Now'), findsOneWidget);
        expect(find.text('Backing up...'), findsNothing);
      },
    );
  });
}
