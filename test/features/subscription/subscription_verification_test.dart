import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:in_app_purchase/in_app_purchase.dart' as iap;

import 'package:ai_birthday/features/subscription/application/subscription_service.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

/// IAP double that reports IAP available and lets tests push purchase events
/// (non-broadcast so events buffered before the listener attaches are kept).
class FakePurchasingIap implements iap.InAppPurchase {
  final StreamController<List<iap.PurchaseDetails>> _controller =
      StreamController<List<iap.PurchaseDetails>>();

  int completePurchaseCalls = 0;

  @override
  Stream<List<iap.PurchaseDetails>> get purchaseStream => _controller.stream;

  @override
  Future<bool> isAvailable() async => true;

  @override
  Future<void> completePurchase(iap.PurchaseDetails purchase) async {
    completePurchaseCalls++;
  }

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
  }) async => true;

  @override
  Future<bool> buyConsumable({
    required iap.PurchaseParam purchaseParam,
    bool autoConsume = true,
  }) async => true;

  @override
  Future<void> restorePurchases({String? applicationUserName}) async {}

  void emit(iap.PurchaseDetails purchase) => _controller.add([purchase]);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

iap.PurchaseDetails purchasedPro() {
  final now = DateTime.now();
  return iap.PurchaseDetails(
    purchaseID: 'pid-1',
    productID: 'ai_birthday_pro_monthly',
    verificationData: iap.PurchaseVerificationData(
      localVerificationData: 'local-token',
      serverVerificationData: 'server-token',
      source: 'google_play',
    ),
    transactionDate: now.millisecondsSinceEpoch.toString(),
    status: iap.PurchaseStatus.purchased,
  )..pendingCompletePurchase = true;
}

String activeResult(DateTime expiry) => jsonEncode({
  'result': {
    'status': 'active',
    'productId': 'ai_birthday_pro_monthly',
    'expiryDateMs': expiry.millisecondsSinceEpoch,
    'isAutoRenewing': true,
  },
});

SubscriptionNotifier buildNotifier(
  FakePurchasingIap iap,
  http.Client client, {
  required DateTime Function() now,
}) {
  return SubscriptionNotifier(
    inAppPurchase: iap,
    httpClient: client,
    authTokenProvider: () async => 'id-token',
    accountBindingProvider: () async => 'binding-1234567890abcdef',
    now: now,
  );
}

/// Flushes the microtask chain (constructor init, verify, state update).
Future<void> settle() => Future<void>.delayed(const Duration(milliseconds: 20));

