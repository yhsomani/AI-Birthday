import 'dart:async';
import 'dart:convert';

import 'package:clock/clock.dart' as clock;
import 'package:flutter/foundation.dart' show kDebugMode;
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

/// Why the last purchase could not be started, so the UI can say what to do.
enum PurchaseFailure {
  none,
  signInRequired,
  storeUnavailable,
  productUnavailable,
  verificationUnavailable,
  unknown,
}

enum EntitlementCheckState {
  checking,
  verified,
  cached,
  stale,
  expired,
  unavailable,
}

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
    Future<String?> Function()? accountIdProvider,
    Future<String?> Function()? appCheckTokenProvider,
    DateTime Function()? now,
    String? verificationEndpoint,
  }) : _store = store,
       _iap = inAppPurchase ?? iap.InAppPurchase.instance,
       _http = httpClient ?? http.Client(),
       _logger = logger ?? ConsoleAppLogger(),
       _authTokenProvider = authTokenProvider,
       _accountBindingProvider = accountBindingProvider,
       _accountIdProvider = accountIdProvider,
       _appCheckTokenProvider = appCheckTokenProvider,
       _now = now ?? clock.clock.now,
       _verificationEndpoint =
           verificationEndpoint ?? FirebaseConfig.functionUrl('verifyPurchase'),
       super(initial) {
    initializationComplete;
  }

  final SecureStoreDriver? _store;
  final iap.InAppPurchase _iap;
  final http.Client _http;
  final AppLogger _logger;
  final Future<String?> Function()? _authTokenProvider;
  final Future<String?> Function()? _accountBindingProvider;
  final Future<String?> Function()? _accountIdProvider;
  final Future<String?> Function()? _appCheckTokenProvider;
  final DateTime Function() _now;
  final String _verificationEndpoint;

  StreamSubscription<List<iap.PurchaseDetails>>? _iapSubscription;
  Timer? _entitlementBoundaryTimer;
  Completer<bool>? _restoreCompleter;
  PurchaseStatus _purchaseStatus = PurchaseStatus.idle;
  PurchaseStatus get purchaseStatus => _purchaseStatus;

  PurchaseFailure _lastPurchaseFailure = PurchaseFailure.none;
  PurchaseFailure get lastPurchaseFailure => _lastPurchaseFailure;

  static const String _kProMonthlyId = 'ai_birthday_pro_monthly';
  // MUST match android/app/build.gradle.kts applicationId. The previous value
  // here ('com.yashomani.ai_birthday', one 's') matched the server constant but
  // NOT the installed app, so Google Play's purchases API returned 404 for the
  // real package and every purchase verified as 'none'.
  static const String _kPackageName = 'com.yashsomani.ai_birthday';
  static const String _kBindingPrefix = 'purchase_binding_';
  static const String _kEntitlementCacheKey = 'verified_entitlement_v1';

  /// How long a server-verified grant survives failed re-verification before the
  /// app stops claiming Pro (audit P1-3). A successful verification resets it.
  static const Duration _kPreserveTtl = Duration(hours: 24);
  static const Duration _kVerifyTimeout = Duration(seconds: 15);

  DateTime? _lastVerifiedAt;
  bool _isCheckingEntitlement = true;
  bool _loadedFromCache = false;
  late final Future<void> initializationComplete = _initIapAndRestore();

  bool get isCheckingEntitlement => _isCheckingEntitlement;

  EntitlementCheckState get checkState {
    if (_isCheckingEntitlement) return EntitlementCheckState.checking;
    if (state.isExpired) return EntitlementCheckState.expired;
    if (state.isVerificationStale) return EntitlementCheckState.stale;
    if (!state.canUseAi) return EntitlementCheckState.unavailable;
    return _loadedFromCache
        ? EntitlementCheckState.cached
        : EntitlementCheckState.verified;
  }

  /// True after a purchase event whose verification failed, while the
  /// purchase is left unacknowledged (so Play re-emits it for retry).
  bool _hasPendingVerification = false;
  bool get hasPendingVerification => _hasPendingVerification;

  Future<void> _initIapAndRestore() async {
    try {
      await _loadCachedEntitlement();
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
    } finally {
      if (mounted) {
        _isCheckingEntitlement = false;
        // Emit a distinct instance so cold-start/no-cache reaches a truthful
        // terminal state even if the entitlement value is still Free.
        state = UserEntitlement(
          status: state.status,
          productId: state.productId,
          expiryDate: state.expiryDate,
          isAutoRenewing: state.isAutoRenewing,
          verifiedAt: state.verifiedAt,
        );
      }
    }
  }

  Future<void> reloadAccountEntitlement() async {
    _isCheckingEntitlement = true;
    _loadedFromCache = false;
    _lastVerifiedAt = null;
    state = UserEntitlement.free;
    await _loadCachedEntitlement();
    _isCheckingEntitlement = false;
    state = UserEntitlement(
      status: state.status,
      productId: state.productId,
      expiryDate: state.expiryDate,
      isAutoRenewing: state.isAutoRenewing,
      verifiedAt: state.verifiedAt,
    );
  }

  Future<void> _loadCachedEntitlement() async {
    final store = _store;
    final accountId = await _accountIdProvider?.call();
    if (store == null || accountId == null || accountId.isEmpty) return;
    final raw = await store.read(_kEntitlementCacheKey);
    if (raw == null) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic> ||
          decoded['accountId'] != accountId) {
        return;
      }
      final verifiedAt = DateTime.tryParse(
        decoded['verifiedAt'] as String? ?? '',
      );
      final expiry = DateTime.tryParse(decoded['expiryDate'] as String? ?? '');
      final statusName = decoded['status'] as String?;
      final status = EntitlementStatus.values
          .where((s) => s.name == statusName)
          .firstOrNull;
      if (verifiedAt == null || status == null) return;
      if (!mounted) return;
      _lastVerifiedAt = verifiedAt;
      _loadedFromCache = true;
      state = UserEntitlement(
        status: status,
        productId: decoded['productId'] as String?,
        expiryDate: expiry,
        isAutoRenewing: decoded['isAutoRenewing'] == true,
        verifiedAt: verifiedAt,
        maxCacheAge: _kPreserveTtl,
      );
      _scheduleEntitlementBoundary(state);
    } catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'Stored entitlement cache was ignored.',
        error: error,
        stackTrace: stackTrace,
      );
    }
  }

  void _scheduleEntitlementBoundary(UserEntitlement entitlement) {
    _entitlementBoundaryTimer?.cancel();
    final verifiedAt = entitlement.verifiedAt;
    if (verifiedAt == null || !entitlement.status.isEntitled) return;
    final staleAt = verifiedAt.add(_kPreserveTtl);
    final expiryAt = entitlement.expiryDate;
    final boundary = expiryAt != null && expiryAt.isBefore(staleAt)
        ? expiryAt
        : staleAt;
    final delay = boundary.difference(_now());
    if (delay.isNegative || delay == Duration.zero) return;
    _entitlementBoundaryTimer = Timer(delay, () {
      if (!mounted) return;
      state = UserEntitlement(
        status: state.status,
        productId: state.productId,
        expiryDate: state.expiryDate,
        isAutoRenewing: state.isAutoRenewing,
        verifiedAt: state.verifiedAt,
        maxCacheAge: _kPreserveTtl,
      );
    });
  }

  Future<void> _persistVerifiedEntitlement(UserEntitlement entitlement) async {
    final store = _store;
    final accountId = await _accountIdProvider?.call();
    if (store == null || accountId == null || accountId.isEmpty) return;
    await store.write(
      _kEntitlementCacheKey,
      jsonEncode({
        'accountId': accountId,
        'status': entitlement.status.name,
        'productId': entitlement.productId,
        'expiryDate': entitlement.expiryDate?.toUtc().toIso8601String(),
        'isAutoRenewing': entitlement.isAutoRenewing,
        'verifiedAt': entitlement.verifiedAt?.toUtc().toIso8601String(),
      }),
    );
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
          final verifiedNow = _now();
          _lastVerifiedAt = verifiedNow;
          _hasPendingVerification = false;
          _loadedFromCache = false;
          final next = UserEntitlement(
            status: verified.status,
            productId: verified.productId,
            expiryDate: DateTime.fromMillisecondsSinceEpoch(
              verified.expiryDateMs,
              isUtc: true,
            ),
            isAutoRenewing: verified.isAutoRenewing,
            verifiedAt: verifiedNow,
            maxCacheAge: _kPreserveTtl,
          );
          state = next;
          _scheduleEntitlementBoundary(next);
          unawaited(_persistVerifiedEntitlement(next));
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
      final appCheckToken = await _appCheckTokenProvider?.call();
      if (appCheckToken == null || appCheckToken.isEmpty) {
        _logger.warning(
          'Subscription',
          'Billing probe blocked because App Check attestation is unavailable.',
        );
        return null;
      }
      final headers = <String, String>{
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $authToken',
        'X-Firebase-AppCheck': appCheckToken,
      };
      final response = await _http
          .post(
            Uri.parse(_verificationEndpoint),
            headers: headers,
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
          status == 'active' && expiry > _now().millisecondsSinceEpoch;
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
    _entitlementBoundaryTimer?.cancel();
    _iapSubscription?.cancel();
    _http.close();
    super.dispose();
  }

  /// Starts the Google Play purchase flow. AI is unlocked only after the
  /// purchase stream is subsequently verified by the backend.
  Future<bool> purchaseProMonthly() async {
    _logger.info('Subscription', 'Starting Pro Monthly purchase flow');
    _purchaseStatus = PurchaseStatus.purchasing;
    _lastPurchaseFailure = PurchaseFailure.none;

    try {
      final authToken = await _authTokenProvider?.call();
      final accountBinding = await _accountBindingProvider?.call();
      if (authToken == null ||
          authToken.isEmpty ||
          accountBinding == null ||
          accountBinding.isEmpty) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }

      // Never bill for a Pro entitlement the backend cannot grant. Confirm the
      // verification function answers before Google Play takes any money.
      if (!await _verificationEndpointReachable(authToken)) {
        return _failPurchase(PurchaseFailure.verificationUnavailable);
      }

      final isAvailable = await _iap.isAvailable();
      if (!isAvailable) return _failPurchase(PurchaseFailure.storeUnavailable);

      final response = await _iap.queryProductDetails({_kProMonthlyId});
      if (response.productDetails.isEmpty) {
        return _failPurchase(PurchaseFailure.productUnavailable);
      }

      final product = response.productDetails.first;
      final purchaseParam = iap.PurchaseParam(
        productDetails: product,
        applicationUserName: accountBinding,
      );
      _purchaseStatus = PurchaseStatus.purchasing;
      return await _iap.buyNonConsumable(purchaseParam: purchaseParam);
    } catch (error, stackTrace) {
      _logger.error(
        'Subscription',
        'Purchase flow failed.',
        error: error,
        stackTrace: stackTrace,
      );
      return _failPurchase(PurchaseFailure.unknown);
    } finally {
      _purchaseStatus = PurchaseStatus.idle;
    }
  }

  bool _failPurchase(PurchaseFailure reason) {
    _purchaseStatus = PurchaseStatus.error;
    _lastPurchaseFailure = reason;
    return false;
  }

  /// True when the verification callable is deployed, reachable, and authenticated.
  /// 401/403 means auth or App Check failed; 404 means undeployed; 5xx means server error.
  /// Only 200 (success) or 400 (schema validation of empty probe payload) indicates
  /// the endpoint is reachable and accepting requests from this client.
  Future<bool> _verificationEndpointReachable(String authToken) async {
    try {
      final appCheckToken = await _appCheckTokenProvider?.call();
      final headers = <String, String>{
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $authToken',
        if (appCheckToken != null && appCheckToken.isNotEmpty)
          'X-Firebase-AppCheck': appCheckToken,
      };
      final response = await _http
          .post(
            Uri.parse(_verificationEndpoint),
            headers: headers,
            body: jsonEncode({'data': <String, dynamic>{}}),
          )
          .timeout(_kVerifyTimeout);
      return response.statusCode == 200 || response.statusCode == 400;
    } catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'Purchase verification endpoint is unreachable.',
        error: error,
        stackTrace: stackTrace,
      );
      return false;
    }
  }

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
  /// purchase callbacks; this method is unavailable outside debug builds.
  void resetToFreeTier() {
    _logger.info('Subscription', 'Resetting active entitlement view to Free');
    _entitlementBoundaryTimer?.cancel();
    _lastVerifiedAt = null;
    _loadedFromCache = false;
    _isCheckingEntitlement = false;
    state = UserEntitlement.free;
  }

  /// Test hook for deterministic sandbox state. The assertion is paired with
  /// the runtime guard so release/profile builds cannot grant entitlement even
  /// if someone accidentally calls this method from product code.
  void setDevSandboxEntitlement(UserEntitlement entitlement) {
    assert(kDebugMode, 'Test-only entitlement mutation is unavailable');
    if (!kDebugMode) return;
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
