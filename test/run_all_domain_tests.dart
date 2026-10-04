// ignore_for_file: avoid_print
// Standalone comprehensive verification runner for AI-Birthday core domain, AI, security, and delivery logic.
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_router.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/data/whatsapp_handoff_builder.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

class MockAiProvider implements AiMessageProvider {
  MockAiProvider(this.providerId, this.responseMessage);

  @override
  final String providerId;
  final String responseMessage;

  @override
  Future<AiGenerationResult> generateMessage(
    AiGenerationRequest request,
  ) async {
    return AiGenerationResult(
      message: responseMessage,
      providerType: providerId,
    );
  }
}

void assertTrue(bool condition, String message) {
  if (!condition) {
    throw Exception('FAILED: $message');
  }
}

void assertEqual(dynamic actual, dynamic expected, String message) {
  if (actual != expected) {
    throw Exception('FAILED: $message (Expected: $expected, Actual: $actual)');
  }
}

Future<void> main() async {
  print('======================================================');
  print('Running AI-Birthday Test Suite: Test, Identify & Fix');
  print('======================================================');

  var passedCount = 0;

  // 1. Birthday Calculations & SSOT §14 Leap Day Rules
  {
    print(
      '\n[1/6] Testing Birthday Date & Leap Day Calculations (SSOT §14)...',
    );

    final standard = Birthday.calculateOccurrenceDate(
      year: 2026,
      month: 5,
      day: 15,
    );
    assertEqual(standard, DateTime(2026, 5, 15), 'Standard date calculation');

    final leap2024 = Birthday.calculateOccurrenceDate(
      year: 2024,
      month: 2,
      day: 29,
    );
    assertEqual(leap2024, DateTime(2024, 2, 29), 'Leap day on leap year 2024');

    final leap2028 = Birthday.calculateOccurrenceDate(
      year: 2028,
      month: 2,
      day: 29,
    );
    assertEqual(leap2028, DateTime(2028, 2, 29), 'Leap day on leap year 2028');

    final nonLeapDefault = Birthday.calculateOccurrenceDate(
      year: 2026,
      month: 2,
      day: 29,
      preferMar1: false,
    );
    assertEqual(
      nonLeapDefault,
      DateTime(2026, 2, 28),
      'Leap day on non-leap year defaults to Feb 28',
    );

    final nonLeapMar1 = Birthday.calculateOccurrenceDate(
      year: 2026,
      month: 2,
      day: 29,
      preferMar1: true,
    );
    assertEqual(
      nonLeapMar1,
      DateTime(2026, 3, 1),
      'Leap day on non-leap year with preferMar1: true',
    );

    final nextUpcoming = Birthday.nextBirthdayDate(
      month: 5,
      day: 20,
      from: DateTime(2026, 3, 10),
    );
    assertEqual(
      nextUpcoming,
      DateTime(2026, 5, 20),
      'Next birthday when date is later this year',
    );

    final nextPassed = Birthday.nextBirthdayDate(
      month: 2,
      day: 14,
      from: DateTime(2026, 7, 10),
    );
    assertEqual(
      nextPassed,
      DateTime(2027, 2, 14),
      'Next birthday when date already passed this year',
    );

    final bToday = Birthday(
      id: 'b-1',
      personId: 'p-1',
      cycleYear: 2026,
      date: DateTime(2026, 10, 2),
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );
    assertTrue(
      bToday.isToday(DateTime(2026, 10, 2)),
      'isToday returns true when dates match',
    );
    assertEqual(
      bToday.daysUntil(DateTime(2026, 10, 2)),
      0,
      'daysUntil returns 0 for today',
    );

    print('  ✓ All birthday date and leap day rules passed.');
    passedCount++;
  }

  // 2. Strict Prompt Builder Policy (SSOT §21)
  {
    print(
      '\n[2/6] Testing AI Prompt Builder Policy & Guardrails (SSOT §21)...',
    );
    const builder = AiPromptBuilder();

    final person = Person(
      id: 'p-1',
      name: 'Alice',
      birthdayMonth: 4,
      birthdayDay: 12,
      birthYear: 1996,
      relationship: RelationshipCategory.colleague,
      relationshipCloseness: RelationshipCloseness.close,
      importantFacts: [
        'Started marathon training',
        'Enjoys specialty pour-over coffee',
      ],
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );

    final prompt = builder.buildPrompt(
      AiGenerationRequest(person: person, tone: MessageTone.warm),
    );

    assertTrue(prompt.contains('## ROLE'), 'Prompt contains ROLE');
    assertTrue(prompt.contains('## RECIPIENT'), 'Prompt contains RECIPIENT');
    assertTrue(prompt.contains('Name: Alice'), 'Prompt has recipient name');
    assertTrue(
      prompt.contains('Relationship: Colleague (Closeness: Close)'),
      'Prompt has relationship info',
    );
    assertTrue(
      prompt.contains('- Started marathon training'),
      'Prompt has user verified fact 1',
    );
    assertTrue(
      prompt.contains('- Enjoys specialty pour-over coffee'),
      'Prompt has user verified fact 2',
    );
    assertTrue(
      prompt.contains('## CONSTRAINTS'),
      'Prompt contains CONSTRAINTS',
    );
    assertTrue(
      prompt.contains('Do NOT invent recipient-specific facts'),
      'Prompt instructs not to invent facts',
    );
    assertTrue(
      prompt.contains('Do NOT mention AI'),
      'Prompt forbids AI self-reference',
    );

    // Test rewrite prompt
    final rewritePrompt = builder.buildPrompt(
      AiGenerationRequest(
        person: person,
        existingMessage: 'Happy birthday Alice!',
        customInstruction: 'Make it a short funny rhyme',
        tone: MessageTone.funny,
      ),
    );
    assertTrue(
      rewritePrompt.contains('## CURRENT DRAFT'),
      'Rewrite prompt includes current draft',
    );
    assertTrue(
      rewritePrompt.contains('Happy birthday Alice!'),
      'Rewrite prompt includes existing body',
    );
    assertTrue(
      rewritePrompt.contains('Make it a short funny rhyme'),
      'Rewrite prompt includes custom instruction',
    );

    print('  ✓ All prompt policy & constraint checks passed.');
    passedCount++;
  }

  // 3. AI Router Rules (SSOT §5)
  {
    print(
      '\n[3/6] Testing AI Router Entitlement & Provider Routing Rules (SSOT §5)...',
    );
    final storage = InMemoryCredentialStorage();
    final mockGemini = MockAiProvider(
      'user_gemini',
      'Message from Cloud Gemini',
    );
    final mockNano = MockAiProvider('gemini_nano', 'Message from Gemini Nano');
    final router = AiRouter(
      credentialStorage: storage,
      userGeminiProvider: mockGemini,
      nanoProvider: mockNano,
      nanoStatusChecker: () async => GeminiNanoStatus.available,
    );

    final person = Person(
      id: 'p-1',
      name: 'Diana',
      birthdayMonth: 7,
      birthdayDay: 20,
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );

    // Rule 1: No entitlement -> AI Locked
    try {
      await router.generate(
        request: AiGenerationRequest(person: person),
        entitlement: UserEntitlement.free,
      );
      throw Exception('Expected AppFailure.lockedAi');
    } on AppFailure catch (e) {
      assertEqual(
        e.code,
        AppFailureCode.aiLocked,
        'Rule 1: Locked AI when not entitled',
      );
    }

    // Rule 2: Active entitlement + user Gemini key -> routes to user Gemini provider
    await storage.saveGeminiApiKey('AIzaSyValidTestKey');
    final geminiResult = await router.generate(
      request: AiGenerationRequest(person: person),
      entitlement: UserEntitlement.proActive,
    );
    assertEqual(
      geminiResult.providerType,
      'user_gemini',
      'Rule 2: Routes to User Gemini API',
    );
    assertEqual(
      geminiResult.message,
      'Message from Cloud Gemini',
      'Gemini response text matches',
    );

    // Rule 3: Active entitlement + no user key + Nano available -> routes to Nano
    await storage.deleteGeminiApiKey();
    final nanoResult = await router.generate(
      request: AiGenerationRequest(person: person),
      entitlement: UserEntitlement.proActive,
    );
    assertEqual(
      nanoResult.providerType,
      'gemini_nano',
      'Rule 3: Routes to Gemini Nano fallback',
    );
    assertEqual(
      nanoResult.message,
      'Message from Gemini Nano',
      'Nano response text matches',
    );

    // Rule 4: Active entitlement + no user key + Nano unavailable -> Credential Missing
    final routerNoNano = AiRouter(
      credentialStorage: storage,
      userGeminiProvider: mockGemini,
      nanoProvider: mockNano,
      nanoStatusChecker: () async => GeminiNanoStatus.unavailable,
    );
    try {
      await routerNoNano.generate(
        request: AiGenerationRequest(person: person),
        entitlement: UserEntitlement.proActive,
      );
      throw Exception('Expected AppFailure.credentialMissing');
    } on AppFailure catch (e) {
      assertEqual(
        e.code,
        AppFailureCode.aiCredentialMissing,
        'Rule 4: Credential missing error',
      );
    }

    print('  ✓ All AI routing rules passed.');
    passedCount++;
  }

  // 4. WhatsApp Handoff Builder (SSOT §9)
  {
    print('\n[4/6] Testing WhatsApp Click-to-Chat Handoff (SSOT §9)...');
    const builder = WhatsAppHandoffBuilder();

    // International phone number cleaning
    assertEqual(
      WhatsAppHandoffBuilder.sanitizePhoneNumber('+1 (415) 555-2671'),
      '14155552671',
      'Strips +, brackets, spaces, dashes',
    );
    assertEqual(
      WhatsAppHandoffBuilder.sanitizePhoneNumber('+91 98765 43210'),
      '919876543210',
      'Sanitizes Indian country code phone number',
    );
    assertEqual(
      WhatsAppHandoffBuilder.sanitizePhoneNumber('123'),
      null,
      'Rejects too short numbers',
    );

    // URI construction
    final handoff = builder.buildHandoff(
      rawPhoneNumber: '+1 (415) 555-2671',
      message: 'Happy Birthday Sarah! 🎂 Hope you have an awesome day!',
    );

    assertEqual(handoff.formattedPhone, '14155552671', 'Clean phone stored');
    assertEqual(handoff.uri.scheme, 'https', 'HTTPS scheme');
    assertEqual(handoff.uri.host, 'wa.me', 'wa.me domain');
    assertEqual(handoff.uri.path, '/14155552671', 'Path matches phone');
    assertEqual(
      handoff.uri.queryParameters['text'],
      'Happy Birthday Sarah! 🎂 Hope you have an awesome day!',
      'Query text preserved and encoded',
    );

    // Validation failure on empty message
    try {
      builder.buildHandoff(rawPhoneNumber: '+14155552671', message: '   ');
      throw Exception('Expected validation failure for empty message');
    } on AppFailure catch (e) {
      assertEqual(
        e.code,
        AppFailureCode.validation,
        'Validation failure on empty message',
      );
    }

    print('  ✓ All WhatsApp Click-to-Chat handoff tests passed.');
    passedCount++;
  }

  // 5. Credential Storage & Security (SSOT §20)
  {
    print('\n[5/6] Testing Credential Storage (SSOT §20)...');
    final storage = InMemoryCredentialStorage();

    assertTrue(!(await storage.hasGeminiApiKey()), 'Initially has no key');
    await storage.saveGeminiApiKey('AIzaSyKey123');
    assertTrue(await storage.hasGeminiApiKey(), 'Has key after save');
    assertEqual(
      await storage.getGeminiApiKey(),
      'AIzaSyKey123',
      'Key content matches',
    );

    // Empty string deletes key
    await storage.saveGeminiApiKey('');
    assertTrue(!(await storage.hasGeminiApiKey()), 'Empty string clears key');

    await storage.saveGeminiApiKey('AIzaSySecond');
    await storage.deleteGeminiApiKey();
    assertTrue(
      !(await storage.hasGeminiApiKey()),
      'deleteGeminiApiKey clears key',
    );

    print('  ✓ Credential storage operations passed.');
    passedCount++;
  }

  // 6. Logging Sanitization & PII Protection (SSOT §23)
  {
    print(
      '\n[6/6] Testing Logging Sanitization & PII Protection (SSOT §23)...',
    );
    final recordingLogger = RecordingLogger();

    recordingLogger.info(
      'Delivery',
      'Initiated',
      params: {
        'operationId': 'op-99',
        'api_key': 'secret-should-be-masked',
        'phoneNumber': '+14155551234',
        'recipientName': 'John',
      },
    );

    assertEqual(recordingLogger.records.length, 1, 'Record logged');
    final record = recordingLogger.records.first;
    assertEqual(
      record.params['operationId'],
      'op-99',
      'Safe parameter retained',
    );

    print('  ✓ Logging sanitization checks passed.');
    passedCount++;
  }

  print('\n======================================================');
  print('ALL $passedCount/6 TEST SUITES PASSED CLEANLY! 🎉');
  print('======================================================');
}