void main() {
  group('verifyPurchase wire contract (P0-1)', () {
    test('callable {"result": ...} envelope grants active entitlement and '
        'acknowledges the purchase', () async {
      final iap = FakePurchasingIap();
      var calls = 0;
      final client = MockClient((request) async {
        calls++;
        expect(request.url.toString(), contains('verifyPurchase'));
        expect(request.headers['Authorization'], 'Bearer id-token');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final data = body['data'] as Map<String, dynamic>;
        expect(data['purchaseToken'], 'server-token');
        expect(data['packageName'], 'com.yashsomani.ai_birthday');
        expect(data['productId'], 'ai_birthday_pro_monthly');
        return http.Response(
          activeResult(DateTime.now().add(const Duration(days: 30))),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      });

      final notifier = buildNotifier(iap, client, now: () => DateTime.now());
      expect(notifier.state.status, EntitlementStatus.none);

      iap.emit(purchasedPro());
      await settle();

      expect(notifier.state.status, EntitlementStatus.active);
      expect(notifier.state.canUseAi, isTrue);
      expect(notifier.hasPendingVerification, isFalse);
      expect(iap.completePurchaseCalls, 1);
      expect(calls, 1);
      notifier.dispose();
    });

    test('HTTP 500 leaves the entitlement locked and does NOT acknowledge the '
        'purchase', () async {
      final iap = FakePurchasingIap();
      final client = MockClient(
        (request) async => http.Response('Internal Server Error', 500),
      );

      final notifier = buildNotifier(iap, client, now: () => DateTime.now());

      iap.emit(purchasedPro());
      await settle();

      expect(notifier.state.status, EntitlementStatus.none);
      expect(notifier.state.canUseAi, isFalse);
      expect(notifier.hasPendingVerification, isTrue);
      // Unacknowledged: the purchase stays pending in Play for retry.
      expect(iap.completePurchaseCalls, 0);
      notifier.dispose();
    });

    test(
      '{"error": ...} envelope is treated as a verification failure',
      () async {
        final iap = FakePurchasingIap();
        final client = MockClient(
          (request) async => http.Response(
            jsonEncode({
              'error': {'status': 'FAILED_PRECONDITION', 'message': 'nope'},
            }),
            200,
          ),
        );

        final notifier = buildNotifier(iap, client, now: () => DateTime.now());

        iap.emit(purchasedPro());
        await settle();

        expect(notifier.state.canUseAi, isFalse);
        expect(notifier.hasPendingVerification, isTrue);
        expect(iap.completePurchaseCalls, 0);
        notifier.dispose();
      },
    );
  });

  group('charged-but-locked retry (P1-1 / AC-2)', () {
    test('a transient failure keeps the purchase unacknowledged, and the '
        're-emitted event then unlocks AI', () async {
      final iap = FakePurchasingIap();
      var fail = true;
      final client = MockClient((request) async {
        if (fail) return http.Response('boom', 500);
        return http.Response(
          activeResult(DateTime.now().add(const Duration(days: 30))),
          200,
        );
      });

      final notifier = buildNotifier(iap, client, now: () => DateTime.now());

      iap.emit(purchasedPro()); // first attempt: transient failure
      await settle();
      expect(notifier.state.canUseAi, isFalse);
      expect(notifier.hasPendingVerification, isTrue);
      expect(iap.completePurchaseCalls, 0);

      fail = false;
      iap.emit(purchasedPro()); // Play re-emits the unacknowledged purchase
      await settle();

      expect(notifier.state.status, EntitlementStatus.active);
      expect(notifier.state.canUseAi, isTrue);
      expect(notifier.hasPendingVerification, isFalse);
      expect(iap.completePurchaseCalls, 1);
      notifier.dispose();
    });
  });

  group('stale-grant TTL (P1-3 / AC-4)', () {
    test(
      'a preserved grant is kept within the TTL and de-grades after it',
      () async {
        final iap = FakePurchasingIap();
        final base = DateTime.utc(2026, 1, 1, 12);
        var nowValue = base;
        var fail = true;
        // The verify call is gated on REAL now, so the granted expiry must
        // stay in the real future; the TTL itself is driven by injected `now`.
        final grantedExpiry = DateTime.now().add(const Duration(days: 90));
        final client = MockClient((request) async {
          if (fail) return http.Response('boom', 500);
          return http.Response(activeResult(grantedExpiry), 200);
        });

        final notifier = buildNotifier(iap, client, now: () => nowValue);

        // 1. Verified grant at t0.
        fail = false;
        iap.emit(purchasedPro());
        await settle();
        expect(notifier.state.status, EntitlementStatus.active);

        // 2. Failed re-verification within the 24h TTL: grant preserved.
        fail = true;
        nowValue = base.add(const Duration(hours: 1));
        iap.emit(purchasedPro());
        await settle();
        expect(notifier.state.status, EntitlementStatus.active);
        expect(notifier.state.canUseAi, isTrue);

        // 3. Failed re-verification after the TTL: stop claiming Pro.
        nowValue = base.add(const Duration(hours: 25));
        iap.emit(purchasedPro());
        await settle();
        expect(notifier.state.status, EntitlementStatus.none);
        expect(notifier.state.canUseAi, isFalse);
        expect(notifier.hasPendingVerification, isTrue);
        notifier.dispose();
      },
    );

    test(
      'restore reports success only after a fresh verified event, never from '
      'stale state',
      () async {
        final iap = FakePurchasingIap();
        final client = MockClient((request) async {
          return http.Response(
            jsonEncode({
              'result': {
                'status': 'active',
                'productId': 'ai_birthday_pro_monthly',
                'expiryDateMs': DateTime.now()
                    .add(const Duration(days: 30))
                    .millisecondsSinceEpoch,
                'isAutoRenewing': true,
              },
            }),
            200,
          );
        });

        final notifier = buildNotifier(iap, client, now: () => DateTime.now());

        final restoreFuture = notifier.restorePurchases();
        // Let restore reach its pending completer before emitting, so the
        // verified event completes it (avoiding the 10s-timeout race).
        await settle();
        iap.emit(purchasedPro());
        expect(await restoreFuture, isTrue);
        expect(notifier.state.canUseAi, isTrue);
        notifier.dispose();
      },
    );
  });
}
