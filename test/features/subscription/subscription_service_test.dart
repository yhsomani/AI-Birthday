import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_purchase/in_app_purchase.dart' as iap;
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

class FakeUnavailableIap implements iap.InAppPurchase {
  @override
  Stream<List<iap.PurchaseDetails>> get purchaseStream => const Stream.empty();

  @override
  Future<bool> isAvailable() async => false;

  @override
  Future<iap.ProductDetailsResponse> queryProductDetails(Set<String> identifiers) async {
    return iap.ProductDetailsResponse(
      productDetails: [],
      notFoundIDs: identifiers.toList(),
    );
  }

  @override
  Future<bool> buyNonConsumable({required iap.PurchaseParam purchaseParam}) async => false;

  @override
  Future<bool> buyConsumable({required iap.PurchaseParam purchaseParam, bool autoConsume = true}) async => false;

  @override
  Future<void> completePurchase(iap.PurchaseDetails purchase) async {}

  @override
  Future<void> restorePurchases({String? applicationUserName}) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class FakeEmptyProductsIap extends FakeUnavailableIap {
  @override
  Future<bool> isAvailable() async => true;

  @override
  Future<iap.ProductDetailsResponse> queryProductDetails(Set<String> identifiers) async {
    return iap.ProductDetailsResponse(
      productDetails: [],
      notFoundIDs: identifiers.toList(),
    );
  }
}

void main() {
  group('SubscriptionNotifier Truthfulness Tests (P0)', () {
    test('does NOT grant Pro entitlement when Play Store is unavailable', () async {
      final notifier = SubscriptionNotifier(
        inAppPurchase: FakeUnavailableIap(),
      );

      expect(notifier.state, UserEntitlement.free);

      final result = await notifier.purchaseProMonthly();

      expect(result, isFalse);
      expect(notifier.state, UserEntitlement.free);
      expect(notifier.purchaseStatus, PurchaseStatus.idle);
      notifier.dispose();
    });

    test('does NOT grant Pro entitlement when product details are empty', () async {
      final notifier = SubscriptionNotifier(
        inAppPurchase: FakeEmptyProductsIap(),
      );

      expect(notifier.state, UserEntitlement.free);

      final result = await notifier.purchaseProMonthly();

      expect(result, isFalse);
      expect(notifier.state, UserEntitlement.free);
      expect(notifier.purchaseStatus, PurchaseStatus.idle);
      notifier.dispose();
    });
  });
}
