/// Tone and length constraints for message generation and review (SSOT §6, §16).
library;

/// Desired tone for the generated or drafted birthday message.
enum MessageTone {
  warm,
  funny,
  emotional,
  casual,
  professional;

  String get displayName => switch (this) {
        MessageTone.warm => 'Warm',
        MessageTone.funny => 'Funny',
        MessageTone.emotional => 'Emotional',
        MessageTone.casual => 'Casual',
        MessageTone.professional => 'Professional',
      };

  String get instructionPrompt => switch (this) {
        MessageTone.warm => 'Warm, affectionate, and thoughtful.',
        MessageTone.funny => 'Light-hearted, playful, and humorous without being mean.',
        MessageTone.emotional => 'Deeply heartfelt, touching, and sincere.',
        MessageTone.casual => 'Relaxed, friendly, and conversational.',
        MessageTone.professional => 'Polite, respectful, and cordial.',
      };

  static MessageTone fromString(String? value) {
    if (value == null) return MessageTone.warm;
    return MessageTone.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
      orElse: () => MessageTone.warm,
    );
  }
}

/// Length constraint for the message.
enum MessageLength {
  short,
  standard,
  expanded;

  String get displayName => switch (this) {
        MessageLength.short => 'Short (1-2 sentences)',
        MessageLength.standard => 'Standard (2-3 sentences)',
        MessageLength.expanded => 'Expanded (3-5 sentences)',
      };

  String get constraintPrompt => switch (this) {
        MessageLength.short => 'Keep it very concise, 1 to 2 sentences maximum.',
        MessageLength.standard => 'Keep it standard length, approximately 2 to 3 sentences.',
        MessageLength.expanded => 'Write an expanded message, 3 to 5 sentences with rich detail.',
      };

  static MessageLength fromString(String? value) {
    if (value == null) return MessageLength.standard;
    return MessageLength.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
      orElse: () => MessageLength.standard,
    );
  }
}
