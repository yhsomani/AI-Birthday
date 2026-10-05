import 'dart:convert';
import 'package:http/http.dart' as http;

/// Mock HTTP client simulating Firebase Auth Identity Platform REST endpoint
/// (accounts:signInWithIdp) for R1 requirement tests.
class FakeFirebaseAuthClient extends http.BaseClient {
  FakeFirebaseAuthClient({
    this.shouldSucceed = true,
    this.statusCode = 200,
    this.customResponseJson,
    this.throwNetworkError = false,
  });

  bool shouldSucceed;
  int statusCode;
  Map<String, dynamic>? customResponseJson;
  bool throwNetworkError;

  int postCalls = 0;
  String? lastRequestBody;
  Uri? lastRequestUrl;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    postCalls++;
    lastRequestUrl = request.url;

    if (request is http.Request) {
      lastRequestBody = request.body;
    }

    if (throwNetworkError) {
      throw http.ClientException('Network connection failed');
    }

    if (!shouldSucceed || statusCode != 200) {
      final errorBody = jsonEncode(
        customResponseJson ?? {
          'error': {
            'code': statusCode,
            'message': 'INVALID_ID_TOKEN',
            'errors': [
              {
                'message': 'INVALID_ID_TOKEN',
                'domain': 'global',
                'reason': 'invalid',
              },
            ],
          },
        },
      );
      return http.StreamedResponse(
        Stream.value(utf8.encode(errorBody)),
        statusCode,
        headers: {'content-type': 'application/json'},
      );
    }

    final successBody = jsonEncode(
      customResponseJson ?? {
        'federatedId': 'google.com:1234567890',
        'providerId': 'google.com',
        'localId': 'firebase-uid-verified-987',
        'email': 'user@example.com',
        'emailVerified': true,
        'displayName': 'Verified User',
        'idToken': 'firebase-jwt-token-verified-abc',
        'refreshToken': 'firebase-refresh-token-xyz',
        'expiresIn': '3600',
      },
    );

    return http.StreamedResponse(
      Stream.value(utf8.encode(successBody)),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}
