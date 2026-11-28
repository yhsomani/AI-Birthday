// ignore_for_file: depend_on_referenced_packages

/// Opaque-box OS, plugin, and MethodChannel boundary fakes for E2E tests.
///
/// Zero `lib/features/` business-logic classes are mocked here. Instead, this
/// file intercepts:
/// - `plugins.it_nomads.com/flutter_secure_storage` (`MethodChannel`)
/// - `SystemChannels.platform` (`Clipboard`, `HapticFeedback`)
/// - `UrlLauncherPlatform` + `plugins.flutter.io/url_launcher`
/// - `GoogleSignInPlatform`
/// - `InAppPurchasePlatform`
/// - `FirebasePlatform` + `FirebaseAppCheckPlatform`
/// - `FilePicker`
/// - The 5 custom `com.yashsomani.ai_birthday/*` MethodChannels
/// - Thin channel-forwarding bridges ONLY for the `WidgetsFlutterBinding`-gated
///   OS adapters so real payload serialization and channel handlers execute
///   under `TestWidgetsFlutterBinding`.
library;

import 'dart:async';
import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:firebase_app_check_platform_interface/firebase_app_check_platform_interface.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_core_platform_interface/firebase_core_platform_interface.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_sign_in_platform_interface/google_sign_in_platform_interface.dart';
import 'package:in_app_purchase_platform_interface/in_app_purchase_platform_interface.dart';
import 'package:url_launcher_platform_interface/link.dart';
import 'package:url_launcher_platform_interface/url_launcher_platform_interface.dart';

import 'package:ai_birthday/core/config/firebase_config.dart';
import 'package:ai_birthday/features/delivery/data/native_share_service.dart';
import 'package:ai_birthday/features/delivery/data/native_sms_gateway.dart';
import 'package:ai_birthday/features/people/data/device_contacts_service.dart';
import 'package:ai_birthday/features/reminders/application/notification_scheduler_gateway.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_schedule.dart';

// -----------------------------------------------------------------------------
// 1. Secure Storage MethodChannel Fake
// -----------------------------------------------------------------------------

class FakeSecureStorageChannel {
  static const MethodChannel channel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );

  final Map<String, String> values = {};
  final Set<String> failOnReadKeys = {};
  bool failAllReads = false;
  bool failWrites = false;
  bool failDeletes = false;

  void install() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, _handleCall);
  }

  void uninstall() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  }

  Future<dynamic> _handleCall(MethodCall call) async {
    final args = call.arguments is Map ? call.arguments as Map : const {};
    final key = args['key']?.toString();
    switch (call.method) {
      case 'read':
        if (failAllReads || (key != null && failOnReadKeys.contains(key))) {
          throw PlatformException(
            code: 'keystore_read_error',
            message: 'Simulated keystore read failure',
          );
        }
        return key == null ? null : values[key];
      case 'write':
        if (failWrites) {
          throw PlatformException(
            code: 'keystore_write_error',
            message: 'Simulated keystore write failure',
          );
        }
        if (key != null) {
          final value = args['value']?.toString();
          if (value == null) {
            values.remove(key);
          } else {
            values[key] = value;
          }
        }
        return null;
      case 'delete':
        if (failDeletes) {
          throw PlatformException(
            code: 'keystore_delete_error',
            message: 'Simulated keystore delete failure',
          );
        }
        if (key != null) values.remove(key);
        return null;
      case 'readAll':
        if (failAllReads) {
          throw PlatformException(
            code: 'keystore_read_error',
            message: 'Simulated keystore readAll failure',
          );
        }
        return Map<String, String>.from(values);
      case 'deleteAll':
        if (failDeletes) {
          throw PlatformException(
            code: 'keystore_delete_error',
            message: 'Simulated keystore deleteAll failure',
          );
        }
        values.clear();
        return null;
      case 'containsKey':
        if (failAllReads || (key != null && failOnReadKeys.contains(key))) {
          throw PlatformException(
            code: 'keystore_read_error',
            message: 'Simulated keystore containsKey failure',
          );
        }
        return key != null && values.containsKey(key);
      default:
        return null;
    }
  }
}

// -----------------------------------------------------------------------------
// 2. SystemChannels.platform Fake (Clipboard + HapticFeedback)
// -----------------------------------------------------------------------------

class FakePlatformSystemChannel {
  String? clipboardText;
  final List<String> clipboardHistory = [];
  final List<String> hapticCalls = [];
  bool failClipboardWrite = false;

