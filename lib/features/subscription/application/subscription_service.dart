import 'dart:async';
import 'dart:convert';

import 'package:clock/clock.dart' as clock;
import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter/foundation.dart'
    show ValueListenable, ValueNotifier, kDebugMode;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:in_app_purchase/in_app_purchase.dart' as iap;

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

  /// Google Play returned a purchase but the backend could not verify it. The
  /// purchase stays unacknowledged and is retried via Restore.
  verificationFailed,

  /// Google Play reported a billing error for the purchase.
  purchaseError,
  unknown,
}

/// Result of a Restore Purchases attempt, so the UI never fails silently.
enum RestoreOutcome {
  restored,
  notFound,
  verificationFailed,
  signInRequired,
  storeUnavailable,
  error,
}

/// Observable purchase/restore progress for the UI (button label, snackbars).
class PurchaseUiState {
  const PurchaseUiState({
    this.status = PurchaseStatus.idle,
    this.failure = PurchaseFailure.none,
    this.isRestoring = false,
    this.hasPendingVerification = false,
    this.productPrice,
  });

  final PurchaseStatus status;
  final PurchaseFailure failure;
  final bool isRestoring;
  final bool hasPendingVerification;

  /// Localised price from Google Play (`ProductDetails.price`), if loaded.
  final String? productPrice;

  bool get isBusy =>
      status == PurchaseStatus.purchasing || status == PurchaseStatus.verifying;

  PurchaseUiState copyWith({
    PurchaseStatus? status,
    PurchaseFailure? failure,
    bool? isRestoring,
    bool? hasPendingVerification,
    String? productPrice,
  }) => PurchaseUiState(
    status: status ?? this.status,
    failure: failure ?? this.failure,
    isRestoring: isRestoring ?? this.isRestoring,
    hasPendingVerification:
        hasPendingVerification ?? this.hasPendingVerification,
    productPrice: productPrice ?? this.productPrice,
  );
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

class _AccountOperationFence {
  const _AccountOperationFence(this.accountId, this.generation);

  final String accountId;
  final int generation;

  bool matches(_AccountOperationFence other) =>
      accountId == other.accountId && generation == other.generation;
}

/// Purchase/entitlement manager.
///
/// A Play purchase never unlocks AI merely because the billing client returned
/// a non-empty token. The token is sent to the authenticated server endpoint,
/// which checks the Google Play Developer API and returns the verified state.
class SubscriptionNotifier extends StateNotifier<UserEntitlement> {
  SubscriptionNotifier({
    UserEntitlement initial = UserEntitlement.free,
    this._store,
    iap.InAppPurchase? inAppPurchase,
    http.Client? httpClient,
    AppLogger? logger,
    this._authTokenProvider,
    this._accountBindingProvider,
    this._accountIdProvider,
    this._appCheckTokenProvider,
    DateTime Function()? now,
    String? verificationEndpoint,
    this._restoreTimeout = const Duration(seconds: 10),
    this._restoreHandoffTimeout = const Duration(seconds: 11),
  }) : _iap = inAppPurchase ?? iap.InAppPurchase.instance,
       _http = httpClient ?? http.Client(),
       _logger = logger ?? ConsoleAppLogger(),
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
  final Duration _restoreTimeout;
  final Duration _restoreHandoffTimeout;

  StreamSubscription<List<iap.PurchaseDetails>>? _iapSubscription;
  Timer? _entitlementBoundaryTimer;
  Completer<RestoreOutcome>? _restoreCompleter;
  Completer<void>? _purchaseUpdatesIdle;
  int _pendingPurchaseUpdates = 0;
  _AccountOperationFence? _restoreFence;
  _AccountOperationFence? _restoreRunFence;
  _AccountOperationFence? _purchaseFence;
  String? _activeAccountId;
  int _accountGeneration = 0;
  final ValueNotifier<PurchaseUiState> _purchaseUi = ValueNotifier(
    const PurchaseUiState(),
  );

  /// Purchase/restore progress. Terminal states (success/error/cancelled)
  /// stay until [clearPurchaseResult] so the UI can report them once.
  ValueListenable<PurchaseUiState> get purchaseUiState => _purchaseUi;

