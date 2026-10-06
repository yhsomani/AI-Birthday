import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:in_app_purchase/in_app_purchase.dart' as iap;
import 'package:uuid/uuid.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

/// State of an in-flight purchase operation.
enum PurchaseStatus { idle, purchasing, verifying, success, cancelled, error }

class _VerifiedEntitlement {
  const _VerifiedEntitlement({
    required this.status,
    required this.productId,
    required this.expiryDateMs,
    required this.isAutoRenewing,
  });

  final EntitlementStatus status;
  final String productId;
  final int expiryDateMs;
  final bool isAutoRenewing;
}

/// Purchase/entitlement manager.
///
/// A Play purchase never unlocks AI merely because the billing client returned
/// a non-empty token. The token is sent to the authenticated server endpoint,
/// which checks the Google Play Developer API and returns the verified state.
class SubscriptionNotifier extends StateNotifier<UserEntitlement> {
  SubscriptionNotifier({
    UserEntitlement initial = UserEntitlement.free,
    SecureStoreDriver? store,
    iap.InAppPurchase? inAppPurchase,
    http.Client? httpClient,
    AppLogger? logger,
    Future<String?> Function()? authTokenProvider,
    Future<String?> Function()? accountBindingProvider,
    String verificationEndpoint =
        'https://asia-south1-relateai-birthday-ysomani.cloudfunctions.net/verifyPurchase',
  }) : _store = store,
       _iap = inAppPurchase ?? iap.InAppPurchase.instance,
       _http = httpClient ?? http.Client(),
       _logger = logger ?? ConsoleAppLogger(),
       _authTokenProvider = authTokenProvider,
       _accountBindingProvider = accountBindingProvider,
       _verificationEndpoint = verificationEndpoint,
       super(initial) {
    _initIapAndRestore();
  }

  final SecureStoreDriver? _store;
  final iap.InAppPurchase _iap;
  final http.Client _http;
  final AppLogger _logger;
  final Future<String?> Function()? _authTokenProvider;
  final Future<String?> Function()? _accountBindingProvider;
  final String _verificationEndpoint;

  StreamSubscription<List<iap.PurchaseDetails>>? _iapSubscription;
  Completer<bool>? _restoreCompleter;
  PurchaseStatus _purchaseStatus = PurchaseStatus.idle;
  PurchaseStatus get purchaseStatus => _purchaseStatus;

  static const String _kProMonthlyId = 'ai_birthday_pro_monthly';
  static const String _kPackageName = 'com.yashomani.ai_birthday';
  static const String _kBindingPrefix = 'purchase_binding_';

