/// Repository contract for managing message drafts (SSOT §16).
library;

import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';

abstract interface class DraftsRepository {
  Future<MessageDraft?> getDraft(String id);
  Future<MessageDraft?> getDraftForBirthday(String birthdayId);
  Future<void> saveDraft(MessageDraft draft);
  Future<void> deleteDraft(String id);
}

/// In-memory implementation of [DraftsRepository] for testing and offline prototyping.
class InMemoryDraftsRepository implements DraftsRepository {
  InMemoryDraftsRepository({List<MessageDraft>? initialDrafts}) {
    if (initialDrafts != null) {
      for (final d in initialDrafts) {
        _store[d.id] = d;
      }
    }
  }

  final Map<String, MessageDraft> _store = {};

  @override
  Future<MessageDraft?> getDraft(String id) async => _store[id];

  @override
  Future<MessageDraft?> getDraftForBirthday(String birthdayId) async {
    return _store.values.cast<MessageDraft?>().firstWhere(
      (d) => d?.birthdayId == birthdayId,
      orElse: () => null,
    );
  }

  @override
  Future<void> saveDraft(MessageDraft draft) async {
    _store[draft.id] = draft;
  }

  @override
  Future<void> deleteDraft(String id) async {
    _store.remove(id);
  }
}
