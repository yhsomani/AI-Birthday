import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:in_app_purchase/in_app_purchase.dart' as iap;

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';

class _Store implements SecureStoreDriver {
  final Map<String, String> data = {};
  bool failWrites = false;
  @override
  Future<void> delete(String key) async => data.remove(key);
  @override
  Future<String?> read(String key) async => data[key];
  @override
  Future<void> write(String key, String value) async {
    if (failWrites) throw StateError('secure storage unavailable');
    data[key] = value;
  }
}

class _FakeIap implements iap.InAppPurchase {
  final _controller = StreamController<List<iap.PurchaseDetails>>();
  int buyCalls = 0;
  int restoreCalls = 0;
  int completePurchaseCalls = 0;
  String? lastApplicationUserName;
  final restoreApplicationUserNames = <String?>[];
  Completer<void>? restoreGate;
  bool buyResult = true;

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
  Future<bool> buyNonConsumable({
    required iap.PurchaseParam purchaseParam,
  }) async {
    buyCalls++;
    lastApplicationUserName = purchaseParam.applicationUserName;
    return buyResult;
  }

  @override
  Future<void> restorePurchases({String? applicationUserName}) async {
    restoreCalls++;
    restoreApplicationUserNames.add(applicationUserName);
    await restoreGate?.future;
  }

