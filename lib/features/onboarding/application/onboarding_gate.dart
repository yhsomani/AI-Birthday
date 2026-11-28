import 'package:ai_birthday/core/security/credential_storage.dart';

/// In-memory cache of the onboarding flag so navigation does not hit secure
/// storage on every redirect.
///
/// Only a positive result (or explicit session bypass after a persistent write
/// error) is cached. A read failure lets the user through and retries later,
/// instead of trapping returning users in onboarding on a flaky Keystore.
class OnboardingGate {
  OnboardingGate(this._storage);

  final CredentialStorage _storage;
  bool _completed = false;

  Future<bool> isCompleted() async {
    if (_completed) return true;
    try {
      _completed = await _storage.hasCompletedOnboarding();
    } catch (_) {
      return true;
    }
    return _completed;
  }

  /// Marks onboarding completed for the current session so a persistent
  /// secure-storage write failure never traps the user on `/onboarding`.
  void markSessionCompleted() {
    _completed = true;
  }
}