  void install() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, _handleCall);
  }

  void uninstall() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  }

  Future<dynamic> _handleCall(MethodCall call) async {
    switch (call.method) {
      case 'Clipboard.setData':
        if (failClipboardWrite) {
          throw PlatformException(
            code: 'clipboard_error',
            message: 'Simulated clipboard write failure',
          );
        }
        final args = call.arguments as Map?;
        final text = args?['text']?.toString();
        clipboardText = text;
        if (text != null) clipboardHistory.add(text);
        return null;
      case 'Clipboard.getData':
        return clipboardText == null
            ? null
            : <String, dynamic>{'text': clipboardText};
      case 'HapticFeedback.vibrate':
        hapticCalls.add(call.arguments?.toString() ?? 'default');
        return null;
      default:
        return null;
    }
  }
}

// -----------------------------------------------------------------------------
// 3. UrlLauncher Fake
// -----------------------------------------------------------------------------

class FakeUrlLauncher extends UrlLauncherPlatform {
  static const MethodChannel _fallbackChannel = MethodChannel(
    'plugins.flutter.io/url_launcher',
  );

  final List<String> canLaunchChecks = [];
  final List<({String url, PreferredLaunchMode mode})> launchedUrls = [];

  bool Function(String url)? canLaunchPredicate;
  bool Function(String url, LaunchOptions options)? launchPredicate;
  Set<PreferredLaunchMode>? supportedModes;
  Object? launchError;
  UrlLauncherPlatform? _previous;

  void install() {
    _previous = UrlLauncherPlatform.instance;
    UrlLauncherPlatform.instance = this;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_fallbackChannel, _handleChannelFallback);
  }

  void uninstall() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_fallbackChannel, null);
    if (_previous != null) {
      UrlLauncherPlatform.instance = _previous!;
    }
  }

  @override
  LinkDelegate? get linkDelegate => null;

  @override
  Future<bool> supportsMode(PreferredLaunchMode mode) async {
    if (supportedModes != null) {
      return supportedModes!.contains(mode);
    }
    return true;
  }

  @override
  Future<bool> canLaunch(String url) async {
    canLaunchChecks.add(url);
    return canLaunchPredicate?.call(url) ?? true;
  }

  @override
  Future<bool> launch(
    String url, {
    required bool useSafariVC,
    required bool useWebView,
    required bool enableJavaScript,
    required bool enableDomStorage,
    required bool universalLinksOnly,
    required Map<String, String> headers,
    String? webOnlyWindowName,
  }) {
    return launchUrl(
      url,
      LaunchOptions(
        mode: universalLinksOnly
            ? PreferredLaunchMode.externalNonBrowserApplication
            : (useWebView
                  ? PreferredLaunchMode.inAppWebView
                  : PreferredLaunchMode.externalApplication),
        webViewConfiguration: InAppWebViewConfiguration(
          enableJavaScript: enableJavaScript,
          enableDomStorage: enableDomStorage,
          headers: headers,
        ),
        webOnlyWindowName: webOnlyWindowName,
      ),
    );
  }

  @override
  Future<bool> launchUrl(String url, LaunchOptions options) async {
    if (launchError != null) throw launchError!;
    final result = launchPredicate?.call(url, options) ?? true;
    if (result) {
      launchedUrls.add((url: url, mode: options.mode));
    }
    return result;
  }

  Future<dynamic> _handleChannelFallback(MethodCall call) async {
    final args = call.arguments is Map ? call.arguments as Map : const {};
    final url = args['url']?.toString() ?? '';
    switch (call.method) {
      case 'canLaunch':
        return canLaunch(url);
      case 'launch':
        return launchUrl(url, const LaunchOptions());
      default:
        return null;
    }
  }
}

// -----------------------------------------------------------------------------
// 4. GoogleSignInPlatform Fake (3.1.0 contract)
// -----------------------------------------------------------------------------

class FakeGoogleSignIn extends GoogleSignInPlatform {
  AuthenticationResults? nextResult = const AuthenticationResults(
    user: GoogleSignInUserData(
      email: 'test.user@example.com',
      id: 'google-sub-123',
      displayName: 'Test User',
      photoUrl: 'https://example.com/avatar.png',
    ),
    authenticationTokens: AuthenticationTokenData(
      idToken: 'mock-google-id-token',
    ),
  );
  Object? nextError;
  Object? initError;
  bool cancelNext = false;
  int initCalls = 0;
  int authenticateCalls = 0;
  int signOutCalls = 0;
  int disconnectCalls = 0;
  GoogleSignInPlatform? _previous;

  void install() {
    _previous = GoogleSignInPlatform.instance;
    GoogleSignInPlatform.instance = this;
  }

  void uninstall() {
    if (_previous != null) {
      GoogleSignInPlatform.instance = _previous!;
    }
  }

  @override
  Future<void> init(InitParameters params) async {
    initCalls++;
    if (initError != null) throw initError!;
  }

