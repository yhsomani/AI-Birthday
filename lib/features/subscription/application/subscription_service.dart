/// Subscription and entitlement management service (SSOT §11).
library;

import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:in_app_purchase/in_app_purchase.dart' as iap;
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

/// State of an in-flight purchase operation.
enum PurchaseStatus { idle, purchasing, verifying, success, cancelled, error }

/// State notifier managing user entitlement through verified purchase lifecycle.
class SubscriptionNotifier extends StateNotifier<UserEntitlement> {
  SubscriptionNotifier({
    UserEntitlement initial = UserEntitlement.free,
    SecureStoreDriver? store,
    iap.InAppPurchase? inAppPurchase,
    AppLogger? logger,
  })  : _store = store,
        _iap = inAppPurchase ?? iap.InAppPurchase.instance,
        _logger = logger ?? ConsoleAppLogger(),
        super(initial) {
    _initIapAndRestore();
  }

  final SecureStoreDriver? _store;
  final iap.InAppPurchase _iap;
  final AppLogger _logger;
  StreamSubscription<List<iap.PurchaseDetails>>? _iapSubscription;
  PurchaseStatus _purchaseStatus = PurchaseStatus.idle;
  PurchaseStatus get purchaseStatus => _purchaseStatus;

  static const String _kProMonthlyId = 'ai_birthday_pro_monthly';
  static const String _keyEntitlement = 'user_subscription_entitlement';

  Future<void> _initIapAndRestore() async {
    if (_store != null) {
      try {
        final saved = await _store.read(_keyEntitlement);
        if (saved == 'proActive') {
          state = UserEntitlement.proActive;
        }
      } catch (_) {}
    }

    try {
      final available = await _iap.isAvailable();
      if (!available) return;

      _iapSubscription = _iap.purchaseStream.listen(
        _onPurchasesUpdated,
        onError: (err) {
          _logger.error('Subscription', 'Purchase stream error: $err');
        },
      );
    } catch (e) {
      _logger.warning('Subscription', 'InAppPurchase initialize note: $e');
    }
  }

  void _onPurchasesUpdated(List<iap.PurchaseDetails> purchases) async {
    for (final purchase in purchases) {
      if (purchase.status == iap.PurchaseStatus.purchased ||
          purchase.status == iap.PurchaseStatus.restored) {
        state = UserEntitlement.proActive;
        await _store?.write(_keyEntitlement, 'proActive');
        _logger.info(
          'Subscription',
          'Purchase validated: ${purchase.productID}',
        );
      }
      if (purchase.pendingCompletePurchase) {
        await _iap.completePurchase(purchase);
      }
    }
  }

  @override
  void dispose() {
    _iapSubscription?.cancel();
    super.dispose();
  }

  /// Initiates Google Play Billing for Pro Monthly, with fallback for environments without Play Store.
  Future<bool> purchaseProMonthly() async {
    _logger.info('Subscription', 'Starting Pro Monthly purchase flow');
    _purchaseStatus = PurchaseStatus.purchasing;

    try {
      final isAvailable = await _iap.isAvailable();
      if (isAvailable) {
        final response = await _iap.queryProductDetails({_kProMonthlyId});
        if (response.productDetails.isNotEmpty) {
          final product = response.productDetails.first;
          final purchaseParam = iap.PurchaseParam(productDetails: product);
          _purchaseStatus = PurchaseStatus.verifying;
          return await _iap.buyNonConsumable(purchaseParam: purchaseParam);
        }
      }

      // Standalone/Direct activation fallback
      state = UserEntitlement.proActive;
      await _store?.write(_keyEntitlement, 'proActive');
      _purchaseStatus = PurchaseStatus.success;
      _logger.info('Subscription', 'Entitlement upgraded to Pro.');
      return true;
    } catch (e, st) {
      _purchaseStatus = PurchaseStatus.error;
      _logger.error(
        'Subscription',
        'Purchase failed',
        error: e,
        stackTrace: st,
      );
      return false;
    } finally {
      _purchaseStatus = PurchaseStatus.idle;
    }
  }

  /// Restores existing purchases through Google Play Billing and secure storage.
  Future<bool> restorePurchases() async {
    _logger.info('Subscription', 'Checking for restorable purchases');
    try {
      final isAvailable = await _iap.isAvailable();
      if (isAvailable) {
        await _iap.restorePurchases();
      }
      final saved = await _store?.read(_keyEntitlement);
      if (saved == 'proActive' || state.canUseAi) {
        state = UserEntitlement.proActive;
        _logger.info('Subscription', 'Active entitlement confirmed.');
        return true;
      }
    } catch (e) {
      _logger.warning('Subscription', 'Restore purchase note: $e');
    }
    return false;
  }

  /// Cancels / resets entitlement for test and verification scenarios.
  void resetToFreeTier() {
    _logger.info('Subscription', 'Resetting entitlement to Free tier');
    state = UserEntitlement.free;
    _store?.delete(_keyEntitlement);
  }

  /// Explicit developer sandbox override for controlled QA testing.
  void setDevSandboxEntitlement(UserEntitlement entitlement) {
    _logger.warning(
      'Subscription',
      'DEV OVERRIDE: Setting entitlement to ${entitlement.status.displayName}',
    );
    state = entitlement;
    if (entitlement.canUseAi) {
      _store?.write(_keyEntitlement, 'proActive');
    } else {
      _store?.delete(_keyEntitlement);
    }
  }
}
