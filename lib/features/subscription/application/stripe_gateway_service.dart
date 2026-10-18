import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:ai_birthday/core/config/stripe_config.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';

/// Service to interact with Stripe API directly for subscriptions and checkouts.
class StripeGatewayService {
  StripeGatewayService({
    http.Client? httpClient,
    AppLogger? logger,
    String? secretKey,
  })  : _http = httpClient ?? http.Client(),
        _logger = logger ?? ConsoleAppLogger(),
        _secretKey = secretKey ?? StripeConfig.secretKey;

  final http.Client _http;
  final AppLogger _logger;
  final String _secretKey;

  static const String _stripeApiBase = 'https://api.stripe.com/v1';

  Map<String, String> get _authHeaders => {
        'Authorization': 'Bearer $_secretKey',
        'Content-Type': 'application/x-www-form-urlencoded',
      };

  /// Creates a hosted Stripe Checkout Session for Pro Monthly subscription.
  ///
  /// Returns the URL to which the user should be redirected.
  Future<String?> createProCheckoutSession({
    String? customerEmail,
    String? clientReferenceId,
  }) async {
    _logger.info('StripeGateway', 'Creating Pro Monthly checkout session');
    try {
      final uri = Uri.parse('$_stripeApiBase/checkout/sessions');
      final body = <String, String>{
        'mode': 'subscription',
        'line_items[0][price]': StripeConfig.proMonthlyPriceId,
        'line_items[0][quantity]': '1',
        'success_url': StripeConfig.successUrl,
        'cancel_url': StripeConfig.cancelUrl,
        if (customerEmail != null && customerEmail.isNotEmpty)
          'customer_email': customerEmail,
        if (clientReferenceId != null && clientReferenceId.isNotEmpty)
          'client_reference_id': clientReferenceId,
      };

      final response = await _http.post(
        uri,
        headers: _authHeaders,
        body: body,
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final url = data['url'] as String?;
        _logger.info('StripeGateway', 'Checkout session created successfully');
        return url;
      } else {
        _logger.warning(
          'StripeGateway',
          'Failed to create checkout session: ${response.statusCode} - ${response.body}',
        );
        return null;
      }
    } catch (e, st) {
      _logger.error(
        'StripeGateway',
        'Error creating checkout session',
        error: e,
        stackTrace: st,
      );
      return null;
    }
  }

  /// Verifies if a customer has an active subscription in Stripe by email or customer ID.
  Future<bool> verifyCustomerActiveSubscription(String customerEmail) async {
    _logger.info('StripeGateway', 'Checking active Stripe subscriptions for email');
    try {
      // 1. Search for customer by email
      final customerUri = Uri.parse('$_stripeApiBase/customers?email=${Uri.encodeComponent(customerEmail)}');
      final custResp = await _http.get(customerUri, headers: _authHeaders);
      if (custResp.statusCode != 200) return false;

      final custData = jsonDecode(custResp.body) as Map<String, dynamic>;
      final customers = custData['data'] as List<dynamic>?;
      if (customers == null || customers.isEmpty) return false;

      for (final cust in customers) {
        final custId = cust['id'] as String?;
        if (custId == null) continue;

        // 2. Fetch active subscriptions for customer
        final subUri = Uri.parse('$_stripeApiBase/subscriptions?customer=$custId&status=active');
        final subResp = await _http.get(subUri, headers: _authHeaders);
        if (subResp.statusCode != 200) continue;

        final subData = jsonDecode(subResp.body) as Map<String, dynamic>;
        final subs = subData['data'] as List<dynamic>?;
        if (subs != null && subs.isNotEmpty) {
          _logger.info('StripeGateway', 'Found active Stripe subscription for customer $custId');
          return true;
        }
      }

      return false;
    } catch (e, st) {
      _logger.error(
        'StripeGateway',
        'Error verifying Stripe subscription',
        error: e,
        stackTrace: st,
      );
      return false;
    }
  }

  /// Creates a Customer Billing Portal session for self-service subscription management.
  Future<String?> createBillingPortalSession(String customerEmail) async {
    _logger.info('StripeGateway', 'Creating Billing Portal session');
    try {
      final customerUri = Uri.parse('$_stripeApiBase/customers?email=${Uri.encodeComponent(customerEmail)}');
      final custResp = await _http.get(customerUri, headers: _authHeaders);
      if (custResp.statusCode != 200) return null;

      final custData = jsonDecode(custResp.body) as Map<String, dynamic>;
      final customers = custData['data'] as List<dynamic>?;
      if (customers == null || customers.isEmpty) return null;

      final custId = customers.first['id'] as String;
      final portalUri = Uri.parse('$_stripeApiBase/billing_portal/sessions');
      final response = await _http.post(
        portalUri,
        headers: _authHeaders,
        body: {
          'customer': custId,
          'return_url': 'https://relateai-birthday-ysoman-a2372.web.app',
        },
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        return data['url'] as String?;
      }
      return null;
    } catch (e, st) {
      _logger.error(
        'StripeGateway',
        'Error creating Billing Portal session',
        error: e,
        stackTrace: st,
      );
      return null;
    }
  }

  void dispose() {
    _http.close();
  }
}