  @override
  Future<AuthenticationResults?>? attemptLightweightAuthentication(
    AttemptLightweightAuthenticationParameters params,
  ) async => null;

  @override
  bool supportsAuthenticate() => true;

  @override
  Future<AuthenticationResults> authenticate(
    AuthenticateParameters params,
  ) async {
    authenticateCalls++;
    if (nextError != null) throw nextError!;
    if (cancelNext || nextResult == null) {
      throw const GoogleSignInException(
        code: GoogleSignInExceptionCode.canceled,
        description: 'User cancelled sign-in',
      );
    }
    return nextResult!;
  }

  @override
  bool authorizationRequiresUserInteraction() => false;

  @override
  Future<ClientAuthorizationTokenData?> clientAuthorizationTokensForScopes(
    ClientAuthorizationTokensForScopesParameters params,
  ) async => null;

  @override
  Future<ServerAuthorizationTokenData?> serverAuthorizationTokensForScopes(
    ServerAuthorizationTokensForScopesParameters params,
  ) async => null;

  @override
  Future<void> signOut(SignOutParams params) async {
    signOutCalls++;
  }

  @override
  Future<void> disconnect(DisconnectParams params) async {
    disconnectCalls++;
  }
}

// -----------------------------------------------------------------------------
// 5. InAppPurchasePlatform Fake (avoids static late read-before-write trap)
// -----------------------------------------------------------------------------

class FakeInAppPurchase extends InAppPurchasePlatform {
  final StreamController<List<PurchaseDetails>> _purchaseController =
      StreamController<List<PurchaseDetails>>.broadcast();

  bool available = true;
  List<ProductDetails> products = [
    ProductDetails(
      id: 'ai_birthday_pro_monthly',
      title: 'AI-Birthday Pro',
      description: 'Monthly Pro subscription',
      price: r'$2.99',
      rawPrice: 2.99,
      currencyCode: 'USD',
      currencySymbol: r'$',
    ),
  ];
  bool buyResult = true;
  bool autoEmitPurchasedOnBuy = true;
  List<PurchaseDetails> restorablePurchases = [];
  final List<String> completedPurchaseIds = [];
  final List<String?> restoreCalls = [];
  final List<PurchaseParam> buyCalls = [];

  InAppPurchasePlatform? _previous;

  void install() {
    // InAppPurchasePlatform._instance is `static late` and throws
    // LateInitializationError if read before initialization.
    try {
      _previous = InAppPurchasePlatform.instance;
    } catch (_) {
      _previous = null;
    }
    InAppPurchasePlatform.instance = this;
  }

  Future<void> uninstall() async {
    if (_previous != null) {
      InAppPurchasePlatform.instance = _previous!;
    }
    await _purchaseController.close();
  }

  void emitPurchases(List<PurchaseDetails> details) {
    if (!_purchaseController.isClosed) {
      _purchaseController.add(details);
    }
  }

  @override
  Stream<List<PurchaseDetails>> get purchaseStream =>
      _purchaseController.stream;

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<ProductDetailsResponse> queryProductDetails(
    Set<String> identifiers,
  ) async {
    final matched = products.where((p) => identifiers.contains(p.id)).toList();
    final foundIds = matched.map((p) => p.id).toSet();
    final missing = identifiers.where((id) => !foundIds.contains(id)).toList();
    return ProductDetailsResponse(
      productDetails: matched,
      notFoundIDs: missing,
    );
  }

  @override
  Future<bool> buyNonConsumable({required PurchaseParam purchaseParam}) async {
    buyCalls.add(purchaseParam);
    if (!buyResult) return false;
    if (autoEmitPurchasedOnBuy) {
      scheduleMicrotask(() {
        emitPurchases([
          PurchaseDetails(
            purchaseID: 'fake-tx-${buyCalls.length}',
            productID: purchaseParam.productDetails.id,
            verificationData: PurchaseVerificationData(
              localVerificationData: 'fake-local-receipt',
              serverVerificationData: 'fake-purchase-token-${buyCalls.length}',
              source: 'google_play',
            ),
            transactionDate: '${DateTime.now().millisecondsSinceEpoch}',
            status: PurchaseStatus.purchased,
          )..pendingCompletePurchase = true,
        ]);
      });
    }
    return true;
  }

  @override
  Future<bool> buyConsumable({
    required PurchaseParam purchaseParam,
    bool autoConsume = true,
  }) => buyNonConsumable(purchaseParam: purchaseParam);

  @override
  Future<void> completePurchase(PurchaseDetails purchase) async {
    if (purchase.purchaseID != null) {
      completedPurchaseIds.add(purchase.purchaseID!);
    }
  }

