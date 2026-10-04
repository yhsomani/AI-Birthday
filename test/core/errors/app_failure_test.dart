import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AppFailure', () {
    test('credentialInvalid is a credential error, not missing', () {
      const failure = AppFailure.credentialInvalid(action: 'Replace the key');
      expect(failure.code, AppFailureCode.aiCredentialInvalid);
      expect(failure.isCredentialError, isTrue);
      expect(failure.message, contains('Gemini API key'));
      expect(failure.action, 'Replace the key');
    });

    test('lockedAi reports the entitlement lock', () {
      const failure = AppFailure.lockedAi();
      expect(failure.code, AppFailureCode.aiLocked);
      expect(failure.isRetryable, isFalse);
    });

    test('transient failures are retryable, lock/validation are not', () {
      for (final failure in const [
        AppFailure.networkUnavailable(),
        AppFailure.timeout(),
        AppFailure.busy(),
        AppFailure.syncConflict(),
      ]) {
        expect(
          failure.isRetryable,
          isTrue,
          reason: '${failure.code} should be retryable',
        );
      }
      for (final failure in const [
        AppFailure.lockedAi(),
        AppFailure.credentialMissing(),
        AppFailure.validation(),
        AppFailure.notFound(),
      ]) {
        expect(
          failure.isRetryable,
          isFalse,
          reason: '${failure.code} should not be retryable',
        );
      }
    });

    test('failures carry problem → reason → action guidance', () {
      const failure = AppFailure.nanoUnavailable();
      expect(failure.message, isNotNull);
      expect(failure.detail, isNotNull);
      expect(failure.action, isNotNull);
    });

    test('toString never exposes stack traces or internals', () {
      const failure = AppFailure.providerError(detail: 'backend-gateway-9');
      expect(failure.toString(), contains('AppFailure'));
      expect(failure.toString(), isNot(contains('Stacktrace')));
    });
  });
}
