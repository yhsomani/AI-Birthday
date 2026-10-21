import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ai_birthday/features/subscription/application/stripe_gateway_service.dart';

void main() {
  group('StripeGatewayService Tests', () {
    test('createProCheckoutSession returns checkout url on 200', () async {
      final mockClient = MockClient((request) async {
        expect(request.url.path, '/v1/checkout/sessions');
        expect(request.method, 'POST');
        expect(request.headers['authorization'], startsWith('Bearer sk_test_'));
        return http.Response(
          jsonEncode({
            'id': 'cs_test_mock123',
            'url': 'https://checkout.stripe.com/c/pay/cs_test_mock123',
          }),
          200,
        );
      });

      final service = StripeGatewayService(
        httpClient: mockClient,
        secretKey: 'sk_test_mock_key',
      );

      final url = await service.createProCheckoutSession(
        customerEmail: 'test@example.com',
        clientReferenceId: 'user_123',
      );

      expect(url, 'https://checkout.stripe.com/c/pay/cs_test_mock123');
    });

    test('createProCheckoutSession returns null on API error', () async {
      final mockClient = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'error': {'message': 'Invalid key'},
          }),
          401,
        );
      });

      final service = StripeGatewayService(
        httpClient: mockClient,
        secretKey: 'sk_test_invalid',
      );

      final url = await service.createProCheckoutSession();
      expect(url, isNull);
    });

    test(
      'verifyCustomerActiveSubscription returns true when active subscription found',
      () async {
        final mockClient = MockClient((request) async {
          if (request.url.path == '/v1/customers') {
            return http.Response(
              jsonEncode({
                'data': [
                  {'id': 'cus_mock123', 'email': 'test@example.com'},
                ],
              }),
              200,
            );
          } else if (request.url.path == '/v1/subscriptions') {
            return http.Response(
              jsonEncode({
                'data': [
                  {'id': 'sub_mock123', 'status': 'active'},
                ],
              }),
              200,
            );
          }
          return http.Response('Not Found', 404);
        });

        final service = StripeGatewayService(
          httpClient: mockClient,
          secretKey: 'sk_test_mock_key',
        );

        final active = await service.verifyCustomerActiveSubscription(
          'test@example.com',
        );
        expect(active, isTrue);
      },
    );

    test(
      'verifyCustomerActiveSubscription returns false when no subscriptions',
      () async {
        final mockClient = MockClient((request) async {
          if (request.url.path == '/v1/customers') {
            return http.Response(
              jsonEncode({
                'data': [
                  {'id': 'cus_mock123', 'email': 'test@example.com'},
                ],
              }),
              200,
            );
          } else if (request.url.path == '/v1/subscriptions') {
            return http.Response(jsonEncode({'data': []}), 200);
          }
          return http.Response('Not Found', 404);
        });

        final service = StripeGatewayService(
          httpClient: mockClient,
          secretKey: 'sk_test_mock_key',
        );

        final active = await service.verifyCustomerActiveSubscription(
          'test@example.com',
        );
        expect(active, isFalse);
      },
    );

    test('createBillingPortalSession returns portal url', () async {
      final mockClient = MockClient((request) async {
        if (request.url.path == '/v1/customers') {
          return http.Response(
            jsonEncode({
              'data': [
                {'id': 'cus_mock123'},
              ],
            }),
            200,
          );
        } else if (request.url.path == '/v1/billing_portal/sessions') {
          return http.Response(
            jsonEncode({
              'url': 'https://billing.stripe.com/p/session?secret=test',
            }),
            200,
          );
        }
        return http.Response('Not Found', 404);
      });

      final service = StripeGatewayService(
        httpClient: mockClient,
        secretKey: 'sk_test_mock_key',
      );

      final url = await service.createBillingPortalSession('test@example.com');
      expect(url, 'https://billing.stripe.com/p/session?secret=test');
    });
  });
}