  @override
  Future<void> restorePurchases({String? applicationUserName}) async {
    restoreCalls.add(applicationUserName);
    if (restorablePurchases.isNotEmpty) {
      scheduleMicrotask(() {
        emitPurchases(restorablePurchases);
      });
    } else {
      scheduleMicrotask(() {
        emitPurchases(const []);
      });
    }
  }

  @override
  Future<String> countryCode() async => 'US';
}

// -----------------------------------------------------------------------------
// 6. Firebase Core & Firebase App Check Fakes
// -----------------------------------------------------------------------------

class FakeFirebaseAppPlatform extends FirebaseAppPlatform {
  FakeFirebaseAppPlatform(super.name, super.options);

  bool _autoCollection = false;

  @override
  bool get isAutomaticDataCollectionEnabled => _autoCollection;

  @override
  Future<void> delete() async {}

  @override
  Future<void> setAutomaticDataCollectionEnabled(bool enabled) async {
    _autoCollection = enabled;
  }

  @override
  Future<void> setAutomaticResourceManagementEnabled(bool enabled) async {}
}

class FakeFirebasePlatform extends FirebasePlatform {
  FakeFirebasePlatform() {
    _apps[defaultFirebaseAppName] = FakeFirebaseAppPlatform(
      defaultFirebaseAppName,
      const FirebaseOptions(
        apiKey: FirebaseConfig.webApiKey,
        appId: FirebaseConfig.androidAppId,
        messagingSenderId: FirebaseConfig.projectNumber,
        projectId: FirebaseConfig.projectId,
      ),
    );
  }

  final Map<String, FirebaseAppPlatform> _apps = {};

  @override
  List<FirebaseAppPlatform> get apps => _apps.values.toList();

  @override
  Future<FirebaseAppPlatform> initializeApp({
    String? name,
    FirebaseOptions? options,
  }) async {
    final appName = name ?? defaultFirebaseAppName;
    final existing = _apps[appName];
    if (existing != null) return existing;
    final created = FakeFirebaseAppPlatform(
      appName,
      options ??
          const FirebaseOptions(
            apiKey: FirebaseConfig.webApiKey,
            appId: FirebaseConfig.androidAppId,
            messagingSenderId: FirebaseConfig.projectNumber,
            projectId: FirebaseConfig.projectId,
          ),
    );
    _apps[appName] = created;
    return created;
  }

  @override
  FirebaseAppPlatform app([String name = defaultFirebaseAppName]) {
    return _apps.putIfAbsent(
      name,
      () => FakeFirebaseAppPlatform(
        name,
        const FirebaseOptions(
          apiKey: FirebaseConfig.webApiKey,
          appId: FirebaseConfig.androidAppId,
          messagingSenderId: FirebaseConfig.projectNumber,
          projectId: FirebaseConfig.projectId,
        ),
      ),
    );
  }
}

class FakeFirebaseAppCheckPlatform extends FirebaseAppCheckPlatform {
  FakeFirebaseAppCheckPlatform({super.appInstance});

  final StreamController<String?> _tokenController =
      StreamController<String?>.broadcast();

  String? token = 'fake-app-check-token';
  String? limitedUseToken = 'fake-limited-app-check-token';
  Object? tokenError;
  Object? limitedUseTokenError;
  bool activated = false;
  bool autoRefreshEnabled = false;

  @override
  FirebaseAppCheckPlatform delegateFor({required FirebaseApp app}) => this;

  @override
  FirebaseAppCheckPlatform setInitialValues() => this;

  @override
  Future<void> activate({
    WebProvider? providerWeb,
    AndroidAppCheckProvider? providerAndroid,
    AppleAppCheckProvider? providerApple,
    WindowsAppCheckProvider? providerWindows,
  }) async {
    activated = true;
  }

  @override
  Future<void> setTokenAutoRefreshEnabled(
    bool isTokenAutoRefreshEnabled,
  ) async {
    autoRefreshEnabled = isTokenAutoRefreshEnabled;
  }

  @override
  Future<String?> getToken(bool forceRefresh) async {
    if (tokenError != null) throw tokenError!;
    return token;
  }

  @override
  Future<AppCheckTokenResult?> getTokenResult(bool forceRefresh) async {
    if (tokenError != null) throw tokenError!;
    if (token == null) return null;
    return AppCheckTokenResult(
      token: token!,
      expirationTime: DateTime.now().toUtc().add(const Duration(hours: 1)),
    );
  }

  @override
  Stream<String?> get onTokenChange => _tokenController.stream;

  @override
  Future<String> getLimitedUseToken() async {
    if (limitedUseTokenError != null) throw limitedUseTokenError!;
    return limitedUseToken ?? '';
  }

  @override
  Future<void> dispose() async {
    await _tokenController.close();
  }
}

