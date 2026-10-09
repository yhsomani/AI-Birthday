/// Single source of truth for the Firebase project the app talks to.
///
/// Every endpoint, client ID and key the app uses is derived from these values
/// so a project move is a one-file change. Each value matches
/// `android/app/google-services.json` and can be overridden per build with
/// `--dart-define=NAME=value` (the Firebase web API key keeps its established
/// `FIREBASE_WEB_API_KEY` name).
library;

class FirebaseConfig {
  const FirebaseConfig._();

  static const String projectId = String.fromEnvironment(
    'FIREBASE_PROJECT_ID',
    defaultValue: 'relateai-birthday-ysoman-a2372',
  );

  static const String projectNumber = String.fromEnvironment(
    'FIREBASE_PROJECT_NUMBER',
    defaultValue: '492062763032',
  );

  /// Region of the callable Cloud Functions.
  static const String functionsRegion = String.fromEnvironment(
    'FIREBASE_FUNCTIONS_REGION',
    defaultValue: 'asia-south1',
  );

  /// OAuth web client used as Google Sign-In's `serverClientId`, so ID tokens
  /// carry this project's audience.
  static const String webClientId = String.fromEnvironment(
    'FIREBASE_WEB_CLIENT_ID',
    defaultValue:
        '492062763032-r83kp8a1ksanfi3k8dvhh5n42c9jpohj.apps.googleusercontent.com',
  );

  /// Public web API key of the project. It names the project and is not a
  /// secret; requests still need a signed-in user's ID token.
  static const String webApiKey = String.fromEnvironment(
    'FIREBASE_WEB_API_KEY',
    defaultValue: 'AIzaSyAhV3scPZPa_KBygWgg-57zkZL2QFf8HS4',
  );

  /// Callable Function URL, e.g. `functionUrl('verifyPurchase')`.
  static String functionUrl(String name) =>
      'https://$functionsRegion-$projectId.cloudfunctions.net/$name';
}
