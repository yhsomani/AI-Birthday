/// Global Riverpod dependency injection and service providers (SSOT §3, §24, §28).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_router.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/delivery/data/whatsapp_handoff_builder.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/domain/repositories/drafts_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/core/security/flutter_secure_storage_driver.dart';
import 'package:ai_birthday/features/people/domain/repositories/people_repository.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/ai/data/gemini_nano_provider.dart';
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';

/// App Logger provider.
final loggerProvider = Provider<AppLogger>((ref) {
  return ConsoleAppLogger();
});

/// Credential Storage provider.
final credentialStorageProvider = Provider<CredentialStorage>((ref) {
  return const SecureCredentialStorage(FlutterSecureStorageDriver());
});

/// Theme Mode state provider.
final themeModeProvider = StateProvider<ThemeMode>((ref) {
  return ThemeMode.system;
});

/// Subscription Notifier managing entitlement through purchase & verification lifecycle (SSOT §11).
final subscriptionNotifierProvider =
    StateNotifierProvider<SubscriptionNotifier, UserEntitlement>((ref) {
  final logger = ref.watch(loggerProvider);
  return SubscriptionNotifier(initial: UserEntitlement.free, logger: logger);
});

/// Application Entitlement state provider (Free vs Pro). Defaults safely to Free (SSOT §11).
final entitlementProvider = StateProvider<UserEntitlement>((ref) {
  return ref.watch(subscriptionNotifierProvider);
});

/// WhatsApp Handoff Builder provider.
final whatsappHandoffBuilderProvider = Provider<WhatsAppHandoffBuilder>((ref) {
  return const WhatsAppHandoffBuilder();
});

/// Gemini Nano Platform provider.
final geminiNanoPlatformProvider = Provider<GeminiNanoPlatform>((ref) {
  return const DefaultGeminiNanoPlatform();
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
          : GeminiNanoStatus.unavailable;
    },
    logger: logger,
  );
});

// Seed data for initial app state
final _seedPeople = <Person>[
  Person(
    id: 'person-1',
    name: 'Sarah Connor',
    birthdayMonth: DateTime.now().month,
    birthdayDay: DateTime.now().day, // Today!
    birthYear: 1994,
    phoneNumber: '+14155552671',
    relationship: RelationshipCategory.friend,
    relationshipCloseness: RelationshipCloseness.close,
    preferredTone: MessageTone.warm,
    importantFacts: [
      'Loves marathon running',
      'Adopted a rescue golden retriever',
    ],
    notes: 'Know each other from college running club.',
    createdAt: DateTime.now().subtract(const Duration(days: 30)),
    updatedAt: DateTime.now(),
  ),
  Person(
    id: 'person-2',
    name: 'David Miller',
    birthdayMonth: DateTime.now().add(const Duration(days: 3)).month,
    birthdayDay: DateTime.now().add(const Duration(days: 3)).day, // In 3 days!
    birthYear: 1988,
    phoneNumber: '+14155558912',
    relationship: RelationshipCategory.colleague,
    relationshipCloseness: RelationshipCloseness.casual,
    preferredTone: MessageTone.funny,
    importantFacts: ['Coffee connoisseur', 'Just promoted to Lead Architect'],
    createdAt: DateTime.now().subtract(const Duration(days: 60)),
    updatedAt: DateTime.now(),
  ),
  Person(
    id: 'person-3',
    name: 'Elena Rostova',
    birthdayMonth: DateTime.now().add(const Duration(days: 14)).month,
    birthdayDay: DateTime.now()
        .add(const Duration(days: 14))
        .day, // In 2 weeks!
    birthYear: 1996,
    phoneNumber: '+14155554321',
    relationship: RelationshipCategory.family,
    relationshipCloseness: RelationshipCloseness.close,
    preferredTone: MessageTone.emotional,
    importantFacts: [
      'Passionate about watercolor painting',
      'New mother to baby Leo',
    ],
    createdAt: DateTime.now().subtract(const Duration(days: 90)),
    updatedAt: DateTime.now(),
  ),
];

final _seedBirthdays = <Birthday>[
  Birthday(
    id: 'birthday-1',
    personId: 'person-1',
    cycleYear: DateTime.now().year,
    date: DateTime(
      DateTime.now().year,
      DateTime.now().month,
      DateTime.now().day,
    ),
    status: BirthdayStatus.messageDrafted,
    draftId: 'draft-1',
    createdAt: DateTime.now().subtract(const Duration(days: 7)),
    updatedAt: DateTime.now(),
  ),
  Birthday(
    id: 'birthday-2',
    personId: 'person-2',
    cycleYear: DateTime.now().year,
    date: DateTime.now().add(const Duration(days: 3)),
    status: BirthdayStatus.reminderDue,
    createdAt: DateTime.now().subtract(const Duration(days: 7)),
    updatedAt: DateTime.now(),
  ),
  Birthday(
    id: 'birthday-3',
    personId: 'person-3',
    cycleYear: DateTime.now().year,
    date: DateTime.now().add(const Duration(days: 14)),
    status: BirthdayStatus.upcoming,
    createdAt: DateTime.now().subtract(const Duration(days: 7)),
    updatedAt: DateTime.now(),
  ),
];

final _seedDrafts = <MessageDraft>[
  MessageDraft(
    id: 'draft-1',
    birthdayId: 'birthday-1',
    personId: 'person-1',
    body:
        'Happy Birthday Sarah! Wishing you another incredible year full of great marathon milestones and sweet moments with your pup! 🏃‍♀️🐕 Have a wonderful celebration!',
    tone: MessageTone.warm,
    length: MessageLength.standard,
    status: DraftStatus.draft,
    providerType: 'user_gemini',
    createdAt: DateTime.now().subtract(const Duration(hours: 4)),
    updatedAt: DateTime.now().subtract(const Duration(hours: 4)),
  ),
];

/// People Repository provider.
final peopleRepositoryProvider = Provider<PeopleRepository>((ref) {
  return InMemoryPeopleRepository(initialPeople: _seedPeople);
});

/// Birthdays Repository provider.
final birthdaysRepositoryProvider = Provider<BirthdaysRepository>((ref) {
  return InMemoryBirthdaysRepository(initialBirthdays: _seedBirthdays);
});

/// Drafts Repository provider.
final draftsRepositoryProvider = Provider<DraftsRepository>((ref) {
  return InMemoryDraftsRepository(initialDrafts: _seedDrafts);
});

/// Stream of all tracked people.
final peopleStreamProvider = StreamProvider<List<Person>>((ref) {
  final repo = ref.watch(peopleRepositoryProvider);
  return repo.watchPeople();
});

/// Stream of all tracked birthdays.
final birthdaysStreamProvider = StreamProvider<List<Birthday>>((ref) {
  final repo = ref.watch(birthdaysRepositoryProvider);
  return repo.watchBirthdays();
});
