import 'dart:async';

import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';

abstract interface class DraftsRepository {
  Stream<List<MessageDraft>> watchDrafts();
  Future<List<MessageDraft>> getAllDrafts();
  Future<MessageDraft?> getDraft(String id);
  Future<MessageDraft?> getDraftForBirthday(String birthdayId);
  Future<void> saveDraft(MessageDraft draft);

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
    return _store.values.cast<MessageDraft?>().firstWhere(
      (d) => d?.birthdayId == birthdayId,
      orElse: () => null,
    );
  }

  @override
  Future<void> saveDraft(MessageDraft draft) async {
    _store[draft.id] = draft;
    _controller.add(_sortedDrafts());
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
