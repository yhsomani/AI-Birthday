/// Single coordinator for opaque-box E2E tests in `test/e2e/`.
///
/// Boots the real [AiBirthdayApp] inside [ProviderScope] with an in-memory Drift
/// database (`NativeDatabase.memory()`), [RecordingLogger], scoped
/// [FakeHttpOverrides], [PlatformFakes], and thin `MethodChannel`-forwarding
/// bridges ONLY for the 5 `WidgetsFlutterBinding`-gated OS adapters.
///
/// Zero `lib/features/` business-logic classes are mocked.
library;

import 'package:clock/clock.dart' as clock;
import 'package:drift/native.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/core_providers.dart' as core;
import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/delivery/application/birthday_sms_service.dart';
import 'package:ai_birthday/features/reminders/application/reminder_bootstrap.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';

import 'fake_http_overrides.dart';
import 'platform_fakes.dart';
import 'responsive_helpers.dart';

/// Coordinates lifecycle, boundary fakes, database, logger, and teardown for an
/// opaque-box E2E test.
class E2eAppHarness {
  E2eAppHarness._({
    required this.db,
    required this.logger,
    required this.http,
    required this.platformFakes,
    required this.targetPlatform,
    this.fixedNow,
  });

  static bool _tzInitialized = false;

  final AppDatabase db;
  final RecordingLogger logger;
  final FakeHttpOverrides http;
  final PlatformFakes platformFakes;
  final TargetPlatform targetPlatform;

  /// Optional deterministic clock timestamp.
  ///
  /// **Scope Note:** `package:clock` (`clock.clock.now()`) governs
  /// `PersonService`, `PersonRepository`, `SubscriptionNotifier`, and
  /// `ReminderSettingsController`. Meanwhile, `birthdaysStreamProvider`,
  /// `BirthdayLifecycleService`, `DashboardScreen`, `CalendarScreen`, and
  /// `JobWorker` currently read `DateTime.now()` directly. Use [withTestClock]
  /// for clock-governed services and select dates relative to `DateTime.now()`
  /// when asserting today/upcoming UI buckets in `DashboardScreen`/`CalendarScreen`.
  DateTime? fixedNow;

  bool _appMounted = false;
  bool _tornDown = false;

  FakeHttpRouter get httpRouter => http.router;
  FakeSecureStorageChannel get secureStorage => platformFakes.secureStorage;
  FakePlatformSystemChannel get systemChannel => platformFakes.system;
  FakeUrlLauncher get urlLauncher => platformFakes.urlLauncher;
  FakeGoogleSignIn get googleSignIn => platformFakes.googleSignIn;
  FakeInAppPurchase get inAppPurchase => platformFakes.inAppPurchase;
  FakeFirebaseCoreAndAppCheck get firebase => platformFakes.firebase;
  FakeFilePicker get filePicker => platformFakes.filePicker;
  FakeAndroidChannels get androidChannels => platformFakes.androidChannels;

  /// Initializes timezone data, in-memory Drift DB, `RecordingLogger`,
  /// `FakeHttpOverrides`, and `PlatformFakes`.
  static Future<E2eAppHarness> create({
    TargetPlatform targetPlatform = TargetPlatform.android,
    bool onboardingCompleted = false,
    DateTime? fixedNow,
    FakeHttpRouter? httpRouter,
  }) async {
    TestWidgetsFlutterBinding.ensureInitialized();
    if (!_tzInitialized) {
      tz_data.initializeTimeZones();
      _tzInitialized = true;
    }

    debugDefaultTargetPlatformOverride = targetPlatform;

    final db = AppDatabase(NativeDatabase.memory());
    final logger = RecordingLogger(level: LogLevel.verbose);
    final http = FakeHttpOverrides.install(router: httpRouter);
    final platformFakes = PlatformFakes()..install();

    if (onboardingCompleted) {
      platformFakes.secureStorage.values['ai_birthday_onboarding_completed'] =
          'true';
    }

    return E2eAppHarness._(
      db: db,
      logger: logger,
      http: http,
      platformFakes: platformFakes,
      targetPlatform: targetPlatform,
      fixedNow: fixedNow,
    );
  }

