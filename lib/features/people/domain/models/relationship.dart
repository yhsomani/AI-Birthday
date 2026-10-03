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

  static RelationshipCloseness fromString(String? value) {
    if (value == null) return RelationshipCloseness.casual;
    return RelationshipCloseness.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
      orElse: () => RelationshipCloseness.casual,
    );
  }
}
