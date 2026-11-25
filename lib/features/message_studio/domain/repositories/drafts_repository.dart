import 'dart:async';

import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';

abstract interface class DraftsRepository {
  Stream<List<MessageDraft>> watchDrafts();
  Future<List<MessageDraft>> getAllDrafts();
  Future<MessageDraft?> getDraft(String id);
  Future<MessageDraft?> getDraftForBirthday(String birthdayId);
  Future<void> saveDraft(MessageDraft draft);

  /// Persists a draft and its active birthday link/status as one aggregate.
  Future<bool> saveDraftAndUpdateBirthday(
    MessageDraft draft,
    BirthdayStatus birthdayStatus, {
    bool requireUnchanged = false,
    MessageDraft? expectedCurrentDraft,
    JobAttemptFence? jobFence,
  });

  Future<int> deleteDraftsForBirthday(String birthdayId);

  /// Removes unreachable draft rows and returns how many were deleted: drafts
  /// whose birthday no longer exists, plus legacy rows keyed by their own id
  /// (`draft-…`) that duplicate the canonical row for the same birthday. A
  /// legacy-only draft for a live birthday is kept — the Studio still loads it
  /// by birthdayId and would lose the user's text if removed.
  Future<int> pruneOrphanedDrafts();
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
  final StreamController<List<MessageDraft>> _controller =
      StreamController<List<MessageDraft>>.broadcast();

  @override
  Stream<List<MessageDraft>> watchDrafts() async* {
    yield _sortedDrafts();
    yield* _controller.stream;
  }

  @override
  Future<List<MessageDraft>> getAllDrafts() async => _sortedDrafts();

  @override
  Future<MessageDraft?> getDraft(String id) async => _store[id];

  @override
  Future<MessageDraft?> getDraftForBirthday(String birthdayId) async {
    final matches =
        _store.values.where((draft) => draft.birthdayId == birthdayId).toList()
          ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return matches.isEmpty ? null : matches.first;
  }

  @override
  Future<void> saveDraft(MessageDraft draft) async {
    if (_store[draft.id]?.status == DraftStatus.confirmedSent) {
      throw StateError('A confirmed-sent draft is immutable. Save a revision.');
    }
    // Mirrors the persistent store: revision is repository-owned and monotonic.
    final existing = _store[draft.id];
    _store[draft.id] = draft.copyWith(
      revision: existing == null ? draft.revision + 1 : existing.revision + 1,
    );
    _controller.add(_sortedDrafts());
  }

  @override
  Future<bool> saveDraftAndUpdateBirthday(
    MessageDraft draft,
    BirthdayStatus birthdayStatus, {
    bool requireUnchanged = false,
    MessageDraft? expectedCurrentDraft,
    JobAttemptFence? jobFence,
  }) async {
    // This store has no durable job table with which to validate ownership.
    // Fail closed rather than allowing a fenced background write.
    if (jobFence != null) return false;
    if (requireUnchanged) {
      final active = await getDraftForBirthday(draft.birthdayId);
      final unchanged = expectedCurrentDraft == null
          ? active == null
          : active != null &&
                active.id == expectedCurrentDraft.id &&
                active.revision == expectedCurrentDraft.revision;
      if (!unchanged) return false;
    }
    await saveDraft(draft);
    return true;
  }

  @override
  Future<int> deleteDraftsForBirthday(String birthdayId) async {
    final ids = _store.values
        .where((draft) => draft.birthdayId == birthdayId)
        .map((draft) => draft.id)
        .toList();
    for (final id in ids) {
      _store.remove(id);
    }
    if (ids.isNotEmpty) _controller.add(_sortedDrafts());
    return ids.length;
  }

  @override
  Future<int> pruneOrphanedDrafts() async {
    // This store is built in memory from explicit inputs and exposes no
    // birthday knowledge, so it can never accumulate shadowed/orphaned rows
    // the way the persistence layer can.
    return 0;
  }

  List<MessageDraft> _sortedDrafts() {
    final list = _store.values.toList();
    list.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return list;
  }
}