class FakeFirebaseCoreAndAppCheck {
  final FakeFirebasePlatform firebase = FakeFirebasePlatform();
  final FakeFirebaseAppCheckPlatform appCheck = FakeFirebaseAppCheckPlatform();
  FirebasePlatform? _previousFirebase;
  FirebaseAppCheckPlatform? _previousAppCheck;

  void install() {
    _previousFirebase = FirebasePlatform.instance;
    try {
      _previousAppCheck = FirebaseAppCheckPlatform.instance;
    } catch (_) {
      _previousAppCheck = null;
    }
    // Assign FirebasePlatform BEFORE FirebaseAppCheckPlatform so
    // Firebase.app() resolves to FakeFirebasePlatform.
    FirebasePlatform.instance = firebase;
    FirebaseAppCheckPlatform.instance = appCheck;
  }

  Future<void> uninstall() async {
    await appCheck.dispose();
    if (_previousAppCheck != null) {
      FirebaseAppCheckPlatform.instance = _previousAppCheck!;
    }
    if (_previousFirebase != null) {
      FirebasePlatform.instance = _previousFirebase!;
    }
  }
}

// -----------------------------------------------------------------------------
// 7. FilePicker Fake (avoids static late read-before-write trap)
// -----------------------------------------------------------------------------

class FakeFilePicker extends FilePicker {
  FilePickerResult? nextPickResult;
  String? nextSavePath = '/fake/ai_birthday_export.csv';
  Object? failure;
  Uint8List? savedBytes;
  String? lastSavedFileName;
  int pickCalls = 0;
  int saveCalls = 0;
  FilePicker? _previous;

  void install() {
    // FilePicker._instance is `static late`; never read without try/catch.
    try {
      _previous = FilePicker.platform;
    } catch (_) {
      _previous = null;
    }
    FilePicker.platform = this;
  }

  void uninstall() {
    if (_previous != null) {
      FilePicker.platform = _previous!;
    }
  }

  void setCsvBytes(String filename, Uint8List bytes) {
    nextPickResult = FilePickerResult([
      PlatformFile(name: filename, size: bytes.length, bytes: bytes),
    ]);
  }

  void setCsvText(String filename, String csvText) {
    setCsvBytes(filename, Uint8List.fromList(utf8.encode(csvText)));
  }

  @override
  Future<FilePickerResult?> pickFiles({
    String? dialogTitle,
    String? initialDirectory,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    Function(FilePickerStatus)? onFileLoading,
    bool allowCompression = false,
    int compressionQuality = 0,
    bool allowMultiple = false,
    bool withData = false,
    bool withReadStream = false,
    bool lockParentWindow = false,
    bool readSequential = false,
  }) async {
    pickCalls++;
    if (failure != null) throw failure!;
    return nextPickResult;
  }

  @override
  Future<String?> saveFile({
    String? dialogTitle,
    String? fileName,
    String? initialDirectory,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    Uint8List? bytes,
    bool lockParentWindow = false,
  }) async {
    saveCalls++;
    savedBytes = bytes;
    lastSavedFileName = fileName;
    if (failure != null) throw failure!;
    return nextSavePath;
  }
}

// -----------------------------------------------------------------------------
// 8. Custom com.yashsomani.ai_birthday/* MethodChannels Fake
// -----------------------------------------------------------------------------

class FakeAndroidChannels {
  static const MethodChannel notificationsChannel = MethodChannel(
    'com.yashsomani.ai_birthday/notifications',
  );
  static const MethodChannel nanoChannel = MethodChannel(
    'com.yashsomani.ai_birthday/nano',
  );
  static const MethodChannel shareChannel = MethodChannel(
    'com.yashsomani.ai_birthday/share',
  );
  static const MethodChannel contactsChannel = MethodChannel(
    'com.yashsomani.ai_birthday/contacts',
  );
  static const MethodChannel smsChannel = MethodChannel(
    'com.yashsomani.ai_birthday/sms_automation',
  );

  // Notifications state
  String? initialNotificationPersonId;
  bool notificationPermission = true;
  bool requestNotificationPermissionResult = true;
  bool exactAlarmPermission = true;
  bool requestExactAlarmPermissionResult = false;
  bool applyNotificationsResult = true;
  bool cancelAllNotificationsResult = true;
  bool testNotificationResult = true;
  final List<List<Map<String, dynamic>>> appliedTriggerBatches = [];
  int cancelAllNotificationsCount = 0;
  final List<Map<String, dynamic>> testNotifications = [];

  // Gemini Nano state
  String nanoState = 'unavailable';
  String nanoDownloadNextState = 'downloading';
  String nanoGeneratedText = 'Happy Birthday! Wishing you a wonderful day.';
  PlatformException? nanoGenerateError;
  final List<String> nanoPrompts = [];

