/// Single source of truth for Stripe integration and keys.
///
/// Publishable and Secret keys provided for test/production environments.
/// Can be overridden via `--dart-define=STRIPE_PUBLISHABLE_KEY=...` or
/// `--dart-define=STRIPE_SECRET_KEY=...`.
library;

class StripeConfig {
  const StripeConfig._();

  static const String publishableKey = String.fromEnvironment(
    'STRIPE_PUBLISHABLE_KEY',
    defaultValue:
        'pk_test_51RMC4AQrKkHLd2iBByVZ6GBVp7jFWlBgXY3UCLF1YqqWTtst5cGpPM9V7B09aib9lIAXQu9cajdRxc4jsWK0ADrt00ySUKoFHe',
  );

  static const String secretKey = String.fromEnvironment(
    'STRIPE_SECRET_KEY',
    defaultValue: '',
  );

  /// Pre-configured monthly Pro subscription price in Stripe.
  static const String proMonthlyPriceId = String.fromEnvironment(
    'STRIPE_PRO_MONTHLY_PRICE_ID',
    defaultValue: 'price_1URyFbQrKkHLd2iBRSKLWOkv',
  );

  /// Success redirect URL for web/hosted checkout.
  static const String successUrl =
      'https://relateai-birthday-ysoman-a2372.web.app/success?session_id={CHECKOUT_SESSION_ID}';

  /// Cancel redirect URL for web/hosted checkout.
  static const String cancelUrl =
      'https://relateai-birthday-ysoman-a2372.web.app/cancel';
}
