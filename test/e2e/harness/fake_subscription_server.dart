import 'package:http/http.dart' as http;
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

/// Simulates Firebase Cloud Functions verifyPurchase and Firestore Entitlement
/// status backend contract for R2 requirement tests.
class FakeSubscriptionServer {
  FakeSubscriptionServer({
    this.authoritativeStatus = EntitlementStatus.none,
    this.productId = 'ai_birthday_pro_monthly',
    this.expectedPackageName = 'com.yashsomani.ai_birthday',
    this.validPurchaseToken = 'valid_google_play_purchase_token_123',
    this.expectedAccountBinding = 'acct-binding-test-123456',
    this.shouldSimulateServerOutage = false,
  });

  EntitlementStatus authoritativeStatus;
  String productId;
  String expectedPackageName;
  String validPurchaseToken;
  String expectedAccountBinding;
  bool shouldSimulateServerOutage;

  int verificationRequests = 0;

  /// Validates a verifyPurchase HTTP request body and headers per M2 contract.
  Map<String, dynamic> handleVerifyPurchaseRequest({
    required Map<String, dynamic> body,
    required String? authHeader,
  }) {
    verificationRequests++;

    if (shouldSimulateServerOutage) {
      throw http.ClientException('Cloud Function verifyPurchase 500 error');
    }

    if (authHeader == null || !authHeader.startsWith('Bearer ')) {
      return {
        'error': 'UNAUTHORIZED',
        'message': 'Missing or invalid Bearer token',
        'statusCode': 401,
      };
    }

    final contractVersion = body['contractVersion'];
    final purchaseToken = body['purchaseToken'];
    final reqProductId = body['productId'];
    final packageName = body['packageName'];
    final accountBinding = body['accountBinding'];

    if (contractVersion != 1) {
      return {
        'error': 'INVALID_CONTRACT',
        'message': 'contractVersion must be 1',
        'statusCode': 400,
      };
    }

    if (packageName != expectedPackageName) {
      return {
        'error': 'PACKAGE_MISMATCH',
        'message': 'Invalid package name: $packageName',
        'statusCode': 400,
      };
    }

    if (reqProductId != productId) {
      return {
        'error': 'INVALID_PRODUCT',
        'message': 'Unknown product ID: $reqProductId',
        'statusCode': 400,
      };
    }

    if (accountBinding != expectedAccountBinding) {
      return {
        'error': 'PURCHASE_ACCOUNT_MISMATCH',
        'message': 'Purchase is not bound to this app account.',
        'statusCode': 403,
      };
    }

    if (purchaseToken != validPurchaseToken) {
      return {
        'status': 'none',
        'productId': reqProductId,
        'expiryDateMs': 0,
        'canUseAi': false,
        'statusCode': 200,
      };
    }

    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final expiryMs = authoritativeStatus == EntitlementStatus.active
        ? nowMs + (30 * 24 * 60 * 60 * 1000)
        : (authoritativeStatus == EntitlementStatus.expired
              ? nowMs - (24 * 60 * 60 * 1000)
              : 0);

    return {
      'status': authoritativeStatus.name,
      'productId': reqProductId,
      'expiryDateMs': expiryMs,
      'canUseAi': authoritativeStatus.isEntitled,
      'statusCode': 200,
    };
  }

  /// Returns the Firestore `/users/{uid}/entitlement/status` document snapshot representation.
  Map<String, dynamic> getFirestoreEntitlementDocument(String uid) {
    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final isEntitled = authoritativeStatus.isEntitled;
    return {
      'status': authoritativeStatus.name,
      'productId': productId,
      'expiryDateMs': isEntitled ? nowMs + (30 * 24 * 3600 * 1000) : 0,
      'isAutoRenewing': isEntitled,
      'canUseAi': isEntitled,
      'verifiedAtMs': nowMs,
      'updatedAt': DateTime.now().toIso8601String(),
    };
  }
}
