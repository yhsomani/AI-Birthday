/// Stable, user-facing failure model for the application.
///
/// Every failure should answer three questions (SSOT layer guidance):
/// - What failed? (`code` / `message`)
/// - Why did it fail? (`detail`)
/// - What can the user do next? (`action`)
///
/// Infrastructure/provider details are normalized into stable domain codes.
/// Raw stack traces and internal implementation details are never exposed
/// through this type to the UI.
library;

/// Stable failure codes that the domain and UI can depend on.
///
/// Mirrors the authoritative set defined in the architecture documentation:
/// AI_LOCKED, AI_CREDENTIAL_MISSING, AI_CREDENTIAL_INVALID, AI_NANO_UNAVAILABLE,
/// AI_BUSY, AI_QUOTA_EXCEEDED, AI_TIMEOUT, AI_PROVIDER_ERROR, PERMISSION_DENIED,
/// NETWORK_UNAVAILABLE, SYNC_CONFLICT, DELIVERY_UNAVAILABLE, DELIVERY_FAILED.
enum AppFailureCode {
  aiLocked,
  aiCredentialMissing,
  aiCredentialInvalid,
  aiNanoUnavailable,
  aiBusy,
  aiQuotaExceeded,
  aiTimeout,
  aiProviderError,
  permissionDenied,
  networkUnavailable,
  syncConflict,
  deliveryUnavailable,
  deliveryFailed,
  validation,
  notFound,
  unknown,
}

/// A typed application failure.
///
/// [AppFailure] carries a stable [AppFailureCode] plus optional human-readable
/// guidance. It is intentionally not an elaborate exception hierarchy: callers
/// switch on [code] and map the code to UI copy in one place.
class AppFailure implements Exception {
  const AppFailure(
    this.code, {
    this.message,
    this.detail,
    this.action,
    this.operationId,
  });

  /// Failure classified into a stable, user-meaningful category.
  final AppFailureCode code;

  /// A short, plain-language description of what failed.
  final String? message;

  /// Additional diagnostic detail that is safe to show the user.
  final String? detail;

  /// Concrete next step the user can take.
  final String? action;

  /// Optional operation id used to correlate sanitized logs.
  final String? operationId;

  /// Whether the operation can reasonably be retried as-is.
  bool get isRetryable => switch (code) {
        AppFailureCode.networkUnavailable ||
        AppFailureCode.aiTimeout ||
        AppFailureCode.aiBusy ||
        AppFailureCode.aiProviderError ||
        AppFailureCode.syncConflict ||
        AppFailureCode.deliveryFailed =>
          true,
        _ => false,
      };

  /// Whether a configured credential exists but is invalid (never "missing").
  bool get isCredentialError => switch (code) {
        AppFailureCode.aiCredentialInvalid => true,
        _ => false,
      };

  @override
  String toString() => 'AppFailure($code${message == null ? '' : ': $message'})';

  /// AI is locked because the application entitlement is inactive.
  const AppFailure.lockedAi({String? action})
      : this(
          AppFailureCode.aiLocked,
          message: 'AI features are locked',
          detail: 'You need an active AI-Birthday subscription to use AI.',
          action: action ?? 'Subscribe to unlock AI.',
        );

  /// No user Gemini credential is configured and no other provider applies.
  const AppFailure.credentialMissing({String? action})
      : this(
          AppFailureCode.aiCredentialMissing,
          message: 'No AI provider is set up',
          detail: 'Add your own Gemini API key, or use on-device AI when available.',
          action: action ?? 'Add a Gemini API key in Settings.',
        );

  /// A configured credential exists but could not be used.
  const AppFailure.credentialInvalid({String? detail, required String action})
      : this(
          AppFailureCode.aiCredentialInvalid,
          message: 'The Gemini API key could not be used',
          detail: detail ?? 'The key may be invalid or the project may have no access to the Gemini API.',
          action: action,
        );

  /// Gemini Nano is not available on this device.
  const AppFailure.nanoUnavailable({String? action})
      : this(
          AppFailureCode.aiNanoUnavailable,
          message: 'On-device AI is unavailable',
          detail: 'Gemini Nano is not available on this device.',
          action: action ?? 'Add a Gemini API key in Settings.',
        );

  /// The AI provider is busy processing another request.
  const AppFailure.busy() : this(AppFailureCode.aiBusy, message: 'AI is busy');

  /// The AI provider quota was exceeded.
  const AppFailure.quotaExceeded({String? action})
      : this(
          AppFailureCode.aiQuotaExceeded,
          message: 'AI quota was exceeded',
          detail: 'The AI provider rejected the request because its usage limit was reached.',
          action: action ?? 'Try again later, or check your Gemini project quota.',
        );

  /// The AI request timed out.
  const AppFailure.timeout() : this(AppFailureCode.aiTimeout, message: 'AI request timed out');

  /// A generic AI provider error occurred.
  const AppFailure.providerError({String? detail})
      : this(
          AppFailureCode.aiProviderError,
          message: 'AI could not complete the request',
          detail: detail,
        );

  /// A required system permission was denied.
  const AppFailure.permissionDenied({String? entity, String? action})
      : this(
          AppFailureCode.permissionDenied,
          message: 'Permission needed',
          detail: entity != null ? 'AI-Birthday needs access to $entity.' : null,
          action: action,
        );

  /// No network connection is available.
  const AppFailure.networkUnavailable()
      : this(
          AppFailureCode.networkUnavailable,
          message: 'No connection',
          detail: 'An internet connection is required for this action.',
        );

  /// A synchronization conflict could not be resolved deterministically.
  const AppFailure.syncConflict({String? detail})
      : this(
          AppFailureCode.syncConflict,
          message: 'Could not sync changes',
          detail: detail,
        );

  /// The requested delivery channel is unavailable.
  const AppFailure.deliveryUnavailable({String? detail, String? action})
      : this(
          AppFailureCode.deliveryUnavailable,
          message: 'This delivery method is unavailable',
          detail: detail,
          action: action,
        );

  /// A delivery operation failed after it was attempted.
  const AppFailure.deliveryFailed({String? detail, String? action})
      : this(
          AppFailureCode.deliveryFailed,
          message: 'Delivery failed',
          detail: detail,
          action: action,
        );

  /// One or more inputs did not validate.
  const AppFailure.validation({String? detail})
      : this(AppFailureCode.validation, message: 'Check your input', detail: detail);

  /// The requested entity could not be found.
  const AppFailure.notFound({String? entity})
      : this(
          AppFailureCode.notFound,
          message: 'Not found',
          detail: entity != null ? '$entity was not found.' : null,
        );
}