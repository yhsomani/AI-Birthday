/// Strict, guardrailed prompt builder for AI message generation and rewriting (SSOT §6, §21).
library;

import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

/// Request parameters for AI birthday message generation or rewrite.
class AiGenerationRequest {
  const AiGenerationRequest({
    required this.person,
    this.tone,
    this.length,
    this.customInstruction,
    this.existingMessage,
    this.targetLanguage,
  });

  final Person person;
  final MessageTone? tone;
  final MessageLength? length;

  /// Optional specific rewrite instruction (e.g., "make it rhyming", "add a reference to their new dog").
  final String? customInstruction;

  /// Existing message when executing a rewrite / shorten / expand / translate operation.
  final String? existingMessage;

  /// Optional language translation target (defaults to person.preferredLanguage).
  final String? targetLanguage;
}

/// Constructs prompts conforming to the authoritative prompt policy in SSOT §21.
class AiPromptBuilder {
  const AiPromptBuilder();

  /// Builds a complete prompt string for generating a new message or rewriting an existing one.
  String buildPrompt(AiGenerationRequest request) {
    final person = request.person;
    final tone = request.tone ?? person.preferredTone;
    final length = request.length ?? MessageLength.standard;
    final language = request.targetLanguage ?? person.preferredLanguage;

    final buffer = StringBuffer();

    // 1. Role
    buffer.writeln('## ROLE');
    buffer.writeln('You are a thoughtful birthday message assistant.');
    buffer.writeln();

    // 2. Recipient
    buffer.writeln('## RECIPIENT');
    buffer.writeln('Name: ${person.name}');
    buffer.writeln(
      'Relationship: ${person.relationship.displayName} (Closeness: ${person.relationshipCloseness.displayName})',
    );
    if (person.birthYear != null) {
      final currentYear = DateTime.now().year;
      final age = currentYear - person.birthYear!;
      if (age > 0) {
        buffer.writeln('Turning age: $age');
      }
    }
    buffer.writeln();

    // 3. Known Facts (User-provided only! SSOT §2, §7, §21)
    buffer.writeln('## KNOWN FACTS');
    if (person.importantFacts.isEmpty) {
      buffer.writeln(
        'None provided. Do NOT invent any specific hobbies, achievements, or biographical details.',
      );
    } else {
      buffer.writeln(
        'The following facts are verified by the user. You may naturally incorporate them:',
      );
      for (final fact in person.importantFacts) {
        // Sanitize fact to prevent prompt injection attempts
        final cleanFact = fact.replaceAll('\n', ' ').trim();
        if (cleanFact.isNotEmpty) {
          buffer.writeln('- $cleanFact');
        }
      }
    }
    buffer.writeln();

    // 4. Tone & Length
    buffer.writeln('## TONE');
    buffer.writeln('${tone.displayName}: ${tone.instructionPrompt}');
    buffer.writeln();

    buffer.writeln('## LENGTH');
    buffer.writeln('${length.displayName}: ${length.constraintPrompt}');
    buffer.writeln();

    if (language.isNotEmpty && language.toLowerCase() != 'en') {
      buffer.writeln('## LANGUAGE');
      buffer.writeln('Write the message in language code: $language.');
      buffer.writeln();
    }

    // 5. Existing draft context if rewriting
    if (request.existingMessage != null &&
        request.existingMessage!.trim().isNotEmpty) {
      buffer.writeln('## CURRENT DRAFT');
      buffer.writeln(request.existingMessage!.trim());
      buffer.writeln();
    }

    // 6. Task
    buffer.writeln('## TASK');
    if (request.customInstruction != null &&
        request.customInstruction!.trim().isNotEmpty) {
      buffer.writeln(
        'Rewrite the birthday message incorporating this instruction: ${request.customInstruction!.trim()}',
      );
    } else if (request.existingMessage != null) {
      buffer.writeln(
        'Rewrite the current draft matching the requested tone, length, and language.',
      );
    } else {
      buffer.writeln(
        'Write a natural, personal birthday greeting for ${person.name}.',
      );
    }
    buffer.writeln();

    // 7. Strict Constraints (SSOT §21)
    buffer.writeln('## CONSTRAINTS');
    buffer.writeln(
      '- Do NOT invent recipient-specific facts, nicknames, or events not listed above.',
    );
    buffer.writeln(
      '- Do NOT mention AI, models, prompts, or that you are an assistant.',
    );
    buffer.writeln('- Do NOT include quotation marks around the message.');
    buffer.writeln('- Return ONLY the final message ready to send.');

    return buffer.toString();
  }
}
