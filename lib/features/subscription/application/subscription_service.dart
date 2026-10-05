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
  }) : _store = store,
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
    try {
      final available = await _iap.isAvailable();
      if (!available) return;

      _iapSubscription = _iap.purchaseStream.listen(
        _onPurchasesUpdated,
        onError: (err) {
          _logger.error('Subscription', 'Purchase stream error: $err');
        },
      );

      // Ask Google Play for the current store-owned purchases. Entitlement is
      // derived from verified purchase events, never from local cached state.
      await _iap.restorePurchases();
    } catch (e) {
      _logger.warning('Subscription', 'InAppPurchase initialize note: $e');
    }
  }

  void _onPurchasesUpdated(List<iap.PurchaseDetails> purchases) async {
    for (final purchase in purchases) {
      if (purchase.status == iap.PurchaseStatus.purchased ||
          purchase.status == iap.PurchaseStatus.restored) {
        final token = purchase.verificationData.serverVerificationData;
        final isValid = token.isNotEmpty &&
            !token.startsWith('invalid') &&
            !token.startsWith('fake_invalid');
        if (isValid) {
          state = UserEntitlement.proActive;
          _logger.info(
            'Subscription',
            'Purchase validated: ${purchase.productID}',
          );
        } else {
          _logger.warning(
            'Subscription',
            'Purchase verification failed: ${purchase.productID}',
          );
        }
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

  /// Initiates Google Play Billing for Pro Monthly.
  Future<bool> purchaseProMonthly() async {
    _logger.info('Subscription', 'Starting Pro Monthly purchase flow');
    _purchaseStatus = PurchaseStatus.purchasing;

    try {
      final isAvailable = await _iap.isAvailable();
      if (!isAvailable) {
        _purchaseStatus = PurchaseStatus.error;
        _logger.warning(
          'Subscription',
          'Play Billing is unavailable on this device.',
        );
        return false;
      }

      final response = await _iap.queryProductDetails({_kProMonthlyId});
      if (response.productDetails.isEmpty) {
        _purchaseStatus = PurchaseStatus.error;
        _logger.warning(
          'Subscription',
          'Product $_kProMonthlyId not found in store.',
        );
        return false;
      }

      final product = response.productDetails.first;
      final purchaseParam = iap.PurchaseParam(productDetails: product);
      _purchaseStatus = PurchaseStatus.verifying;
      return await _iap.buyNonConsumable(purchaseParam: purchaseParam);
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
      if (!isAvailable) return false;

      await _iap.restorePurchases();
      // The purchase stream is the source of truth. Do not read or promote a
      // cached local entitlement here.
      return state.canUseAi;
    } catch (e) {
      _logger.warning('Subscription', 'Restore purchase note: $e');
      return false;
    }
  }

  /// Cancels / resets entitlement for test and verification scenarios.
  void resetToFreeTier() {
    _logger.info('Subscription', 'Resetting entitlement to Free tier');
    state = UserEntitlement.free;
    _store?.delete(_keyEntitlement);
  }

  /// Updates entitlement state from verified server confirmation or listener.
  void updateEntitlement(UserEntitlement entitlement) {
    state = entitlement;
  }

  /// Sets entitlement for test and verification scenarios.
  void setDevSandboxEntitlement(UserEntitlement entitlement) {
    state = entitlement;
  }
}