  void emit(iap.PurchaseDetails purchase) => _controller.add([purchase]);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

iap.PurchaseDetails _purchase(iap.PurchaseStatus status) => iap.PurchaseDetails(
  purchaseID: 'pid',
  productID: 'ai_birthday_pro_monthly',
  verificationData: iap.PurchaseVerificationData(
    localVerificationData: 'local',
    serverVerificationData: 'server-token',
    source: 'google_play',
  ),
  transactionDate: DateTime.now().millisecondsSinceEpoch.toString(),
  status: status,
)..pendingCompletePurchase = true;

String _active() => jsonEncode({
  'result': {
    'status': 'active',
    'productId': 'ai_birthday_pro_monthly',
    'expiryDateMs': DateTime.now()
        .add(const Duration(days: 30))
        .millisecondsSinceEpoch,
    'isAutoRenewing': true,
  },
});

Future<void> _settle() =>
    Future<void>.delayed(const Duration(milliseconds: 30));

SubscriptionNotifier _build(
  _FakeIap fake,
  http.Client client, {
  String? appCheck = 'app-check',
  String? token = 'id-token',
  Future<String?> Function()? tokenProvider,
  Future<String?> Function()? bindingProvider,
  Future<String?> Function()? accountIdProvider,
  SecureStoreDriver? store,
  AppLogger? logger,
  Duration restoreTimeout = const Duration(milliseconds: 100),
  Duration restoreHandoffTimeout = const Duration(seconds: 11),
}) => SubscriptionNotifier(
  store: store,
  logger: logger,
  inAppPurchase: fake,
  httpClient: client,
  verificationEndpoint: 'https://example.test/verifyPurchase',
  authTokenProvider: tokenProvider ?? () async => token,
  accountBindingProvider:
      bindingProvider ?? () async => 'binding-1234567890abcdef',
  accountIdProvider: accountIdProvider ?? () async => 'account-a',
  appCheckTokenProvider: appCheck == null ? null : () async => appCheck,
  restoreTimeout: restoreTimeout,
  restoreHandoffTimeout: restoreHandoffTimeout,
);

void main() {
  group('account purchase binding (A3-001)', () {
    final serverFormat = RegExp(r'^[A-Za-z0-9._-]{16,128}$');

    test('deterministicBinding is the documented SHA-256 hex', () {
      expect(
        SubscriptionNotifier.deterministicBinding('uid-123'),
        '06e9cd01f3469459112edfafd27b317b0f6243b8d6155ab233a7dcd3aa60170e',
      );
      final binding = SubscriptionNotifier.deterministicBinding('uid-123');
      expect(binding, hasLength(64));
      expect(serverFormat.hasMatch(binding), isTrue);
      expect(
        SubscriptionNotifier.deterministicBinding('uid-124'),
        isNot(binding),
      );
    });

    test('a fresh install derives the same binding as the previous install, '
        'without touching storage', () async {
      final first = await SubscriptionNotifier.readBindingFromStore(
        _Store(),
        'google-sub',
        firebaseUid: 'uid-123',
      );
      final afterWipe = await SubscriptionNotifier.readBindingFromStore(
        _Store(), // wiped secure storage (reinstall / new phone)
        'google-sub',
        firebaseUid: 'uid-123',
      );
      expect(first, afterWipe);
      expect(first, SubscriptionNotifier.deterministicBinding('uid-123'));
    });

    test('a legacy stored UUID binding keeps being sent', () async {
      final store = _Store()
        ..data['purchase_binding_google-sub'] =
            '0123456789abcdef0123456789abcdef';
      final binding = await SubscriptionNotifier.readBindingFromStore(
        store,
        'google-sub',
        firebaseUid: 'uid-123',
      );
      expect(binding, '0123456789abcdef0123456789abcdef');
      expect(serverFormat.hasMatch(binding!), isTrue);
    });

    test('no uid and no legacy binding means sign-in is required', () async {
      expect(
        await SubscriptionNotifier.readBindingFromStore(_Store(), 'google-sub'),
        isNull,
      );
    });
  });

  group('purchase flow guards and state (A3-005/006/007)', () {
    test('missing App Check token refuses before the Play sheet or any '
        'HTTP call (A3-005)', () async {
      final fake = _FakeIap();
      final notifier = _build(
        fake,
        MockClient((_) async => fail('no HTTP without App Check')),
        appCheck: null,
      );
      await notifier.initializationComplete;

      expect(await notifier.purchaseProMonthly(), isFalse);
      expect(fake.buyCalls, 0);
      expect(
        notifier.purchaseUiState.value.failure,
        PurchaseFailure.verificationUnavailable,
      );
      notifier.dispose();
    });

    test('empty App Check token is treated as missing (A3-005)', () async {
      final fake = _FakeIap();
      final notifier = _build(
        fake,
        MockClient((_) async => fail('no HTTP without App Check')),
        appCheck: '',
      );
      await notifier.initializationComplete;
      expect(await notifier.purchaseProMonthly(), isFalse);
      expect(fake.buyCalls, 0);
      notifier.dispose();
    });

    test(
      'price comes from ProductDetails, not a hard-coded value (A3-006)',
      () async {
        final fake = _FakeIap();
        final notifier = _build(
          fake,
          MockClient((_) async => http.Response('{}', 400)),
        );
        await notifier.initializationComplete;
        expect(notifier.purchaseUiState.value.productPrice, '₹249.00');
        notifier.dispose();
      },
    );

    test('purchase -> purchasing -> verifying -> success, with the binding '
        'passed to Play (A3-007)', () async {
      final fake = _FakeIap();
      final verifyGate = Completer<http.Response>();
      var probed = false;
      final notifier = _build(
        fake,
        MockClient((request) async {
          if (!probed) {
            probed = true;
            return http.Response('{}', 400); // reachability probe
          }
          return verifyGate.future;
        }),
      );
      await notifier.initializationComplete;
      final states = <PurchaseStatus>[];
      notifier.purchaseUiState.addListener(
        () => states.add(notifier.purchaseUiState.value.status),
      );

      expect(await notifier.purchaseProMonthly(), isTrue);
      expect(fake.lastApplicationUserName, 'binding-1234567890abcdef');
      // Play sheet opened: the UI is still busy (not reset on return).
      expect(notifier.purchaseUiState.value.status, PurchaseStatus.purchasing);
      expect(notifier.purchaseUiState.value.isBusy, isTrue);

      fake.emit(_purchase(iap.PurchaseStatus.purchased));
      await _settle();
      expect(notifier.purchaseUiState.value.status, PurchaseStatus.verifying);

      verifyGate.complete(http.Response(_active(), 200));
      await _settle();
      expect(notifier.purchaseUiState.value.status, PurchaseStatus.success);
      expect(notifier.state.canUseAi, isTrue);
      expect(
        states,
        containsAllInOrder([PurchaseStatus.verifying, PurchaseStatus.success]),
      );

      notifier.clearPurchaseResult();
      expect(notifier.purchaseUiState.value.status, PurchaseStatus.idle);
      notifier.dispose();
    });

    test('verification failure surfaces an error state (A3-007)', () async {
      final fake = _FakeIap();
      final notifier = _build(
        fake,
        MockClient((_) async => http.Response('boom', 500)),
      );
      await notifier.initializationComplete;

      fake.emit(_purchase(iap.PurchaseStatus.purchased));
      await _settle();

      final ui = notifier.purchaseUiState.value;
      expect(ui.status, PurchaseStatus.error);
      expect(ui.failure, PurchaseFailure.verificationFailed);
      expect(ui.hasPendingVerification, isTrue);
      expect(notifier.state.canUseAi, isFalse);
      notifier.dispose();
    });

    test(
      'verified purchase is acknowledged when entitlement caching fails',
      () async {
        final fake = _FakeIap();
        final store = _Store()..failWrites = true;
        final logger = RecordingLogger();
        final notifier = _build(
          fake,
          MockClient((_) async => http.Response(_active(), 200)),
          store: store,
          logger: logger,
          restoreTimeout: const Duration(milliseconds: 10),
        );
        await notifier.initializationComplete;

        fake.emit(_purchase(iap.PurchaseStatus.purchased));
        await _settle();

        expect(fake.completePurchaseCalls, 1);
        expect(notifier.state.canUseAi, isTrue);
        expect(notifier.purchaseUiState.value.status, PurchaseStatus.success);
        expect(
          logger.records.any(
            (record) =>
                record.message.contains('could not be cached') &&
                record.error is StateError &&
                record.errorCategory == 'entitlement_cache_write',
          ),
          isTrue,
        );
        notifier.dispose();
      },
    );

    test(
      'account entitlement reload requests a fresh Play restore (A3-024)',
      () async {
        final fake = _FakeIap();
        final notifier = _build(
          fake,
          MockClient((_) async => http.Response('{}', 400)),
          restoreTimeout: const Duration(milliseconds: 10),
        );
        await notifier.initializationComplete;
        final initialRestoreCalls = fake.restoreCalls;

        await notifier.reloadAccountEntitlement();

        expect(fake.restoreCalls, greaterThan(initialRestoreCalls));
        notifier.dispose();
      },
    );

    test('cancelled purchase is observable (A3-007)', () async {
      final fake = _FakeIap();
      final notifier = _build(
        fake,
        MockClient((_) async => http.Response('{}', 400)),
      );
      await notifier.initializationComplete;

      fake.emit(_purchase(iap.PurchaseStatus.canceled));
      await _settle();
      expect(notifier.purchaseUiState.value.status, PurchaseStatus.cancelled);
      notifier.dispose();
    });

    test('Play billing error is observable (A3-007)', () async {
      final fake = _FakeIap();
      final notifier = _build(
        fake,
        MockClient((_) async => http.Response('{}', 400)),
      );
      await notifier.initializationComplete;

      fake.emit(_purchase(iap.PurchaseStatus.error));
      await _settle();
      final ui = notifier.purchaseUiState.value;
      expect(ui.status, PurchaseStatus.error);
      expect(ui.failure, PurchaseFailure.purchaseError);
      notifier.dispose();
    });
  });

  group('Restore Purchases reports a visible outcome (A3-001/A3-007)', () {
    test('fresh install restore verifies and reports restored', () async {
      final fake = _FakeIap();
      final notifier = _build(
        fake,
        MockClient((_) async => http.Response(_active(), 200)),
        restoreTimeout: const Duration(milliseconds: 80),
        restoreHandoffTimeout: const Duration(milliseconds: 300),
      );
      final restore = notifier.restorePurchasesDetailed();
      await _settle();
      expect(notifier.purchaseUiState.value.isRestoring, isTrue);
      fake.emit(_purchase(iap.PurchaseStatus.restored));
      expect(await restore, RestoreOutcome.restored);
      await _settle();
      expect(notifier.purchaseUiState.value.isRestoring, isFalse);
      expect(notifier.state.canUseAi, isTrue);
      notifier.dispose();
    });

    test('server rejection is reported as verificationFailed, not "none '
        'found"', () async {
      final fake = _FakeIap();
      final notifier = _build(
        fake,
        MockClient((_) async => http.Response('denied', 403)),
        restoreTimeout: const Duration(seconds: 3),
      );
      final restore = notifier.restorePurchasesDetailed();
      await _settle();
      fake.emit(_purchase(iap.PurchaseStatus.restored));
      expect(await restore, RestoreOutcome.verificationFailed);
      notifier.dispose();
    });

    test('nothing to restore reports notFound', () async {
      final notifier = _build(
        _FakeIap(),
        MockClient((_) async => http.Response('{}', 400)),
        restoreTimeout: const Duration(milliseconds: 50),
      );
      expect(
        await notifier.restorePurchasesDetailed(),
        RestoreOutcome.notFound,
      );
      notifier.dispose();
    });

    test('signed-out restore reports signInRequired', () async {
      final notifier = _build(
        _FakeIap(),
        MockClient((_) async => http.Response('{}', 400)),
        token: null,
      );
      expect(
        await notifier.restorePurchasesDetailed(),
        RestoreOutcome.signInRequired,
      );
      notifier.dispose();
    });

    test(
      'late restore verification cannot grant or persist after account switch',
      () async {
        final fake = _FakeIap();
        final store = _Store();
        final verifyGate = Completer<http.Response>();
        var accountId = 'account-a';
        String? token;
        final notifier = _build(
          fake,
          MockClient((_) => verifyGate.future),
          tokenProvider: () async => token,
          accountIdProvider: () async => accountId,
          store: store,
          restoreTimeout: const Duration(seconds: 3),
        );
        await notifier.initializationComplete;
        token = 'token-a';

        final restore = notifier.restorePurchasesDetailed();
        await _settle();
        fake.emit(_purchase(iap.PurchaseStatus.restored));
        await _settle();

        accountId = 'account-b';
        final restoreB = notifier.restorePurchasesDetailed();
        await _settle();
        verifyGate.complete(http.Response(_active(), 200));
        await _settle();

        expect(await restoreB, RestoreOutcome.notFound);
        expect(await restore, RestoreOutcome.signInRequired);
        expect(notifier.state.canUseAi, isFalse);
        expect(store.data, isEmpty);
        notifier.dispose();
      },
    );

    test('a different account never joins an in-flight restore run', () async {
      final fake = _FakeIap();
      var accountId = 'account-a';
      var binding = 'binding-account-a';
      String? token;
      final notifier = _build(
        fake,
        MockClient((_) async => http.Response('{}', 400)),
        tokenProvider: () async => token,
        bindingProvider: () async => binding,
        accountIdProvider: () async => accountId,
        restoreTimeout: const Duration(milliseconds: 40),
        restoreHandoffTimeout: const Duration(milliseconds: 300),
      );
      await notifier.initializationComplete;
      token = 'token-a';

      final restoreA = notifier.restorePurchasesDetailed();
      await _settle();
      expect(fake.restoreCalls, 1);

      accountId = 'account-b';
      binding = 'binding-account-b';
      token = 'token-b';
      final restoreB = notifier.restorePurchasesDetailed();
      expect(await restoreA, RestoreOutcome.signInRequired);
      await _settle();
      expect(fake.restoreCalls, 2);
      expect(fake.restoreApplicationUserNames, [
        'binding-account-a',
        'binding-account-b',
      ]);
      expect(await restoreB, RestoreOutcome.notFound);
      notifier.dispose();
    });

    test(
      'queued account restore fails after its bounded handoff deadline',
      () async {
        final fake = _FakeIap()..restoreGate = Completer<void>();
        var accountId = 'account-a';
        String? token;
        final notifier = _build(
          fake,
          MockClient((_) async => http.Response('{}', 400)),
          tokenProvider: () async => token,
          accountIdProvider: () async => accountId,
          restoreTimeout: const Duration(milliseconds: 100),
          restoreHandoffTimeout: const Duration(milliseconds: 20),
        );
        await notifier.initializationComplete;
        token = 'token-a';

        final restoreA = notifier.restorePurchasesDetailed();
        await _settle();
        accountId = 'account-b';
        token = 'token-b';

        expect(await notifier.purchaseProMonthly(), isFalse);
        expect(fake.buyCalls, 0);
        expect(await notifier.restorePurchasesDetailed(), RestoreOutcome.error);
        expect(fake.restoreCalls, 1);

        fake.restoreGate!.complete();
        expect(await restoreA, RestoreOutcome.signInRequired);
        notifier.dispose();
      },
    );
  });
}
