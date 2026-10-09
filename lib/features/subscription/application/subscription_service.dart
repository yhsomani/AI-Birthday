import 'dart:async';
import 'dart:convert';

import 'package:clock/clock.dart' as clock;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:in_app_purchase/in_app_purchase.dart' as iap;
import 'package:uuid/uuid.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';
import 'package:ai_birthday/core/config/firebase_config.dart';

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
    DateTime Function()? now,
    String? verificationEndpoint,
  }) : _store = store,
       _iap = inAppPurchase ?? iap.InAppPurchase.instance,
       _http = httpClient ?? http.Client(),
       _logger = logger ?? ConsoleAppLogger(),
       _authTokenProvider = authTokenProvider,
       _accountBindingProvider = accountBindingProvider,
       _now = now ?? clock.clock.now,
       _verificationEndpoint =
           verificationEndpoint ?? FirebaseConfig.functionUrl('verifyPurchase'),
       super(initial) {
    _initIapAndRestore();
  }

  final SecureStoreDriver? _store;
  final iap.InAppPurchase _iap;
  final http.Client _http;
  final AppLogger _logger;
  final Future<String?> Function()? _authTokenProvider;
  final Future<String?> Function()? _accountBindingProvider;
  final DateTime Function() _now;
  final String _verificationEndpoint;

  StreamSubscription<List<iap.PurchaseDetails>>? _iapSubscription;
  Completer<bool>? _restoreCompleter;
  PurchaseStatus _purchaseStatus = PurchaseStatus.idle;
  PurchaseStatus get purchaseStatus => _purchaseStatus;

  static const String _kProMonthlyId = 'ai_birthday_pro_monthly';
  // MUST match android/app/build.gradle.kts applicationId. The previous value
  // here ('com.yashomani.ai_birthday', one 's') matched the server constant but
  // NOT the installed app, so Google Play's purchases API returned 404 for the
  // real package and every purchase verified as 'none'.
  static const String _kPackageName = 'com.yashsomani.ai_birthday';
  static const String _kBindingPrefix = 'purchase_binding_';

  /// How long an in-session grant survives failed re-verification before the
  /// app stops claiming Pro (audit P1-3). A successful verification resets it.
  static const Duration _kPreserveTtl = Duration(hours: 24);
  static const Duration _kVerifyTimeout = Duration(seconds: 15);

  DateTime? _lastVerifiedAt;

  /// True after a purchase event whose verification failed, while the
  /// purchase is left unacknowledged (so Play re-emits it for retry).
  bool _hasPendingVerification = false;
  bool get hasPendingVerification => _hasPendingVerification;

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

      _VerifiedEntitlement? verified;
      if (purchase.status == iap.PurchaseStatus.purchased ||
          purchase.status == iap.PurchaseStatus.restored) {
        _purchaseStatus = PurchaseStatus.verifying;
        verified = await _verifyPurchase(purchase);

        if (verified != null) {
          _lastVerifiedAt = _now();
          _hasPendingVerification = false;
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
          // Verification could not be completed (network/server). A
          // previously verified grant is preserved only for a bounded
          // window; past that, stop claiming Pro (audit P1-3).
          final preserved =
              state.status.isEntitled &&
              state.canUseAi &&
              _lastVerifiedAt != null &&
              _now().difference(_lastVerifiedAt!) < _kPreserveTtl;
          if (!preserved) state = UserEntitlement.free;
          _hasPendingVerification = true;
          _purchaseStatus = PurchaseStatus.error;
        }
      } else if (purchase.status == iap.PurchaseStatus.error) {
        _purchaseStatus = PurchaseStatus.error;
      } else if (purchase.status == iap.PurchaseStatus.canceled) {
        _purchaseStatus = PurchaseStatus.cancelled;
      }

      // Acknowledge the platform transaction only after a verified success.
      // An unacknowledged purchase stays pending in Google Play and is
      // re-emitted on the next launch/restore, which is the automatic retry
      // path for a transient verification failure (audit P1-1).
      if (purchase.pendingCompletePurchase && verified != null) {
        await _iap.completePurchase(purchase);
      }

      final completer = _restoreCompleter;
      if (completer != null && !completer.isCompleted) {
        // Only a fresh, successful verification counts as "restored";
        // a preserved or timeout state never reports success (audit P3-1).
        completer.complete(verified != null && state.canUseAi);
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
      final response = await _http
          .post(
            Uri.parse(_verificationEndpoint),
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $authToken',
            },
            body: jsonEncode(payload),
          )
          .timeout(_kVerifyTimeout);

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
      // The deployed backend is a Firebase callable, which wraps success in
      // {"result": {...}} (and failure in {"error": {...}}). Accept the
      // legacy {"data": ...} envelope for emulator/proxy variants (P0-1).
      final data = decoded['result'] ?? decoded['data'];
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

  /// Single shared restore pipeline. The claim is fully synchronous so two
  /// overlapping restore attempts (cold-start init + a manual Restore) can
  /// never race two completers: the second caller awaits the first run
  /// (audit P1-2).
  Future<bool>? _restoreRun;

  Future<bool> _restorePurchases() {
    final inFlight = _restoreRun;
    if (inFlight != null) return inFlight;

    final run = _doRestorePurchases();
    _restoreRun = run;
    unawaited(
      run.whenComplete(() {
        if (identical(_restoreRun, run)) _restoreRun = null;
      }),
    );
    return run;
  }

  Future<bool> _doRestorePurchases() async {
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
        // A timeout means nothing was re-verified this run: never report
        // success from stale state (audit P3-1/C6).
        onTimeout: () => false,
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
