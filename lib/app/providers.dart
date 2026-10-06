/// Global Riverpod dependency injection and service providers (SSOT §3, §24, §28).
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_router.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/birthdays/application/birthday_lifecycle_service.dart';
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/delivery/data/whatsapp_handoff_builder.dart';
import 'package:ai_birthday/features/delivery/data/native_share_service.dart';
import 'package:ai_birthday/features/delivery/data/sms_delivery_service.dart';
import 'package:ai_birthday/features/people/data/contact_csv_service.dart';
import 'package:ai_birthday/features/people/data/device_contacts_service.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/domain/repositories/drafts_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/core/security/flutter_secure_storage_driver.dart';
import 'package:ai_birthday/features/people/domain/repositories/people_repository.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';
import 'package:ai_birthday/features/sync/data/cloud_sync_service.dart';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:ai_birthday/core/core_providers.dart' as core;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/ai/data/gemini_nano_provider.dart';
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';

/// App-wide logger provider. Keep one logger instance across app and core layers.
final loggerProvider = core.loggerProvider;

/// Credential Storage provider.
final credentialStorageProvider = Provider<CredentialStorage>((ref) {
  final logger = ref.watch(loggerProvider);
  return SecureCredentialStorage(
    FlutterSecureStorageDriver(const FlutterSecureStorage(), logger),
  );
});

/// Subscription Notifier managing entitlement through purchase & verification lifecycle (SSOT §11).
final subscriptionNotifierProvider =
    StateNotifierProvider<SubscriptionNotifier, UserEntitlement>((ref) {
      final logger = ref.watch(loggerProvider);
      final secureStore = FlutterSecureStorageDriver(
        const FlutterSecureStorage(),
        logger,
      );
      return SubscriptionNotifier(
        initial: UserEntitlement.free,
        store: secureStore,
        logger: logger,
        authTokenProvider: () async {
          final auth = await ref.read(authControllerProvider.future);
          return auth.identity?.idToken;
        },
        accountBindingProvider: () async {
          final auth = await ref.read(authControllerProvider.future);
          final subject = auth.identity?.googleSubject;
          if (subject == null || subject.isEmpty) return null;
          return SubscriptionNotifier.readBindingFromStore(
            secureStore,
            subject,
          );
        },
      );
    });

/// Application Entitlement state provider (Free vs Pro). Defaults safely to Free (SSOT §11).
final entitlementProvider = Provider<UserEntitlement>((ref) {
  return ref.watch(subscriptionNotifierProvider);
});

/// WhatsApp Handoff Builder provider.
final whatsappHandoffBuilderProvider = Provider<WhatsAppHandoffBuilder>((ref) {
  return const WhatsAppHandoffBuilder();
});

/// SMS Delivery Service provider (SSOT §10).
final smsDeliveryServiceProvider = Provider<SmsDeliveryService>((ref) {
  return const SmsDeliveryService();
});

/// Native Share Service provider (SSOT §10).
final nativeShareServiceProvider = Provider<NativeShareService>((ref) {
  return const NativeShareService();
});

/// Contact CSV Service provider (SSOT §18).
final contactCsvServiceProvider = Provider<ContactCsvService>((ref) {
  return const ContactCsvService();
});

/// Device Contacts Service provider (SSOT §18, §25).
final deviceContactsServiceProvider = Provider<DeviceContactsService>((ref) {
  return const DeviceContactsService();
});

/// Cloud Sync Service provider (SSOT §13).
final cloudSyncServiceProvider = Provider<CloudSyncService>((ref) {
  final db = ref.watch(databaseProvider);
  final logger = ref.watch(loggerProvider);
  final store = FlutterSecureStorageDriver(
    const FlutterSecureStorage(),
    logger,
  );
  return CloudSyncService(db: db, store: store, logger: logger);
});

/// Gemini Nano Platform provider.
final geminiNanoPlatformProvider = Provider<GeminiNanoPlatform>((ref) {
  return MethodChannelGeminiNanoPlatform();
});

/// Gemini Nano on-device AI provider.
final geminiNanoProvider = Provider<GeminiNanoProvider>((ref) {
  final platform = ref.watch(geminiNanoPlatformProvider);
  final logger = ref.watch(loggerProvider);
  return GeminiNanoProvider(platform: platform, logger: logger);
});

/// User Gemini API Provider.
final userGeminiApiProvider = Provider<UserGeminiApiProvider>((ref) {
  final storage = ref.watch(credentialStorageProvider);
  final logger = ref.watch(loggerProvider);
  return UserGeminiApiProvider(credentialStorage: storage, logger: logger);
});

/// AI Router provider wiring entitlement, user Gemini key, and Gemini Nano (SSOT §5).
final aiRouterProvider = Provider<AiRouter>((ref) {
  final storage = ref.watch(credentialStorageProvider);
  final geminiProvider = ref.watch(userGeminiApiProvider);
  final nanoPlatform = ref.watch(geminiNanoPlatformProvider);
  final nanoProvider = ref.watch(geminiNanoProvider);
  final logger = ref.watch(loggerProvider);

  return AiRouter(
    credentialStorage: storage,
    userGeminiProvider: geminiProvider,
    nanoProvider: nanoProvider,
    nanoStatusChecker: () async {
      final state = await nanoPlatform.currentState();
      return state.isUsable
          ? GeminiNanoStatus.available
          : switch (state) {
              NanoState.downloadable => GeminiNanoStatus.downloadable,
              NanoState.downloading => GeminiNanoStatus.downloading,
              NanoState.busy => GeminiNanoStatus.busy,
              _ => GeminiNanoStatus.unavailable,
            };
    },
    logger: logger,
  );
});

/// People Repository provider backed by operational Drift/SQLite (SSOT §13).
final peopleRepositoryProvider = Provider<PeopleRepository>((ref) {
  final db = ref.watch(databaseProvider);
  return DriftPeopleRepository(db);
});

/// Birthdays Repository provider backed by operational Drift/SQLite (SSOT §13).
final birthdaysRepositoryProvider = Provider<BirthdaysRepository>((ref) {
  final db = ref.watch(databaseProvider);
  return DriftBirthdaysRepository(db);
});

/// Drafts Repository provider backed by operational Drift/SQLite (SSOT §13).
final draftsRepositoryProvider = Provider<DraftsRepository>((ref) {
  final db = ref.watch(databaseProvider);
  return DriftDraftsRepository(db);
});

/// Stream of all tracked people.
final peopleStreamProvider = StreamProvider<List<Person>>((ref) {
  final repo = ref.watch(peopleRepositoryProvider);
  return repo.watchPeople();
});

/// Stream of all tracked birthdays.
final birthdaysStreamProvider = StreamProvider<List<Birthday>>((ref) async* {
  final birthdayRepo = ref.watch(birthdaysRepositoryProvider);
  final peopleRepo = ref.watch(peopleRepositoryProvider);

  // Reconcile the active occurrence before any screen consumes birthday data.
  final people = await peopleRepo.getPeople();
  await const BirthdayLifecycleService().refresh(
    people: people,
    birthdaysRepository: birthdayRepo,
  );

  yield* birthdayRepo.watchBirthdays();
});

/// Stream of all message drafts.
final draftsStreamProvider = StreamProvider<List<MessageDraft>>((ref) {
  final repo = ref.watch(draftsRepositoryProvider);
  return repo.watchDrafts();
});