  // Native Share state
  bool shareResult = true;
  Object? shareError;
  final List<({String text, String? title})> sharedItems = [];
  bool platformShareResult = true;
  Object? platformShareError;
  final List<({String platform, String text, String? title})>
  platformSharedItems = [];

  // Contacts state
  bool contactsPermission = true;
  bool requestContactsPermissionResult = true;
  List<Map<String, dynamic>> deviceContacts = [];
  PlatformException? fetchContactsError;

  // SMS automation state
  bool smsAutomationSupported = false;
  bool hasSmsPermission = false;
  bool requestSmsPermissionResult = false;
  final List<Map<String, dynamic>> scheduledSmsJobs = [];
  final List<String> cancelledSmsJobIds = [];
  int cancelAllSmsCount = 0;
  String smsAttemptsLogJson = '[]';

  void install() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      notificationsChannel,
      _handleNotifications,
    );
    messenger.setMockMethodCallHandler(nanoChannel, _handleNano);
    messenger.setMockMethodCallHandler(shareChannel, _handleShare);
    messenger.setMockMethodCallHandler(contactsChannel, _handleContacts);
    messenger.setMockMethodCallHandler(smsChannel, _handleSmsAutomation);
  }

  void uninstall() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(notificationsChannel, null);
    messenger.setMockMethodCallHandler(nanoChannel, null);
    messenger.setMockMethodCallHandler(shareChannel, null);
    messenger.setMockMethodCallHandler(contactsChannel, null);
    messenger.setMockMethodCallHandler(smsChannel, null);
  }

  /// Simulates the Android OS tapping a birthday notification (`onNotificationOpened`)
  /// and delivering the method call from native Kotlin into Dart.
  Future<void> simulateNotificationTap(
    WidgetTester tester,
    String personId,
  ) async {
    final encoded = const StandardMethodCodec().encodeMethodCall(
      MethodCall('onNotificationOpened', <String, dynamic>{
        'personId': personId,
      }),
    );
    await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .handlePlatformMessage(notificationsChannel.name, encoded, (_) {});
    await tester.pumpAndSettle();
  }

  Future<dynamic> _handleNotifications(MethodCall call) async {
    final args = call.arguments is Map ? call.arguments as Map : const {};
    switch (call.method) {
      case 'getInitialNotification':
        final id = initialNotificationPersonId;
        if (id == null) return null;
        return <String, String>{'personId': id};
      case 'hasPermission':
        return notificationPermission;
      case 'requestPermission':
        notificationPermission = requestNotificationPermissionResult;
        return requestNotificationPermissionResult;
      case 'hasExactAlarmPermission':
        return exactAlarmPermission;
      case 'requestExactAlarmPermission':
        exactAlarmPermission = requestExactAlarmPermissionResult;
        return requestExactAlarmPermissionResult;
      case 'apply':
        final raw = args['triggers'] as List? ?? const [];
        final batch = raw
            .whereType<Map>()
            .map((m) => Map<String, dynamic>.from(m))
            .toList();
        appliedTriggerBatches.add(batch);
        return applyNotificationsResult;
      case 'cancelAll':
        cancelAllNotificationsCount++;
        return cancelAllNotificationsResult;
      case 'testNotification':
        testNotifications.add(Map<String, dynamic>.from(args));
        return testNotificationResult;
      default:
        return null;
    }
  }

  Future<dynamic> _handleNano(MethodCall call) async {
    final args = call.arguments is Map ? call.arguments as Map : const {};
    switch (call.method) {
      case 'currentState':
        return nanoState;
      case 'startDownload':
        nanoState = nanoDownloadNextState;
        return nanoState;
      case 'generate':
        final prompt = args['prompt']?.toString() ?? '';
        nanoPrompts.add(prompt);
        if (nanoGenerateError != null) throw nanoGenerateError!;
        return nanoGeneratedText;
      default:
        return null;
    }
  }

  Future<dynamic> _handleShare(MethodCall call) async {
    final args = call.arguments is Map ? call.arguments as Map : const {};
    if (call.method == 'shareText') {
      if (shareError != null) throw shareError!;
      sharedItems.add((
        text: args['text']?.toString() ?? '',
        title: args['title']?.toString(),
      ));
      return shareResult;
    }
    if (call.method == 'shareToPlatform') {
      if (platformShareError != null) throw platformShareError!;
      platformSharedItems.add((
        platform: args['platform']?.toString() ?? '',
        text: args['text']?.toString() ?? '',
        title: args['title']?.toString(),
      ));
      return platformShareResult;
    }
    return null;
  }

  Future<dynamic> _handleContacts(MethodCall call) async {
    switch (call.method) {
      case 'hasPermission':
        return contactsPermission;
      case 'requestPermission':
        contactsPermission = requestContactsPermissionResult;
        return requestContactsPermissionResult;
      case 'fetchDeviceContacts':
        if (fetchContactsError != null) throw fetchContactsError!;
        return deviceContacts;
      default:
        return null;
    }
  }

  Future<dynamic> _handleSmsAutomation(MethodCall call) async {
    final args = call.arguments is Map ? call.arguments as Map : const {};
    switch (call.method) {
      case 'isSupported':
        return smsAutomationSupported;
      case 'hasSmsPermission':
        return hasSmsPermission;
      case 'requestSmsPermission':
        hasSmsPermission = requestSmsPermissionResult;
        return requestSmsPermissionResult;
      case 'scheduleSmsJob':
        final job = args['job'];
        if (job is Map) {
          scheduledSmsJobs.add(Map<String, dynamic>.from(job));
        }
        return true;
      case 'cancelSmsJob':
        final jobId = args['jobId']?.toString();
        if (jobId != null) cancelledSmsJobIds.add(jobId);
        return true;
      case 'cancelAllSmsJobs':
        cancelAllSmsCount++;
        return true;
      case 'cancelSmsJobsForPerson':
        return true;
      case 'getSmsAttemptsLog':
        return smsAttemptsLogJson;
      default:
        return null;
    }
  }
}

