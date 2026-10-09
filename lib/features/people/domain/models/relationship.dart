/// Relationship metadata for recipients in AI-Birthday (SSOT §7).
library;

/// High-level category of relationship to the user.
enum RelationshipCategory {
  family,
  friend,
  colleague,
  partner,
  mentor,
  other;

  String get displayName => switch (this) {
    RelationshipCategory.family => 'Family',
    RelationshipCategory.friend => 'Friend',
    RelationshipCategory.colleague => 'Colleague',
    RelationshipCategory.partner => 'Partner',
    RelationshipCategory.mentor => 'Mentor',
    RelationshipCategory.other => 'Other',
  };

  static RelationshipCategory fromString(String? value) {
    if (value == null) return RelationshipCategory.other;
    return RelationshipCategory.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
      orElse: () => RelationshipCategory.other,
    );
  }
}

/// The closeness level of the relationship.
enum RelationshipCloseness {
  close,
  casual,
  distant;

  String get displayName => switch (this) {
    RelationshipCloseness.close => 'Close',
    RelationshipCloseness.casual => 'Casual',
    RelationshipCloseness.distant => 'Distant',
  };

  /// Reads a stored closeness. Rows written before the three-level set
  /// (family, goodFriend, friend, acquaintance, colleague, other) collapse
  /// onto the canonical levels here, so no migration is required (audit F10).
  static RelationshipCloseness fromStored(String? value) {
    return switch (value?.toLowerCase()) {
      'close' || 'family' || 'goodfriend' => RelationshipCloseness.close,
      'distant' => RelationshipCloseness.distant,
      _ => RelationshipCloseness.casual,
    };
  }

  static RelationshipCloseness fromString(String? value) => fromStored(value);
}
