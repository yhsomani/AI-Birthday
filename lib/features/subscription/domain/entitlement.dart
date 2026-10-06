/// Application entitlement model for AI features (SSOT §5, §11).
library;

/// Entitlement state of the application.
enum EntitlementStatus {
  active,
  trial,
  grace,
  expired,
  none;

  /// Whether the user is currently entitled to use AI capabilities.
  bool get isEntitled => switch (this) {
    EntitlementStatus.active ||
    EntitlementStatus.trial ||
    EntitlementStatus.grace => true,
    _ => false,
  };

  String get displayName => switch (this) {
    EntitlementStatus.active => 'Pro (Active)',
    EntitlementStatus.trial => 'Free Trial',
    EntitlementStatus.grace => 'Grace Period',
    EntitlementStatus.expired => 'Expired',
    EntitlementStatus.none => 'Free Tier',
  };
}

/// Entitlement details for the current user.
class UserEntitlement {
  const UserEntitlement({
    required this.status,
    this.productId,
    this.expiryDate,
    this.isAutoRenewing = false,
  });

  final EntitlementStatus status;
  final String? productId;
  final DateTime? expiryDate;
  final bool isAutoRenewing;

  bool get canUseAi {
    if (!status.isEntitled) return false;
    final expiry = expiryDate;
    return expiry == null || expiry.isAfter(DateTime.now());
  }

  /// Default free tier entitlement without active AI subscription.
  static const free = UserEntitlement(status: EntitlementStatus.none);

  /// Active pro entitlement.
  static const proActive = UserEntitlement(
    status: EntitlementStatus.active,
    productId: 'ai_birthday_pro_monthly',
    isAutoRenewing: true,
  );
}
