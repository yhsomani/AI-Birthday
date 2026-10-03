/// Message draft entity and draft states (SSOT §16).
library;

import 'package:ai_birthday/features/people/domain/models/tone.dart';

/// Editorial status of a birthday message draft.
enum DraftStatus {
  draft,
  reviewed,
  ready,
  handedOff,
  confirmedSent;

  String get displayName => switch (this) {
        DraftStatus.draft => 'Draft',
        DraftStatus.reviewed => 'Reviewed',
        DraftStatus.ready => 'Ready to Send',
        DraftStatus.handedOff => 'Handed Off',
        DraftStatus.confirmedSent => 'Sent & Confirmed',
      };

  static DraftStatus fromString(String? value) {
    if (value == null) return DraftStatus.draft;
    return DraftStatus.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
      orElse: () => DraftStatus.draft,
    );
  }
}

/// A prepared or generated message draft for a specific birthday event.
class MessageDraft {
  const MessageDraft({
    required this.id,
    required this.birthdayId,
    required this.personId,
    required this.body,
    this.tone = MessageTone.warm,
    this.length = MessageLength.standard,
    this.status = DraftStatus.draft,
    this.providerType = 'manual',
    this.variationIndex = 0,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String birthdayId;
  final String personId;

  /// The editable content of the message.
  final String body;

  /// The tone applied when generating or editing.
  final MessageTone tone;

  /// The length constraint applied.
  final MessageLength length;

  /// Editorial and review status.
  final DraftStatus status;

  /// AI provider used ('user_gemini', 'gemini_nano', 'manual').
  final String providerType;

  /// Index of the variation if multiple drafts were generated.
  final int variationIndex;

  final DateTime createdAt;
  final DateTime updatedAt;

  bool get isReviewed => status == DraftStatus.reviewed || status == DraftStatus.ready;

  MessageDraft copyWith({
    String? id,
    String? birthdayId,
    String? personId,
    String? body,
    MessageTone? tone,
    MessageLength? length,
    DraftStatus? status,
    String? providerType,
    int? variationIndex,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return MessageDraft(
      id: id ?? this.id,
      birthdayId: birthdayId ?? this.birthdayId,
      personId: personId ?? this.personId,
      body: body ?? this.body,
      tone: tone ?? this.tone,
      length: length ?? this.length,
      status: status ?? this.status,
      providerType: providerType ?? this.providerType,
      variationIndex: variationIndex ?? this.variationIndex,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'birthdayId': birthdayId,
        'personId': personId,
        'body': body,
        'tone': tone.name,
        'length': length.name,
        'status': status.name,
        'providerType': providerType,
        'variationIndex': variationIndex,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };

  factory MessageDraft.fromJson(Map<String, dynamic> json) {
    return MessageDraft(
      id: json['id'] as String,
      birthdayId: json['birthdayId'] as String,
      personId: json['personId'] as String,
      body: json['body'] as String,
      tone: MessageTone.fromString(json['tone'] as String?),
      length: MessageLength.fromString(json['length'] as String?),
      status: DraftStatus.fromString(json['status'] as String?),
      providerType: (json['providerType'] as String?) ?? 'manual',
      variationIndex: (json['variationIndex'] as int?) ?? 0,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }
}
