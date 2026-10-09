import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:in_app_purchase/in_app_purchase.dart' as iap;
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

class FakeUnavailableIap implements iap.InAppPurchase {
  @override
  Stream<List<iap.PurchaseDetails>> get purchaseStream => const Stream.empty();

  @override
  Future<bool> isAvailable() async => false;

  @override
  Future<iap.ProductDetailsResponse> queryProductDetails(
    Set<String> identifiers,
  ) async {
    return iap.ProductDetailsResponse(
      productDetails: [],
      notFoundIDs: identifiers.toList(),
    );
  }

  @override
  Future<bool> buyNonConsumable({
    required iap.PurchaseParam purchaseParam,
  }) async => false;

  @override
  Future<bool> buyConsumable({
    required iap.PurchaseParam purchaseParam,
    bool autoConsume = true,
  }) async => false;

  @override
  Future<void> completePurchase(iap.PurchaseDetails purchase) async {}

  @override
  Future<void> restorePurchases({String? applicationUserName}) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class CountingIap extends FakeUnavailableIap {
  int availabilityChecks = 0;

  @override
  Future<bool> isAvailable() async {
    availabilityChecks++;
    return true;
  }
}

class FakeEmptyProductsIap extends FakeUnavailableIap {
  @override
  Future<bool> isAvailable() async => true;

  @override
  Future<iap.ProductDetailsResponse> queryProductDetails(
    Set<String> identifiers,
  ) async {
    return iap.ProductDetailsResponse(
      productDetails: [],
      notFoundIDs: identifiers.toList(),
    );
  }
}

void main() {
  group('SubscriptionNotifier Truthfulness Tests (P0)', () {
    test(
      'does NOT grant Pro entitlement when Play Store is unavailable',
      () async {
        final notifier = SubscriptionNotifier(
          inAppPurchase: FakeUnavailableIap(),
        );

        expect(notifier.state, UserEntitlement.free);

        final result = await notifier.purchaseProMonthly();

        expect(result, isFalse);
        expect(notifier.state, UserEntitlement.free);
        expect(notifier.purchaseStatus, PurchaseStatus.idle);
        notifier.dispose();
      },
    );

    test(
      'does NOT grant Pro entitlement when product details are empty',
      () async {
        final notifier = SubscriptionNotifier(
          inAppPurchase: FakeEmptyProductsIap(),
        );

        expect(notifier.state, UserEntitlement.free);

        final result = await notifier.purchaseProMonthly();

        expect(result, isFalse);
        expect(notifier.state, UserEntitlement.free);
        expect(notifier.purchaseStatus, PurchaseStatus.idle);
        notifier.dispose();
      },
    );

    group('Purchase start is refused before Google Play bills', () {
      const endpoint = 'https://example.test/verifyPurchase';

      SubscriptionNotifier build({
        required http.Client client,
        required iap.InAppPurchase iapFake,
        String? token = 'id-token',
      }) {
        return SubscriptionNotifier(
          inAppPurchase: iapFake,
          httpClient: client,
          verificationEndpoint: endpoint,
          authTokenProvider: () async => token,
          accountBindingProvider: () async => 'binding-1',
        );
      }

      test('missing verification function (404) blocks billing', () async {
        final iapFake = CountingIap();
        final notifier = build(
          client: MockClient((_) async => http.Response('not found', 404)),
          iapFake: iapFake,
        );

        // Discard the availability check made by the notifier's own startup.
        await Future<void>.delayed(Duration.zero);
        iapFake.availabilityChecks = 0;
        expect(await notifier.purchaseProMonthly(), isFalse);
        expect(
          notifier.lastPurchaseFailure,
          PurchaseFailure.verificationUnavailable,
        );
        expect(iapFake.availabilityChecks, 0);
        expect(notifier.state, UserEntitlement.free);
        notifier.dispose();
      });

      test('server error on the probe also blocks billing', () async {
        final iapFake = CountingIap();
        final notifier = build(
          client: MockClient((_) async => http.Response('boom', 503)),
          iapFake: iapFake,
        );

        // Discard the availability check made by the notifier's own startup.
        await Future<void>.delayed(Duration.zero);
        iapFake.availabilityChecks = 0;
        expect(await notifier.purchaseProMonthly(), isFalse);
        expect(
          notifier.lastPurchaseFailure,
          PurchaseFailure.verificationUnavailable,
        );
        expect(iapFake.availabilityChecks, 0);
        notifier.dispose();
      });

      test(
        'a deployed function with no Play store reports the store',
        () async {
          final notifier = build(
            client: MockClient((_) async => http.Response('{}', 400)),
            iapFake: FakeUnavailableIap(),
          );

          expect(await notifier.purchaseProMonthly(), isFalse);
          expect(
            notifier.lastPurchaseFailure,
            PurchaseFailure.storeUnavailable,
          );
          notifier.dispose();
        },
      );

      test('no signed-in session reports sign-in before any request', () async {
        var requests = 0;
        final notifier = build(
          client: MockClient((_) async {
            requests++;
            return http.Response('{}', 200);
          }),
          iapFake: CountingIap(),
          token: null,
        );

        expect(await notifier.purchaseProMonthly(), isFalse);
        expect(notifier.lastPurchaseFailure, PurchaseFailure.signInRequired);
        expect(requests, 0);
        notifier.dispose();
      });
    });
  });
}
