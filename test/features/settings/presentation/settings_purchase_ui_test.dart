import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_purchase/in_app_purchase.dart' as iap;

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';

import '../../../e2e/harness/fake_store_driver.dart';
import '../../../e2e/harness/test_harness.dart';

class _PricedIap implements iap.InAppPurchase {
  @override
  Stream<List<iap.PurchaseDetails>> get purchaseStream => const Stream.empty();
  @override
  Future<bool> isAvailable() async => true;
  @override
  Future<iap.ProductDetailsResponse> queryProductDetails(
    Set<String> identifiers,
  ) async => iap.ProductDetailsResponse(
    productDetails: [
      iap.ProductDetails(
        id: 'ai_birthday_pro_monthly',
        title: 'Pro',
        description: 'Pro monthly',
        price: '₹249.00',
        rawPrice: 249,
        currencyCode: 'INR',
      ),
    ],
    notFoundIDs: const [],
  );
  @override
  Future<void> restorePurchases({String? applicationUserName}) async {}
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  late E2ETestHarness harness;
  late SubscriptionNotifier notifier;

  setUp(() => harness = E2ETestHarness()..setUp());
  tearDown(() => harness.tearDown());

  Future<void> pumpSettings(WidgetTester tester) async {
    // Built inside the test zone so its async init runs under the fake clock.
    notifier = SubscriptionNotifier(
      store: FakeStoreDriver(),
      inAppPurchase: _PricedIap(),
      logger: const NoopLogger(),
    );
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ...harness.providerOverrides,
          subscriptionNotifierProvider.overrideWith((ref) => notifier),
        ],
        child: const MaterialApp(home: SettingsScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('upgrade button shows the localised Play price (A3-006)', (
    tester,
  ) async {
    await pumpSettings(tester);
    expect(find.textContaining('₹249.00'), findsOneWidget);
    expect(find.textContaining(r'$2.99'), findsNothing);
  });

  testWidgets('restore with no signed-in account shows a visible result '
      '(A3-007)', (tester) async {
    await pumpSettings(tester);
    final restore = find.text('Restore Purchases');
    await tester.ensureVisible(restore);
    await tester.pumpAndSettle();
    await tester.tap(restore);
    await tester.pumpAndSettle();
    expect(
      find.text('Sign in with Google to restore your purchases.'),
      findsOneWidget,
    );
  });

  testWidgets('a failed purchase start reports why via the purchase state '
      '(A3-007)', (tester) async {
    await pumpSettings(tester);
    final upgrade = find.textContaining('Upgrade via Google Play');
    await tester.ensureVisible(upgrade);
    await tester.pumpAndSettle();
    await tester.tap(upgrade);
    await tester.pumpAndSettle();
    expect(
      find.text('Sign in with Google to subscribe to Pro.'),
      findsOneWidget,
    );
    expect(notifier.purchaseUiState.value.status, PurchaseStatus.idle);
  });
}
