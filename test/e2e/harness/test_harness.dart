import 'package:drift/native.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_purchase/in_app_purchase.dart' as iap;

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/core_providers.dart' hide loggerProvider;
import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import 'fake_aicore_platform.dart';
import 'fake_firebase_auth_client.dart';
import 'fake_store_driver.dart';
import 'fake_subscription_server.dart';

/// Test double for in-app purchase platform avoiding live BillingClient calls.
class FakeUnavailableIap implements iap.InAppPurchase {
  @override
  Stream<List<iap.PurchaseDetails>> get purchaseStream => const Stream.empty();

  @override
  Future<bool> isAvailable() async => false;

  @override
  Future<iap.ProductDetailsResponse> queryProductDetails(
    Set<String> identifiers,
  ) async {
    return iap.ProductDetailsResponse(
      productDetails: [],
      notFoundIDs: identifiers.toList(),
    );
  }

  @override
  Future<bool> buyNonConsumable({
    required iap.PurchaseParam purchaseParam,
  }) async => false;

  @override
  Future<bool> buyConsumable({
    required iap.PurchaseParam purchaseParam,
    bool autoConsume = true,
  }) async => false;

  @override
  Future<void> completePurchase(iap.PurchaseDetails purchase) async {}

  @override
  Future<void> restorePurchases({String? applicationUserName}) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Test harness orchestrating in-memory database, fake secure store,
/// and Riverpod overrides for E2E requirement tests.
class E2ETestHarness {
  late AppDatabase db;
  late FakeStoreDriver storeDriver;
  late SecureCredentialStorage credentialStorage;
  late DriftPeopleRepository peopleRepo;
  late DriftBirthdaysRepository birthdaysRepo;
  late DriftDraftsRepository draftsRepo;
  late DriftDeliveryEventsRepository deliveryEventsRepo;
  late FakeAICorePlatform fakeAiCore;
  late FakeFirebaseAuthClient fakeAuthClient;
  late FakeSubscriptionServer fakeSubscriptionServer;
  late SubscriptionNotifier subscriptionNotifier;

  void setUp() {
    TestWidgetsFlutterBinding.ensureInitialized();
    db = AppDatabase(NativeDatabase.memory());
    storeDriver = FakeStoreDriver();
    credentialStorage = SecureCredentialStorage(storeDriver);
    peopleRepo = DriftPeopleRepository(db);
    birthdaysRepo = DriftBirthdaysRepository(db);
    draftsRepo = DriftDraftsRepository(db);
    deliveryEventsRepo = DriftDeliveryEventsRepository(db);
    fakeAiCore = FakeAICorePlatform();
    fakeAuthClient = FakeFirebaseAuthClient();
    fakeSubscriptionServer = FakeSubscriptionServer();
    subscriptionNotifier = SubscriptionNotifier(
      initial: UserEntitlement.free,
      store: storeDriver,
      inAppPurchase: FakeUnavailableIap(),
      logger: const NoopLogger(),
    );
  }

  Future<void> tearDown() async {
    // Riverpod disposes the notifier when a widget tree that watches it
    // unmounts (end of test), so a second dispose here would throw in debug.
    try {
      subscriptionNotifier.dispose();
    } on StateError {
      // Already disposed by Riverpod; nothing to release.
    }
    await fakeAiCore.dispose();
    await db.close();
  }

  /// List of provider overrides configuring Riverpod with test doubles.
  List<Override> get providerOverrides => [
    databaseProvider.overrideWithValue(db),
    credentialStorageProvider.overrideWithValue(credentialStorage),
    peopleRepositoryProvider.overrideWithValue(peopleRepo),
    birthdaysRepositoryProvider.overrideWithValue(birthdaysRepo),
    draftsRepositoryProvider.overrideWithValue(draftsRepo),
    deliveryEventsRepositoryProvider.overrideWithValue(deliveryEventsRepo),
    geminiNanoPlatformProvider.overrideWithValue(fakeAiCore),
    subscriptionNotifierProvider.overrideWith((ref) => subscriptionNotifier),
    loggerProvider.overrideWithValue(const NoopLogger()),
  ];
}
