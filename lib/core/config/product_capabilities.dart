/// Release-scoped product capabilities.
///
/// Core local features are always available. Features that require separate
/// product, platform-policy, legal, or deployment approval stay disabled by
/// default and must be enabled deliberately for a tested distribution build.
library;

class ProductCapabilities {
  const ProductCapabilities._();

  /// Automatic background message generation is not part of the v1 product.
  /// A future release must define consent, quota, failure and review behavior
  /// before enabling this flag.
  static const bool autoPrepare = bool.fromEnvironment(
    'ENABLE_AUTO_PREPARE',
    defaultValue: false,
  );

  /// Direct background SMS is excluded from the v1 production contract. The
  /// existing native adapter remains isolated behind this compile-time gate
  /// while manual SMS composer handoff stays available.
  static const bool automaticSms = bool.fromEnvironment(
    'ENABLE_AUTOMATIC_SMS',
    defaultValue: false,
  );

  /// Stripe is not supported from the client. Payment secrets must never be
  /// loaded into a shipped Flutter binary.
  static const bool stripeClientBilling = false;
}