  /// Boundary-only Riverpod overrides (zero `lib/features/` business-logic mocks).
  List<Override> buildProviderOverrides({
    bool enableReminderBootstrap = false,
    List<Override> extraOverrides = const [],
  }) {
    return [
      core.loggerProvider.overrideWithValue(logger),
      core.databaseProvider.overrideWithValue(db),
      geminiNanoPlatformProvider.overrideWithValue(
        MethodChannelGeminiNanoPlatform(
          isLiveAndroid: () => defaultTargetPlatform == TargetPlatform.android,
        ),
      ),
      notificationSchedulerGatewayProvider.overrideWithValue(
        const ChannelForwardingNotificationGateway(),
      ),
      nativeShareServiceProvider.overrideWithValue(
        const ChannelForwardingShareService(),
      ),
      deviceContactsServiceProvider.overrideWithValue(
        const ChannelForwardingContactsService(),
      ),
      nativeSmsGatewayProvider.overrideWithValue(
        const ChannelForwardingSmsGateway(),
      ),
      if (enableReminderBootstrap)
        reminderBootstrapProvider.overrideWith((ref) {
          final notifier = ref.read(reminderSettingsProvider.notifier);
          final bootstrap = ReminderBootstrap(
            reconcile: (reason) =>
                notifier.replan(reason: reason, onlyIfStale: false),
            logger: ref.read(loggerProvider),
            onResume: () => ref.invalidate(birthdaysStreamProvider),
          );
          ref.onDispose(bootstrap.dispose);
          return bootstrap;
        }),
      ...extraOverrides,
    ];
  }

  /// Runs [body] inside a `clock.withClock` zone fixed to [now].
  Future<T> withTestClock<T>(DateTime now, Future<T> Function() body) {
    return clock.withClock(clock.Clock.fixed(now), body);
  }

  /// Mounts [AiBirthdayApp] with boundary overrides and waits for initial settle.
  Future<void> pumpApp(
    WidgetTester tester, {
    E2eViewport viewport = E2eViewport.phone412,
    Brightness brightness = Brightness.light,
    double imeBottomInset = 0,
    bool? onboardingCompleted,
    DateTime? fixedNow,
    bool enableReminderBootstrap = false,
    List<Override> extraOverrides = const [],
  }) async {
    if (onboardingCompleted != null) {
      if (onboardingCompleted) {
        secureStorage.values['ai_birthday_onboarding_completed'] = 'true';
      } else {
        secureStorage.values.remove('ai_birthday_onboarding_completed');
      }
    }

    applyViewport(
      tester,
      viewport,
      brightness: brightness,
      imeBottomInset: imeBottomInset,
    );

    final effectiveClock = fixedNow ?? this.fixedNow;
    Future<void> mount() async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: buildProviderOverrides(
            enableReminderBootstrap: enableReminderBootstrap,
            extraOverrides: extraOverrides,
          ),
          child: const AiBirthdayApp(),
        ),
      );
      _appMounted = true;
      await tester.pumpAndSettle();
    }

    if (effectiveClock != null) {
      await withTestClock(effectiveClock, mount);
    } else {
      await mount();
    }
  }

  /// Unmounts [AiBirthdayApp] and drains Drift stream-close & midnight timers
  /// before closing the database.
  Future<void> disposeApp(WidgetTester tester) async {
    if (_appMounted) {
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 20));
      _appMounted = false;
    }
  }

  /// Full teardown: unmounts widget tree if still mounted, closes Drift DB,
  /// uninstalls [PlatformFakes], restores [HttpOverrides] and
  /// `debugDefaultTargetPlatformOverride`.
  Future<void> tearDown(WidgetTester tester) async {
    if (_tornDown) return;
    _tornDown = true;
    try {
      await disposeApp(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
      await platformFakes.uninstall();
      http.restore();
      await db.close();
    }
  }
}