  void _setUi(PurchaseUiState next) {
    if (!mounted) return;
    _purchaseUi.value = next;
  }

  /// Acknowledge a terminal purchase result after the UI has shown it.
  void clearPurchaseResult() {
    final ui = _purchaseUi.value;
    if (ui.isBusy || ui.status == PurchaseStatus.idle) return;
    _setUi(
      PurchaseUiState(
        isRestoring: ui.isRestoring,
        hasPendingVerification: ui.hasPendingVerification,
        productPrice: ui.productPrice,
      ),
    );
  }

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

  Future<String?> _readAccountId() async {
    final provider = _accountIdProvider;
    if (provider != null) return provider();
    return _accountBindingProvider?.call();
  }

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

      await _loadProductPrice();

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

  /// Best-effort fetch of the localised Play price shown on the upgrade button.
  Future<void> _loadProductPrice() async {
    try {
      final response = await _iap.queryProductDetails({_kProMonthlyId});
      if (response.productDetails.isEmpty) return;
      _setUi(
        _purchaseUi.value.copyWith(
          productPrice: response.productDetails.first.price,
        ),
      );
    } catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'Could not load product price.',
        error: error,
        stackTrace: stackTrace,
      );
    }
  }

  Future<_AccountOperationFence?> _captureAccountFence() async {
    final generation = _accountGeneration;
    final accountId = await _readAccountId();
    if (!mounted ||
        generation != _accountGeneration ||
        accountId == null ||
        accountId.isEmpty) {
      return null;
    }
    if (_activeAccountId == null) {
      _activeAccountId = accountId;
    } else if (_activeAccountId != accountId) {
      resetToFreeTier();
      _activeAccountId = accountId;
    }
    return _AccountOperationFence(accountId, _accountGeneration);
  }

  Future<bool> _isFenceCurrent(_AccountOperationFence fence) async {
    if (!mounted ||
        fence.generation != _accountGeneration ||
        _activeAccountId != fence.accountId) {
      return false;
    }
    final accountId = await _readAccountId();
    return mounted &&
        fence.generation == _accountGeneration &&
        _activeAccountId == fence.accountId &&
        accountId == fence.accountId;
  }

  String _accountEntitlementKey(String accountId) =>
      '$_kEntitlementCacheKey:$accountId';

  Future<void> reloadAccountEntitlement() async {
    await initializationComplete;
    if (!mounted) return;
    final fence = await _captureAccountFence();
    if (fence == null) {
      resetToFreeTier();
      return;
    }
    _isCheckingEntitlement = true;
    _loadedFromCache = false;
    _lastVerifiedAt = null;
    state = UserEntitlement.free;
    await _loadCachedEntitlement(fence);
    if (!await _isFenceCurrent(fence)) return;
    _isCheckingEntitlement = false;
    state = UserEntitlement(
      status: state.status,
      productId: state.productId,
      expiryDate: state.expiryDate,
      isAutoRenewing: state.isAutoRenewing,
      verifiedAt: state.verifiedAt,
    );
    await _restorePurchases();
  }

  Future<void> _loadCachedEntitlement([
    _AccountOperationFence? expectedFence,
  ]) async {
    final store = _store;
    final fence = expectedFence ?? await _captureAccountFence();
    if (store == null || fence == null) return;
    final scopedKey = _accountEntitlementKey(fence.accountId);
    var raw = await store.read(scopedKey);
    final isLegacyCache = raw == null;
    raw ??= await store.read(_kEntitlementCacheKey);
    if (raw == null) return;
    if (!await _isFenceCurrent(fence)) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic> ||
          decoded['accountId'] != fence.accountId) {
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
      if (isLegacyCache && await _isFenceCurrent(fence)) {
        await store.write(scopedKey, raw);
      }
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

  Future<void> _persistVerifiedEntitlement(
    UserEntitlement entitlement,
    _AccountOperationFence fence,
  ) async {
    final store = _store;
    if (store == null || !await _isFenceCurrent(fence)) return;
    try {
      await store.write(
        _accountEntitlementKey(fence.accountId),
        jsonEncode({
          'accountId': fence.accountId,
          'status': entitlement.status.name,
          'productId': entitlement.productId,
          'expiryDate': entitlement.expiryDate?.toUtc().toIso8601String(),
          'isAutoRenewing': entitlement.isAutoRenewing,
          'verifiedAt': entitlement.verifiedAt?.toUtc().toIso8601String(),
        }),
      );
    } on Object catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'Verified entitlement could not be cached; store acknowledgement will continue.',
        error: error,
        stackTrace: stackTrace,
        errorCategory: 'entitlement_cache_write',
      );
    }
  }

  Future<void> _onPurchasesUpdated(List<iap.PurchaseDetails> purchases) async {
    if (_pendingPurchaseUpdates++ == 0) {
      _purchaseUpdatesIdle = Completer<void>();
    }
    try {
      await _processPurchaseUpdates(purchases);
    } finally {
      _pendingPurchaseUpdates--;
      if (_pendingPurchaseUpdates == 0) {
        final idle = _purchaseUpdatesIdle;
        _purchaseUpdatesIdle = null;
        if (idle != null && !idle.isCompleted) idle.complete();
      }
    }
  }

  Future<void> _processPurchaseUpdates(
    List<iap.PurchaseDetails> purchases,
  ) async {
    for (final purchase in purchases) {
      if (purchase.productID != _kProMonthlyId) {
        if (purchase.pendingCompletePurchase) {
          await _iap.completePurchase(purchase);
        }
        continue;
      }

      final fence =
          _restoreFence ?? _purchaseFence ?? await _captureAccountFence();
      if (fence == null || !await _isFenceCurrent(fence)) continue;

      _VerifiedEntitlement? verified;
      if (purchase.status == iap.PurchaseStatus.purchased ||
          purchase.status == iap.PurchaseStatus.restored) {
        _purchaseStatus = PurchaseStatus.verifying;
        _setUi(
          _purchaseUi.value.copyWith(
            status: PurchaseStatus.verifying,
            failure: PurchaseFailure.none,
          ),
        );
        verified = await _verifyPurchase(purchase, fence);
        if (!await _isFenceCurrent(fence)) continue;

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
          await _persistVerifiedEntitlement(next, fence);
          if (!await _isFenceCurrent(fence)) continue;
          _purchaseStatus = PurchaseStatus.success;
          _setUi(
            _purchaseUi.value.copyWith(
              status: next.canUseAi
                  ? PurchaseStatus.success
                  : PurchaseStatus.error,
              hasPendingVerification: false,
              failure: next.canUseAi
                  ? PurchaseFailure.none
                  : PurchaseFailure.verificationFailed,
            ),
          );
        } else {
          // Verification could not be completed (network/server). A
          // previously verified grant is preserved only for a bounded
          // window; past that, stop claiming Pro (audit P1-3).
          final preserved =
              state.status.isEntitled &&
              state.canUseAi &&
              _lastVerifiedAt != null &&
              _now().difference(_lastVerifiedAt!) < _kPreserveTtl;
          _hasPendingVerification = true;
          if (!preserved) state = UserEntitlement.free;
          _purchaseStatus = PurchaseStatus.error;
          _setUi(
            _purchaseUi.value.copyWith(
              status: PurchaseStatus.error,
              failure: PurchaseFailure.verificationFailed,
              hasPendingVerification: true,
            ),
          );
        }
      } else if (purchase.status == iap.PurchaseStatus.error) {
        _purchaseStatus = PurchaseStatus.error;
        _setUi(
          _purchaseUi.value.copyWith(
            status: PurchaseStatus.error,
            failure: PurchaseFailure.purchaseError,
          ),
        );
      } else if (purchase.status == iap.PurchaseStatus.canceled) {
        _purchaseStatus = PurchaseStatus.cancelled;
        _setUi(
          _purchaseUi.value.copyWith(
            status: PurchaseStatus.cancelled,
            failure: PurchaseFailure.none,
          ),
        );
      }

      // Acknowledge the platform transaction only after a verified success.
      // An unacknowledged purchase stays pending in Google Play and is
      // re-emitted on the next launch/restore, which is the automatic retry
      // path for a transient verification failure (audit P1-1).
      if (purchase.pendingCompletePurchase &&
          verified != null &&
          await _isFenceCurrent(fence)) {
        await _iap.completePurchase(purchase);
      }

      final completer = _restoreCompleter;
      if (completer != null &&
          _restoreFence?.matches(fence) == true &&
          !completer.isCompleted) {
        // Only a fresh, successful verification counts as "restored";
        // a preserved or timeout state never reports success (audit P3-1).
        completer.complete(switch (purchase.status) {
          iap.PurchaseStatus.purchased || iap.PurchaseStatus.restored =>
            verified == null
                ? RestoreOutcome.verificationFailed
                : (state.canUseAi
                      ? RestoreOutcome.restored
                      : RestoreOutcome.notFound),
          iap.PurchaseStatus.error => RestoreOutcome.error,
          _ => RestoreOutcome.notFound,
        });
      }
    }

    final activeFence = _restoreFence ?? _purchaseFence;
    if (activeFence == null || await _isFenceCurrent(activeFence)) {
      _purchaseStatus = PurchaseStatus.idle;
    }
  }

  Future<_VerifiedEntitlement?> _verifyPurchase(
    iap.PurchaseDetails purchase,
    _AccountOperationFence fence,
  ) async {
    if (!await _isFenceCurrent(fence)) return null;
    final authToken = await _authTokenProvider?.call();
    if (!await _isFenceCurrent(fence)) return null;
    final accountBinding = await _accountBindingProvider?.call();
    if (!await _isFenceCurrent(fence)) return null;

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
      if (!await _isFenceCurrent(fence)) return null;
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
      if (!await _isFenceCurrent(fence)) return null;

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
    super.dispose(); // throws StateError on a double dispose, as before
    _purchaseUi.dispose();
  }

  /// Starts the Google Play purchase flow. AI is unlocked only after the
  /// purchase stream is subsequently verified by the backend.
  Future<bool> purchaseProMonthly() async {
    _logger.info('Subscription', 'Starting Pro Monthly purchase flow');
    final fence = await _captureAccountFence();
    if (fence == null) return _failPurchase(PurchaseFailure.signInRequired);
    if (_restoreRun != null && _restoreRunFence?.matches(fence) != true) {
      return _failPurchase(PurchaseFailure.signInRequired);
    }
    _purchaseFence = fence;
    _purchaseStatus = PurchaseStatus.purchasing;
    _lastPurchaseFailure = PurchaseFailure.none;
    _setUi(
      _purchaseUi.value.copyWith(
        status: PurchaseStatus.purchasing,
        failure: PurchaseFailure.none,
      ),
    );

    try {
      final authToken = await _authTokenProvider?.call();
      if (!await _isFenceCurrent(fence)) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }
      final accountBinding = await _accountBindingProvider?.call();
      if (!await _isFenceCurrent(fence)) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }
      if (authToken == null ||
          authToken.isEmpty ||
          accountBinding == null ||
          accountBinding.isEmpty) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }

      // Probe and verification share one requirement: without an App Check
      // token verification would hard-fail after billing, so refuse up front.
      final appCheckToken = await _appCheckTokenProvider?.call();
      if (!await _isFenceCurrent(fence)) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }
      if (appCheckToken == null || appCheckToken.isEmpty) {
        _logger.warning(
          'Subscription',
          'Purchase blocked because App Check attestation is unavailable.',
        );
        return _failPurchase(PurchaseFailure.verificationUnavailable);
      }

      // Never bill for a Pro entitlement the backend cannot grant. Confirm the
      // verification function answers before Google Play takes any money.
      if (!await _verificationEndpointReachable(authToken, appCheckToken) ||
          !await _isFenceCurrent(fence)) {
        return _failPurchase(PurchaseFailure.verificationUnavailable);
      }

      final isAvailable = await _iap.isAvailable();
      if (!await _isFenceCurrent(fence)) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }
      if (!isAvailable) return _failPurchase(PurchaseFailure.storeUnavailable);

      final response = await _iap.queryProductDetails({_kProMonthlyId});
      if (!await _isFenceCurrent(fence)) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }
      if (response.productDetails.isEmpty) {
        return _failPurchase(PurchaseFailure.productUnavailable);
      }

      final product = response.productDetails.first;
      _setUi(_purchaseUi.value.copyWith(productPrice: product.price));
      final purchaseParam = iap.PurchaseParam(
        productDetails: product,
        applicationUserName: accountBinding,
      );
      _purchaseStatus = PurchaseStatus.purchasing;
      final launched = await _iap.buyNonConsumable(
        purchaseParam: purchaseParam,
      );
      if (!await _isFenceCurrent(fence)) {
        return _failPurchase(PurchaseFailure.signInRequired);
      }
      if (!launched) return _failPurchase(PurchaseFailure.purchaseError);
      // The Play sheet is open: purchasing -> verifying/terminal state is
      // driven by the purchase stream.
      return true;
    } catch (error, stackTrace) {
      _logger.error(
        'Subscription',
        'Purchase flow failed.',
        error: error,
        stackTrace: stackTrace,
      );
      return _failPurchase(PurchaseFailure.unknown);
    } finally {
      if (identical(_purchaseFence, fence)) _purchaseFence = null;
      if (await _isFenceCurrent(fence)) _purchaseStatus = PurchaseStatus.idle;
    }
  }

  bool _failPurchase(PurchaseFailure reason) {
    _purchaseStatus = PurchaseStatus.error;
    _lastPurchaseFailure = reason;
    _setUi(
      _purchaseUi.value.copyWith(status: PurchaseStatus.error, failure: reason),
    );
    return false;
  }

  /// True when the verification callable is deployed, reachable, and authenticated.
  /// 401/403 means auth or App Check failed; 404 means undeployed; 5xx means server error.
  /// Only 200 (success) or 400 (schema validation of empty probe payload) indicates
  /// the endpoint is reachable and accepting requests from this client.
  Future<bool> _verificationEndpointReachable(
    String authToken,
    String appCheckToken,
  ) async {
    try {
      final headers = <String, String>{
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $authToken',
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
    return await _restorePurchases() == RestoreOutcome.restored;
  }

  /// Restore with a specific outcome so callers can tell the user why nothing
  /// was restored instead of failing silently.
  Future<RestoreOutcome> restorePurchasesDetailed() => _restorePurchases();

  /// Same-session restore attempts share one run. A different session queues
  /// behind the old store restore because Play events do not identify their
  /// initiating account.
  Future<RestoreOutcome>? _restoreRun;

  Future<RestoreOutcome> _restorePurchases([
    _AccountOperationFence? requestedFence,
  ]) async {
    final fence = requestedFence ?? await _captureAccountFence();
    if (fence == null) return RestoreOutcome.signInRequired;
    var waitingForPriorSession = false;

    while (true) {
      if (!await _isFenceCurrent(fence)) {
        return RestoreOutcome.signInRequired;
      }
      final inFlight = _restoreRun;
      if (inFlight == null) break;
      if (_restoreRunFence?.matches(fence) == true) return inFlight;
      waitingForPriorSession = true;

      try {
        await inFlight.timeout(_restoreHandoffTimeout);
      } on TimeoutException {
        _logger.warning(
          'Subscription',
          'Restore for the previous account did not finish before the handoff deadline.',
          errorCategory: 'restore_handoff_timeout',
        );
        return RestoreOutcome.error;
      }
      if (!await _isFenceCurrent(fence)) {
        return RestoreOutcome.signInRequired;
      }
    }
    if (waitingForPriorSession) {
      final pendingUpdates = _purchaseUpdatesIdle?.future;
      if (pendingUpdates != null) {
        try {
          await pendingUpdates.timeout(_restoreHandoffTimeout);
        } on TimeoutException {
          _logger.warning(
            'Subscription',
            'Restore handoff expired while the previous account had purchase updates in progress.',
            errorCategory: 'restore_handoff_timeout',
          );
          return RestoreOutcome.error;
        }
      }
      if (!await _isFenceCurrent(fence)) {
        return RestoreOutcome.signInRequired;
      }
    }

    _setUi(_purchaseUi.value.copyWith(isRestoring: true));
    final run = _doRestorePurchases(fence);
    _restoreRun = run;
    _restoreRunFence = fence;
    unawaited(
      run.whenComplete(() async {
        if (identical(_restoreRun, run)) {
          _restoreRun = null;
          _restoreRunFence = null;
        }
        if (await _isFenceCurrent(fence) &&
            _restoreFence?.matches(fence) != true) {
          _setUi(_purchaseUi.value.copyWith(isRestoring: false));
        }
      }),
    );
    return run;
  }

  Future<RestoreOutcome> _doRestorePurchases(
    _AccountOperationFence fence,
  ) async {
    _logger.info('Subscription', 'Checking for restorable purchases');
    Completer<RestoreOutcome>? completer;

    try {
      final authToken = await _authTokenProvider?.call();
      if (!await _isFenceCurrent(fence)) {
        return RestoreOutcome.signInRequired;
      }
      final accountBinding = await _accountBindingProvider?.call();
      if (!await _isFenceCurrent(fence)) {
        return RestoreOutcome.signInRequired;
      }
      if (authToken == null ||
          authToken.isEmpty ||
          accountBinding == null ||
          accountBinding.isEmpty) {
        return RestoreOutcome.signInRequired;
      }

      final isAvailable = await _iap.isAvailable();
      if (!await _isFenceCurrent(fence)) {
        return RestoreOutcome.signInRequired;
      }
      if (!isAvailable) return RestoreOutcome.storeUnavailable;

      completer = Completer<RestoreOutcome>();
      _restoreFence = fence;
      _restoreCompleter = completer;
      await _iap.restorePurchases(applicationUserName: accountBinding);
      if (!await _isFenceCurrent(fence)) {
        return RestoreOutcome.signInRequired;
      }

      final outcome = await completer.future.timeout(
        _restoreTimeout,
        // A timeout means nothing was re-verified this run: never report
        // success from stale state (audit P3-1/C6).
        onTimeout: () => RestoreOutcome.notFound,
      );
      return await _isFenceCurrent(fence)
          ? outcome
          : RestoreOutcome.signInRequired;
    } catch (error, stackTrace) {
      _logger.warning(
        'Subscription',
        'Restore purchase flow failed.',
        error: error,
        stackTrace: stackTrace,
      );
      return RestoreOutcome.error;
    } finally {
      if (completer != null &&
          identical(_restoreCompleter, completer) &&
          _restoreFence?.matches(fence) == true) {
        _restoreCompleter = null;
        _restoreFence = null;
      }
    }
  }

  /// Clears account-specific entitlement state after sign-out or account
  /// switching. Pro access is subsequently restored only through verification.
  void resetToFreeTier() {
    _logger.info('Subscription', 'Resetting active entitlement view to Free');
    _accountGeneration++;
    _activeAccountId = null;
    _purchaseFence = null;
    // Keep an old restore fenced until its store operation completes or times
    // out. Its unscoped Play events must not be attributed to the next account.
    _entitlementBoundaryTimer?.cancel();
    _lastVerifiedAt = null;
    _loadedFromCache = false;
    _isCheckingEntitlement = false;
    _hasPendingVerification = false;
    _purchaseStatus = PurchaseStatus.idle;
    _setUi(PurchaseUiState(productPrice: _purchaseUi.value.productPrice));
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

  /// Deterministic, non-PII Play account binding derived from the Firebase
  /// uid: it survives reinstall and device changes (64 hex chars).
  static String deterministicBinding(String firebaseUid) => crypto.sha256
      .convert(utf8.encode('ai-birthday-purchase-binding:v1:$firebaseUid'))
      .toString();

  /// Binding sent to Google Play and the server. A legacy random per-device
  /// binding already in secure storage is kept so purchases made with it still
  /// match Play's `obfuscatedExternalAccountId`; otherwise it is derived from
  /// the account.
  static Future<String?> readBindingFromStore(
    SecureStoreDriver? store,
    String googleSubject, {
    String? firebaseUid,
  }) async {
    if (store != null && googleSubject.isNotEmpty) {
      final existing = await store.read('$_kBindingPrefix$googleSubject');
      if (existing != null && existing.length >= 16) return existing;
    }
    if (firebaseUid == null || firebaseUid.isEmpty) return null;
    return deterministicBinding(firebaseUid);
  }
}
