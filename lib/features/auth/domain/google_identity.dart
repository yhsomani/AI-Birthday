/// A signed-in Google identity (SSOT §12 single login).
///
/// Contains no credentials: the ID token is exchanged with Firebase Auth in
/// the device gateway and never retained, logged or synced here.
class GoogleIdentity {
  const GoogleIdentity({
    required this.googleSubject,
    required this.email,
    required this.displayName,
  });

  /// Stable Google `sub` — the identifier used to scope the application
  /// account.
  final String googleSubject;

  final String email;
  final String displayName;
}

/// Result of a sign-in attempt.
sealed class SignInOutcome {
  const SignInOutcome();
}

class SignInSuccess extends SignInOutcome {
  const SignInSuccess(this.identity);
  final GoogleIdentity identity;
}

/// Google/Firebase are not configured on this build or device state.
class SignInUnavailable extends SignInOutcome {
  const SignInUnavailable({this.reason});
  final String? reason;
}

/// The user cancelled the account chooser or sign-in failed.
class SignInFailed extends SignInOutcome {
  const SignInFailed([this.message]);
  final String? message;
}