  Future<void> _initIapAndRestore() async {
    try {
      final available = await _iap.isAvailable();
      if (!available) return;

      _iapSubscription = _iap.purchaseStream.listen(
        _onPurchasesUpdated,
        onError: (Object error, StackTrace stackTrace) {
          _logger.error(
            'Subscription',
            'Purchase stream unavailable.',
            error: error,
            stackTrace: stackTrace,
          );
        },
      );

      final authToken = await _authTokenProvider?.call();
      if (authToken != null && authToken.isNotEmpty) {
        await _restorePurchases();
      }
    } catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'In-app purchase initialization failed.',
        error: error,
        stackTrace: stackTrace,
      );
    }
  }

  Future<void> _onPurchasesUpdated(List<iap.PurchaseDetails> purchases) async {
    for (final purchase in purchases) {
      if (purchase.productID != _kProMonthlyId) {
        if (purchase.pendingCompletePurchase) {
          await _iap.completePurchase(purchase);
        }
        continue;
      }

      if (purchase.status == iap.PurchaseStatus.purchased ||
          purchase.status == iap.PurchaseStatus.restored) {
        _purchaseStatus = PurchaseStatus.verifying;
        final verified = await _verifyPurchase(purchase);

        if (verified != null) {
          state = UserEntitlement(
            status: verified.status,
            productId: verified.productId,
            expiryDate: DateTime.fromMillisecondsSinceEpoch(
              verified.expiryDateMs,
              isUtc: true,
            ),
            isAutoRenewing: verified.isAutoRenewing,
          );
          _purchaseStatus = PurchaseStatus.success;
        } else {
          // Do not manufacture or preserve a Pro entitlement after an
          // unverified restore/purchase while the app is starting from Free.
          if (state.status != EntitlementStatus.active &&
              state.status != EntitlementStatus.grace) {
            state = UserEntitlement.free;
          }
          _purchaseStatus = PurchaseStatus.error;
        }
      } else if (purchase.status == iap.PurchaseStatus.error) {
        _purchaseStatus = PurchaseStatus.error;
      } else if (purchase.status == iap.PurchaseStatus.canceled) {
        _purchaseStatus = PurchaseStatus.cancelled;
      }

      // completePurchase acknowledges the platform transaction. It is called
      // after verification so the entitlement decision itself is server-backed.
      if (purchase.pendingCompletePurchase) {
        await _iap.completePurchase(purchase);
      }

      final completer = _restoreCompleter;
      if (completer != null && !completer.isCompleted) {
        completer.complete(state.canUseAi);
      }
    }

    _purchaseStatus = PurchaseStatus.idle;
  }

  Future<_VerifiedEntitlement?> _verifyPurchase(
    iap.PurchaseDetails purchase,
  ) async {
    final authToken = await _authTokenProvider?.call();
    final accountBinding = await _accountBindingProvider?.call();

    if (authToken == null ||
        authToken.isEmpty ||
        accountBinding == null ||
        accountBinding.isEmpty) {
      _logger.warning(
        'Subscription',
        'Purchase cannot be verified because the account session is unavailable.',
      );
      return null;
    }

    final payload = <String, dynamic>{
      'data': {
        'contractVersion': 1,
        'purchaseToken': purchase.verificationData.serverVerificationData,
        'productId': purchase.productID,
        'packageName': _kPackageName,
        'accountBinding': accountBinding,
      },
    };

    try {
      final response = await _http.post(
        Uri.parse(_verificationEndpoint),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $authToken',
        },
        body: jsonEncode(payload),
      );

      if (response.statusCode != 200) {
        _logger.warning(
          'Subscription',
          'Server purchase verification was rejected.',
          params: {'statusCode': response.statusCode},
        );
        return null;
      }

      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) return null;
      final data = decoded['data'];
      if (data is! Map<String, dynamic>) return null;

      final status = data['status'];
      final expiryDateMs = data['expiryDateMs'];
      final productId = data['productId'];
      final isAutoRenewing = data['isAutoRenewing'];
      if (status is! String ||
          expiryDateMs is! num ||
          productId is! String ||
          isAutoRenewing is! bool ||
          productId != _kProMonthlyId) {
        return null;
      }

      final expiry = expiryDateMs.toInt();
      final isActive =
          status == 'active' && expiry > DateTime.now().millisecondsSinceEpoch;
      if (isActive) {
        return _VerifiedEntitlement(
          status: EntitlementStatus.active,
          productId: productId,
          expiryDateMs: expiry,
          isAutoRenewing: isAutoRenewing,
        );
      }

      return _VerifiedEntitlement(
        status: status == 'expired'
            ? EntitlementStatus.expired
            : EntitlementStatus.none,
        productId: productId,
        expiryDateMs: expiry,
        isAutoRenewing: isAutoRenewing,
      );
    } catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'Server purchase verification is temporarily unavailable.',
        error: error,
        stackTrace: stackTrace,
      );
      return null;
    }
  }

  @override
  void dispose() {
    _iapSubscription?.cancel();
    _http.close();
    super.dispose();
  }

  /// Starts the Google Play purchase flow. AI is unlocked only after the
  /// purchase stream is subsequently verified by the backend.
  Future<bool> purchaseProMonthly() async {
    _logger.info('Subscription', 'Starting Pro Monthly purchase flow');
    _purchaseStatus = PurchaseStatus.purchasing;

    try {
      final authToken = await _authTokenProvider?.call();
      final accountBinding = await _accountBindingProvider?.call();
      if (authToken == null ||
          authToken.isEmpty ||
          accountBinding == null ||
          accountBinding.isEmpty) {
        _purchaseStatus = PurchaseStatus.error;
        return false;
      }

      final isAvailable = await _iap.isAvailable();
      if (!isAvailable) {
        _purchaseStatus = PurchaseStatus.error;
        return false;
      }

      final response = await _iap.queryProductDetails({_kProMonthlyId});
      if (response.productDetails.isEmpty) {
        _purchaseStatus = PurchaseStatus.error;
        return false;
      }

      final product = response.productDetails.first;
      final purchaseParam = iap.PurchaseParam(
        productDetails: product,
        applicationUserName: accountBinding,
      );
      _purchaseStatus = PurchaseStatus.purchasing;
      return await _iap.buyNonConsumable(purchaseParam: purchaseParam);
    } catch (error, stackTrace) {
      _purchaseStatus = PurchaseStatus.error;
      _logger.error(
        'Subscription',
        'Purchase flow failed.',
        error: error,
        stackTrace: stackTrace,
      );
      return false;
    } finally {
      _purchaseStatus = PurchaseStatus.idle;
    }
  }

  /// Requests store purchase restoration and waits for its verification event.
  Future<bool> restorePurchases() async {
    return _restorePurchases();
  }

  Future<bool> _restorePurchases() async {
    _logger.info('Subscription', 'Checking for restorable purchases');

    try {
      final authToken = await _authTokenProvider?.call();
      final accountBinding = await _accountBindingProvider?.call();
      if (authToken == null ||
          authToken.isEmpty ||
          accountBinding == null ||
          accountBinding.isEmpty) {
        return false;
      }

      final isAvailable = await _iap.isAvailable();
      if (!isAvailable) return false;

      final completer = Completer<bool>();
      _restoreCompleter = completer;
      await _iap.restorePurchases();

      return await completer.future.timeout(
        const Duration(seconds: 10),
        onTimeout: () => state.canUseAi,
      );
    } catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'Restore purchase flow failed.',
        error: error,
        stackTrace: stackTrace,
      );
      return false;
    } finally {
      _restoreCompleter = null;
    }
  }

  /// Test-only hook. Production entitlement still originates from verified
  /// purchase callbacks; no UI should expose this method.
  void resetToFreeTier() {
    _logger.info('Subscription', 'Resetting entitlement to Free tier');
    state = UserEntitlement.free;
    final store = _store;
    if (store != null) {
      unawaited(store.delete('user_subscription_entitlement'));
    }
  }

  /// Test hook for deterministic provider tests.
  void updateEntitlement(UserEntitlement entitlement) {
    state = entitlement;
  }

  /// Test hook for deterministic sandbox tests.
  void setDevSandboxEntitlement(UserEntitlement entitlement) {
    state = entitlement;
  }

  /// Creates a stable non-PII binding used by Google Play's account field.
  static Future<String?> readBindingFromStore(
    SecureStoreDriver? store,
    String googleSubject,
  ) async {
    if (store == null) return null;
    final key = '$_kBindingPrefix$googleSubject';
    final existing = await store.read(key);
    if (existing != null && existing.length >= 16) return existing;

    final generated = const Uuid().v4().replaceAll('-', '');
    await store.write(key, generated);
    return generated;
  }
}