// -----------------------------------------------------------------------------
// 9. Thin Channel-Forwarding Bridges for TestWidgetsFlutterBinding
// -----------------------------------------------------------------------------

/// Thin bridge forwarding directly to `com.yashsomani.ai_birthday/notifications`
/// without the production `WidgetsBinding.instance is WidgetsFlutterBinding`
/// check (since `TestWidgetsFlutterBinding` does not extend `WidgetsFlutterBinding`).
class ChannelForwardingNotificationGateway
    implements
        NotificationSchedulerGateway,
        ClearableNotificationSchedulerGateway {
  const ChannelForwardingNotificationGateway();

  static const MethodChannel _channel =
      FakeAndroidChannels.notificationsChannel;

  @override
  Future<bool> hasPermission() async {
    try {
      return await _channel.invokeMethod<bool>('hasPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> hasExactAlarmPermission() async {
    try {
      return await _channel.invokeMethod<bool>('hasExactAlarmPermission') ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> requestExactAlarmPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestExactAlarmPermission') ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> requestPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> apply(ReminderPlan plan, {bool hideNames = false}) async {
    try {
      final triggers = plan.active.map((t) {
        final whenText = switch (t.kind) {
          ReminderKind.birthday => 'Today is their birthday! 🎉',
          ReminderKind.ready => 'Birthday is tomorrow!',
          ReminderKind.prepare => 'Birthday is in 2 days.',
          ReminderKind.approaching => 'Birthday is in 7 days.',
        };
        final title = hideNames
            ? (t.kind == ReminderKind.birthday
                  ? "🎉 It's a Birthday Today!"
                  : 'Upcoming Birthday')
            : (t.kind == ReminderKind.birthday
                  ? "🎉 It's ${t.personName}'s Birthday!"
                  : "Upcoming: ${t.personName}'s Birthday");
        return {
          'id': _stableNotificationId('${t.personId}_${t.kind.name}'),
          'personId': t.personId,
          'personName': hideNames ? '' : t.personName,
          'kind': t.kind.name,
          'title': title,
          'body': whenText,
          'timestampMs': t.at.millisecondsSinceEpoch,
        };
      }).toList();
      return await _channel.invokeMethod<bool>('apply', {
            'triggers': triggers,
          }) ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> cancelAll() async {
    try {
      return await _channel.invokeMethod<bool>('cancelAll') ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> sendTestNotification({
    required String title,
    required String body,
  }) async {
    try {
      return await _channel.invokeMethod<bool>('testNotification', {
            'title': title,
            'body': body,
          }) ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<String?> getInitialNotificationPersonId() async {
    try {
      final res = await _channel.invokeMapMethod<String, dynamic>(
        'getInitialNotification',
      );
      return res?['personId'] as String?;
    } catch (_) {
      return null;
    }
  }

  @override
  void setNotificationOpenedHandler(void Function(String personId) onOpened) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onNotificationOpened') {
        final args = call.arguments as Map<dynamic, dynamic>?;
        final personId = args?['personId'] as String?;
        if (personId != null && personId.isNotEmpty) {
          onOpened(personId);
        }
      }
    });
  }

  @override
  void clearNotificationOpenedHandler() {
    _channel.setMethodCallHandler(null);
  }
}

int _stableNotificationId(String value) {
  var hash = 0x811C9DC5;
  for (final unit in value.codeUnits) {
    hash ^= unit;
    hash = (hash * 0x01000193) & 0x7FFFFFFF;
  }
  return hash == 0 ? 1 : hash;
}

/// Thin bridge forwarding directly to `com.yashsomani.ai_birthday/share`.
class ChannelForwardingShareService extends NativeShareService {
  const ChannelForwardingShareService();

  @override
  Future<bool> shareText({required String text, String? title}) async {
    try {
      final success = await FakeAndroidChannels.shareChannel.invokeMethod<bool>(
        'shareText',
        {'text': text, 'title': title ?? 'Share Birthday Message'},
      );
      return success ?? false;
    } catch (_) {
      return false;
    }
  }
}

/// Thin bridge forwarding directly to `com.yashsomani.ai_birthday/contacts`.
class ChannelForwardingContactsService extends DeviceContactsService {
  const ChannelForwardingContactsService();

  @override
  Future<bool> hasPermission() async {
    try {
      return await FakeAndroidChannels.contactsChannel.invokeMethod<bool>(
            'hasPermission',
          ) ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> requestPermission() async {
    try {
      return await FakeAndroidChannels.contactsChannel.invokeMethod<bool>(
            'requestPermission',
          ) ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<List<DeviceContact>> fetchDeviceContacts() async {
    try {
      final result = await FakeAndroidChannels.contactsChannel
          .invokeListMethod<dynamic>('fetchDeviceContacts');
      if (result == null) return [];
      return result
          .whereType<Map<dynamic, dynamic>>()
          .map(DeviceContact.fromMap)
          .where((c) => c.name.isNotEmpty)
          .toList();
    } catch (_) {
      return [];
    }
  }
}

/// Thin bridge forwarding directly to `com.yashsomani.ai_birthday/sms_automation`.
class ChannelForwardingSmsGateway implements NativeSmsGateway {
  const ChannelForwardingSmsGateway();

  static const MethodChannel _channel = FakeAndroidChannels.smsChannel;

  @override
  Future<bool> isSupported() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      return false;
    }
    try {
      return await _channel.invokeMethod<bool>('isSupported') ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> hasSmsPermission() async {
    try {
      return await _channel.invokeMethod<bool>('hasSmsPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> requestSmsPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestSmsPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> scheduleSmsJob(ScheduledSmsJob job) async {
    try {
      return await _channel.invokeMethod<bool>('scheduleSmsJob', {
            'job': job.toMap(),
          }) ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> cancelSmsJob(String jobId) async {
    try {
      return await _channel.invokeMethod<bool>('cancelSmsJob', {
            'jobId': jobId,
          }) ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> cancelAllSmsJobs() async {
    try {
      return await _channel.invokeMethod<bool>('cancelAllSmsJobs') ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> cancelSmsJobsForPerson(
    String personId, {
    String? exceptJobId,
  }) async {
    try {
      return await _channel.invokeMethod<bool>('cancelSmsJobsForPerson', {
            'personId': personId,
            'exceptJobId': ?exceptJobId,
          }) ??
          false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<String> getSmsAttemptsLog() async {
    try {
      return await _channel.invokeMethod<String>('getSmsAttemptsLog') ?? '[]';
    } catch (_) {
      return '[]';
    }
  }
}

// -----------------------------------------------------------------------------
// 10. Unified PlatformFakes Bundle
// -----------------------------------------------------------------------------

class PlatformFakes {
  final FakeSecureStorageChannel secureStorage = FakeSecureStorageChannel();
  final FakePlatformSystemChannel system = FakePlatformSystemChannel();
  final FakeUrlLauncher urlLauncher = FakeUrlLauncher();
  final FakeGoogleSignIn googleSignIn = FakeGoogleSignIn();
  final FakeInAppPurchase inAppPurchase = FakeInAppPurchase();
  final FakeFirebaseCoreAndAppCheck firebase = FakeFirebaseCoreAndAppCheck();
  final FakeFilePicker filePicker = FakeFilePicker();
  final FakeAndroidChannels androidChannels = FakeAndroidChannels();

  bool _installed = false;

  void install() {
    if (_installed) return;
    _installed = true;
    secureStorage.install();
    system.install();
    urlLauncher.install();
    googleSignIn.install();
    inAppPurchase.install();
    firebase.install();
    filePicker.install();
    androidChannels.install();
  }

  Future<void> uninstall() async {
    if (!_installed) return;
    _installed = false;
    androidChannels.uninstall();
    filePicker.uninstall();
    await firebase.uninstall();
    await inAppPurchase.uninstall();
    googleSignIn.uninstall();
    urlLauncher.uninstall();
    system.uninstall();
    secureStorage.uninstall();
  }
}
