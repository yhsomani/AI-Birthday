/// Drift-backed persistent implementations of domain repositories (legacy SSOT §13, §19 (PRODUCT.md §16.7)).
library;

import 'dart:async';
import 'dart:convert';
import 'package:drift/drift.dart';
import 'package:crypto/crypto.dart';

import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';
import 'package:ai_birthday/features/delivery/domain/repositories/delivery_events_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/domain/repositories/drafts_repository.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

/// Drift/SQLite-backed implementation of [BirthdaysRepository].
class DriftBirthdaysRepository implements BirthdaysRepository {
  DriftBirthdaysRepository(this._database);

  final db.AppDatabase _database;

  @override
  Stream<List<Birthday>> watchBirthdays() {
    return (_database.select(
      _database.birthdays,
    )..where((r) => r.deletedAt.isNull())).watch().map((rows) {
      final list = rows.map(_birthdayFromRow).toList();
      list.sort(_byDateThenId);
      return list;
    });
  }

  @override
  Future<List<Birthday>> getBirthdays() async {
    final rows = await (_database.select(
      _database.birthdays,
    )..where((r) => r.deletedAt.isNull())).get();
    final list = rows.map(_birthdayFromRow).toList();
    list.sort(_byDateThenId);
    return list;
  }

  @override
  Future<Birthday?> getBirthday(String id) async {
    final row = await (_database.select(
      _database.birthdays,
    )..where((r) => r.id.equals(id) & r.deletedAt.isNull())).getSingleOrNull();
    return row == null ? null : _birthdayFromRow(row);
  }

  @override
  Future<Birthday?> getBirthdayForPerson(String personId) async {
    final row =
        await (_database.select(_database.birthdays)
              ..where((r) => r.personId.equals(personId) & r.deletedAt.isNull())
              ..orderBy([
                (r) => OrderingTerm.desc(r.cycleYear),
                (r) => OrderingTerm.desc(r.updatedAt),
                (r) => OrderingTerm.desc(r.id),
              ])
              ..limit(1))
            .getSingleOrNull();
    return row == null ? null : _birthdayFromRow(row);
  }

  @override
  Future<void> saveBirthday(Birthday birthday) async {
    await _database
        .into(_database.birthdays)
        .insertOnConflictUpdate(
          db.BirthdaysCompanion(
            id: Value(birthday.id),
            personId: Value(birthday.personId),
            cycleYear: Value(birthday.cycleYear),
            date: Value(birthday.date),
            status: Value(birthday.status.name),
            draftId: Value(birthday.draftId),
            createdAt: Value(birthday.createdAt),
            updatedAt: Value(birthday.updatedAt),
          ),
        );
  }

  @override
  Future<void> rolloverBirthday(Birthday birthday) async {
    await _database.transaction(() async {
      await (_database.delete(_database.messageDrafts)..where(
            (row) =>
                row.birthdayId.equals(birthday.id) &
                row.status.equals(DraftStatus.confirmedSent.name).not(),
          ))
          .go();
      await (_database.delete(
        _database.jobs,
      )..where((row) => row.subjectId.equals(birthday.id))).go();
      await _database
          .into(_database.birthdays)
          .insertOnConflictUpdate(
            db.BirthdaysCompanion(
              id: Value(birthday.id),
              personId: Value(birthday.personId),
              cycleYear: Value(birthday.cycleYear),
              date: Value(birthday.date),
              status: Value(birthday.status.name),
              draftId: const Value(null),
              createdAt: Value(birthday.createdAt),
              updatedAt: Value(birthday.updatedAt),
            ),
          );
    });
  }

  @override
  Future<void> mergeDuplicateBirthdays(
    Birthday birthday, {
    required List<String> duplicateIds,
  }) async {
    final ids = duplicateIds.where((id) => id != birthday.id).toSet().toList();
    if (ids.isEmpty) return;

    await _database.transaction(() async {
      await (_database.update(_database.messageDrafts)
            ..where((row) => row.birthdayId.isIn(ids)))
          .write(db.MessageDraftsCompanion(birthdayId: Value(birthday.id)));
      await (_database.update(_database.deliveryEvents)
            ..where((row) => row.birthdayId.isIn(ids)))
          .write(db.DeliveryEventsCompanion(birthdayId: Value(birthday.id)));
      await (_database.update(_database.jobs)
            ..where((row) => row.subjectId.isIn(ids)))
          .write(db.JobsCompanion(subjectId: Value(birthday.id)));

      await _database
          .into(_database.birthdays)
          .insertOnConflictUpdate(
            db.BirthdaysCompanion(
              id: Value(birthday.id),
              personId: Value(birthday.personId),
              cycleYear: Value(birthday.cycleYear),
              date: Value(birthday.date),
              status: Value(birthday.status.name),
              draftId: Value(birthday.draftId),
              createdAt: Value(birthday.createdAt),
              updatedAt: Value(birthday.updatedAt),
            ),
          );
      await (_database.delete(
        _database.birthdays,
      )..where((row) => row.id.isIn(ids))).go();
    });
  }

  @override
  Future<void> updateBirthdayStatus(
    String id,
    BirthdayStatus status, {
    String? draftId,
  }) async {
    final existing = await getBirthday(id);
    if (existing != null) {
      await (_database.update(
        _database.birthdays,
      )..where((r) => r.id.equals(id))).write(
        db.BirthdaysCompanion(
          status: Value(status.name),
          draftId: draftId != null ? Value(draftId) : Value(existing.draftId),
          updatedAt: Value(DateTime.now()),
        ),
      );
    }
  }

  @override
  Future<void> deleteBirthday(String id) async {
    await (_database.delete(
      _database.birthdays,
    )..where((r) => r.id.equals(id))).go();
  }

  @override
  Future<void> disableBirthdayForPerson(String personId) async {
    await _database.transaction(() async {
      final rows =
          await (_database.select(_database.birthdays)..where(
                (row) => row.personId.equals(personId) & row.deletedAt.isNull(),
              ))
              .get();
      if (rows.isEmpty) return;
      final ids = rows.map((row) => row.id).toList();
      await (_database.delete(
        _database.birthdays,
      )..where((row) => row.id.isIn(ids))).go();
      await (_database.delete(_database.messageDrafts)..where(
            (row) =>
                row.birthdayId.isIn(ids) &
                row.status.equals(DraftStatus.confirmedSent.name).not(),
          ))
          .go();
      await (_database.delete(
        _database.jobs,
      )..where((row) => row.subjectId.isIn(ids))).go();
    });
  }

  static Birthday _birthdayFromRow(db.Birthday row) {
    return Birthday(
      id: row.id,
      personId: row.personId,
      cycleYear: row.cycleYear,
      date: row.date,
      status: BirthdayStatus.fromString(row.status),
      draftId: row.draftId,
      createdAt: row.createdAt.toUtc(),
      updatedAt: row.updatedAt.toUtc(),
    );
  }

  /// Deterministic total order (query audit Phase 4): date asc with the
  /// unique id as tie-breaker.
  static int _byDateThenId(Birthday a, Birthday b) {
    final byDate = Birthday.compareByDate(a, b);
    return byDate != 0 ? byDate : a.id.compareTo(b.id);
  }
}

/// Drift/SQLite-backed implementation of [DraftsRepository].
class DriftDraftsRepository implements DraftsRepository {
  DriftDraftsRepository(this._database);

  final db.AppDatabase _database;

  @override
  Stream<List<MessageDraft>> watchDrafts() {
    return (_database.select(
      _database.messageDrafts,
    )..where((r) => r.deletedAt.isNull())).watch().map((rows) {
      final list = rows.map(_draftFromRow).toList();
      list.sort(_byUpdatedAtDescThenIdDesc);
      return list;
    });
  }

  @override
  Future<List<MessageDraft>> getAllDrafts() async {
    final rows = await (_database.select(
      _database.messageDrafts,
    )..where((r) => r.deletedAt.isNull())).get();
    final list = rows.map(_draftFromRow).toList();
    list.sort(_byUpdatedAtDescThenIdDesc);
    return list;
  }

  @override
  Future<MessageDraft?> getDraft(String id) async {
    final row = await (_database.select(
      _database.messageDrafts,
    )..where((r) => r.id.equals(id) & r.deletedAt.isNull())).getSingleOrNull();
    return row == null ? null : _draftFromRow(row);
  }

  @override
  Future<MessageDraft?> getDraftForBirthday(String birthdayId) async {
    final birthday =
        await (_database.select(_database.birthdays)..where(
              (row) => row.id.equals(birthdayId) & row.deletedAt.isNull(),
            ))
            .getSingleOrNull();
    if (birthday?.draftId != null) {
      final linked =
          await (_database.select(_database.messageDrafts)..where(
                (row) =>
                    row.id.equals(birthday!.draftId!) & row.deletedAt.isNull(),
              ))
              .getSingleOrNull();
      return linked == null ? null : _draftFromRow(linked);
    }

    // Legacy rows may predate birthday.draftId, but an immutable sent draft
    // can never become the editable Studio draft for a replacement cycle.
    final row =
        await (_database.select(_database.messageDrafts)
              ..where(
                (r) =>
                    r.birthdayId.equals(birthdayId) &
                    r.deletedAt.isNull() &
                    r.status.equals(DraftStatus.confirmedSent.name).not(),
              )
              ..orderBy([
                (r) => OrderingTerm.desc(r.updatedAt),
                (r) => OrderingTerm.desc(r.id),
              ])
              ..limit(1))
            .getSingleOrNull();
    return row == null ? null : _draftFromRow(row);
  }

  @override
  Future<void> saveDraft(MessageDraft draft) =>
      _database.transaction(() => _saveDraft(draft));

  @override
  Future<bool> saveDraftAndUpdateBirthday(
    MessageDraft draft,
    BirthdayStatus birthdayStatus, {
    bool requireUnchanged = false,
    MessageDraft? expectedCurrentDraft,
    JobAttemptFence? jobFence,
  }) {
    return _database.transaction(() async {
      if (jobFence != null) {
        final owner = await (_database.select(
          _database.jobs,
        )..where((row) => row.id.equals(jobFence.jobId))).getSingleOrNull();
        if (owner?.status != JobStatus.running.name ||
            owner?.attempts != jobFence.attempt) {
          return false;
        }
      }

      if (requireUnchanged) {
        final birthday =
            await (_database.select(_database.birthdays)..where(
                  (row) =>
                      row.id.equals(draft.birthdayId) & row.deletedAt.isNull(),
                ))
                .getSingleOrNull();

        db.MessageDraft? active;
        if (birthday?.draftId != null) {
          active =
              await (_database.select(_database.messageDrafts)..where(
                    (row) =>
                        row.id.equals(birthday!.draftId!) &
                        row.deletedAt.isNull(),
                  ))
                  .getSingleOrNull();
        } else {
          active =
              await (_database.select(_database.messageDrafts)
                    ..where(
                      (row) =>
                          row.birthdayId.equals(draft.birthdayId) &
                          row.deletedAt.isNull() &
                          row.status
                              .equals(DraftStatus.confirmedSent.name)
                              .not(),
                    )
                    ..orderBy([
                      (row) => OrderingTerm.desc(row.updatedAt),
                      (row) => OrderingTerm.desc(row.id),
                    ])
                    ..limit(1))
                  .getSingleOrNull();
        }

        final unchanged = expectedCurrentDraft == null
            ? active == null
            : active != null &&
                  active.id == expectedCurrentDraft.id &&
                  active.revision == expectedCurrentDraft.revision;
        if (!unchanged) return false;
      }

      await _saveDraft(draft);
      final updated =
          await (_database.update(
            _database.birthdays,
          )..where((row) => row.id.equals(draft.birthdayId))).write(
            db.BirthdaysCompanion(
              status: Value(birthdayStatus.name),
              draftId: Value(draft.id),
              updatedAt: Value(draft.updatedAt),
            ),
          );
      if (updated != 1) {
        throw StateError('Cannot link a draft to a missing birthday.');
      }
      return true;
    });
  }

  Future<void> _saveDraft(MessageDraft draft) async {
    final existing = await (_database.select(
      _database.messageDrafts,
    )..where((row) => row.id.equals(draft.id))).getSingleOrNull();
    if (existing != null &&
        DraftStatus.fromString(existing.status) == DraftStatus.confirmedSent) {
      throw StateError('A confirmed-sent draft is immutable. Save a revision.');
    }

    // Monotonic regardless of the revision carried by the incoming model.
    final nextRevision = existing == null
        ? draft.revision + 1
        : existing.revision + 1;
    await _database
        .into(_database.messageDrafts)
        .insertOnConflictUpdate(
          db.MessageDraftsCompanion(
            id: Value(draft.id),
            birthdayId: Value(draft.birthdayId),
            personId: Value(draft.personId),
            body: Value(draft.body),
            tone: Value(draft.tone.name),
            length: Value(draft.length.name),
            status: Value(draft.status.name),
            providerType: Value(draft.providerType),
            variationIndex: Value(draft.variationIndex),
            revision: Value(nextRevision),
            createdAt: Value(draft.createdAt),
            updatedAt: Value(draft.updatedAt),
          ),
        );
  }

  @override
  Future<int> deleteDraftsForBirthday(String birthdayId) {
    return (_database.delete(
      _database.messageDrafts,
    )..where((row) => row.birthdayId.equals(birthdayId))).go();
  }

  @override
  Future<int> pruneOrphanedDrafts() async {
    final before = (await (_database.select(
      _database.messageDrafts,
    )..where((r) => r.deletedAt.isNull())).get()).length;

    final liveBirthdayIds = (await (_database.select(
      _database.birthdays,
    )..where((r) => r.deletedAt.isNull())).get()).map((r) => r.id).toList();

    if (liveBirthdayIds.isEmpty) {
      await (_database.delete(_database.messageDrafts)..where(
            (row) =>
                row.deletedAt.isNull() &
                row.status.equals(DraftStatus.confirmedSent.name).not(),
          ))
          .go();
    } else {
      // Mutable drafts whose birthday was deleted can never be applied again;
      // confirmed-sent history remains immutable and visible in History.
      await (_database.delete(_database.messageDrafts)..where(
            (row) =>
                row.deletedAt.isNull() &
                row.birthdayId.isNotIn(liveBirthdayIds) &
                row.status.equals(DraftStatus.confirmedSent.name).not(),
          ))
          .go();
    }

    // Legacy non-terminal duplicates can be removed, but confirmed history
    // and later editable revisions are intentionally append-only.
    final remaining = await (_database.select(
      _database.messageDrafts,
    )..where((r) => r.deletedAt.isNull())).get();
    final canonicalRows = {
      for (final row in remaining.where((r) => r.id == r.birthdayId))
        row.birthdayId: row,
    };
    final shadowedLegacyIds = remaining
        .where((row) {
          if (row.id == row.birthdayId ||
              DraftStatus.fromString(row.status) == DraftStatus.confirmedSent) {
            return false;
          }
          final canonical = canonicalRows[row.birthdayId];
          return canonical != null &&
              canonical.body == row.body &&
              canonical.tone == row.tone &&
              canonical.length == row.length &&
              canonical.status == row.status;
        })
        .map((row) => row.id)
        .toList();
    if (shadowedLegacyIds.isNotEmpty) {
      await (_database.delete(
        _database.messageDrafts,
      )..where((r) => r.id.isIn(shadowedLegacyIds))).go();
    }

    final after = (await (_database.select(
      _database.messageDrafts,
    )..where((r) => r.deletedAt.isNull())).get()).length;
    return before - after;
  }

  static MessageDraft _draftFromRow(db.MessageDraft row) {
    return MessageDraft(
      id: row.id,
      birthdayId: row.birthdayId,
      personId: row.personId,
      body: row.body,
      tone: MessageTone.fromString(row.tone),
      length: MessageLength.fromString(row.length),
      status: DraftStatus.fromString(row.status),
      providerType: row.providerType.isEmpty ? 'manual' : row.providerType,
      variationIndex: row.variationIndex,
      revision: row.revision,
      createdAt: row.createdAt.toUtc(),
      updatedAt: row.updatedAt.toUtc(),
    );
  }

  /// Deterministic total order (query audit Phase 4): newest-first with the
  /// unique id as tie-breaker — equal timestamps must not reorder between
  /// watch emissions.
  static int _byUpdatedAtDescThenIdDesc(MessageDraft a, MessageDraft b) {
    final byUpdatedAt = b.updatedAt.compareTo(a.updatedAt);
    return byUpdatedAt != 0 ? byUpdatedAt : b.id.compareTo(a.id);
  }
}

/// Drift/SQLite-backed implementation of [DeliveryEventsRepository].
class DriftDeliveryEventsRepository implements DeliveryEventsRepository {
  DriftDeliveryEventsRepository(this._database);

  final db.AppDatabase _database;
  int _eventSequence = 0;

  @override
  Future<void> recordHandoff({
    required String birthdayId,
    required DeliveryChannel channel,
    required DateTime at,
    String? draftId,
    int? draftRevision,
    String? bodyHash,
  }) => recordOutcome(
    birthdayId: birthdayId,
    draftId: draftId,
    draftRevision: draftRevision,
    bodyHash: bodyHash,
    channel: channel,
    outcome: DeliveryOutcome.launched,
    at: at,
  );

  @override
  Future<void> recordOutcome({
    required String birthdayId,
    required DeliveryChannel channel,
    required DeliveryOutcome outcome,
    required DateTime at,
    String? draftId,
    int? draftRevision,
    String? bodyHash,
  }) async {
    final utc = at.toUtc();
    await _database
        .into(_database.deliveryEvents)
        .insert(
          db.DeliveryEventsCompanion.insert(
            id: '$birthdayId-${draftId ?? 'legacy'}-${channel.name}-${outcome.name}-${utc.microsecondsSinceEpoch}-${DateTime.now().microsecondsSinceEpoch}-${_eventSequence++}',
            birthdayId: birthdayId,
            draftId: Value(draftId),
            draftRevision: Value(draftRevision),
            bodyHash: Value(bodyHash),
            channel: channel.name,
            outcome: Value(outcome.name),
            handedOffAt: utc,
          ),
        );
  }

  @override
  Future<void> recordHandoffAndMarkHandedOff({
    required String birthdayId,
    required DeliveryChannel channel,
    required DateTime at,
    String? draftId,
    int? draftRevision,
    String? bodyHash,
  }) {
    // One transaction: a failure part-way cannot leave a handed-off birthday
    // without its evidence row, or the reverse (audit F20).
    return _database.transaction(() async {
      await recordHandoff(
        birthdayId: birthdayId,
        draftId: draftId,
        draftRevision: draftRevision,
        bodyHash: bodyHash,
        channel: channel,
        at: at,
      );
      await (_database.update(
        _database.birthdays,
      )..where((r) => r.id.equals(birthdayId))).write(
        db.BirthdaysCompanion(
          status: Value(BirthdayStatus.handedOff.name),
          draftId: draftId != null ? Value(draftId) : const Value.absent(),
          updatedAt: Value(at.toUtc()),
        ),
      );
    });
  }

  @override
  Future<void> confirmHandoff({
    required String birthdayId,
    required String draftId,
    required int expectedDraftRevision,
    required String expectedBodyHash,
    required DateTime at,
  }) {
    return _database.transaction(() async {
      final handoff = await latestHandoffForBirthday(birthdayId);
      if (handoff == null ||
          handoff.draftId != draftId ||
          handoff.draftRevision != expectedDraftRevision ||
          handoff.bodyHash != expectedBodyHash) {
        throw StateError('Cannot confirm delivery without matching evidence.');
      }
      final birthday = await (_database.select(
        _database.birthdays,
      )..where((row) => row.id.equals(birthdayId))).getSingleOrNull();
      if (birthday == null ||
          birthday.status != BirthdayStatus.handedOff.name ||
          birthday.draftId != draftId) {
        throw StateError('The handed-off birthday no longer matches.');
      }
      final draft = await (_database.select(
        _database.messageDrafts,
      )..where((row) => row.id.equals(draftId))).getSingleOrNull();
      if (draft == null ||
          draft.birthdayId != birthdayId ||
          draft.revision != expectedDraftRevision ||
          sha256.convert(utf8.encode(draft.body.trim())).toString() !=
              expectedBodyHash) {
        throw StateError('The draft changed after this handoff.');
      }
      if (DraftStatus.fromString(draft.status) == DraftStatus.confirmedSent) {
        return;
      }
      final birthdayUpdated =
          await (_database.update(_database.birthdays)..where(
                (row) =>
                    row.id.equals(birthdayId) &
                    row.status.equals(BirthdayStatus.handedOff.name) &
                    row.draftId.equals(draftId),
              ))
              .write(
                db.BirthdaysCompanion(
                  status: Value(BirthdayStatus.completed.name),
                  draftId: Value(draftId),
                  updatedAt: Value(at.toUtc()),
                ),
              );
      final draftUpdated =
          await (_database.update(_database.messageDrafts)..where(
                (row) =>
                    row.id.equals(draftId) &
                    row.birthdayId.equals(birthdayId) &
                    row.revision.equals(expectedDraftRevision),
              ))
              .write(
                db.MessageDraftsCompanion(
                  status: Value(DraftStatus.confirmedSent.name),
                  updatedAt: Value(at.toUtc()),
                ),
              );
      if (birthdayUpdated != 1 || draftUpdated != 1) {
        throw StateError('The handoff changed during confirmation.');
      }
      await recordOutcome(
        birthdayId: birthdayId,
        draftId: draftId,
        draftRevision: expectedDraftRevision,
        bodyHash: expectedBodyHash,
        channel: handoff.channel,
        outcome: DeliveryOutcome.confirmed,
        at: at,
      );
    });
  }

  @override
  Future<DeliveryHandoff?> latestHandoffForBirthday(String birthdayId) async {
    final row =
        await (_database.select(_database.deliveryEvents)
              ..where(
                (r) =>
                    r.birthdayId.equals(birthdayId) &
                    r.outcome.equals(DeliveryOutcome.launched.name),
              )
              ..orderBy([(r) => OrderingTerm.desc(r.handedOffAt)])
              ..limit(1))
            .getSingleOrNull();
    return row == null ? null : _handoffFromRow(row);
  }

  @override
  Stream<List<DeliveryHandoff>> watchHandoffs() {
    return _database
        .select(_database.deliveryEvents)
        .watch()
        .map((rows) => rows.map(_handoffFromRow).toList());
  }

  @override
  Stream<List<DeliveryHandoff>> watchLatestHandoffs(Set<String> birthdayIds) {
    if (birthdayIds.isEmpty) return Stream.value(const []);
    // Query audit: "batched related data". Only events for the requested
    // birthdays are loaded — newest first — then reduced to the latest per
    // birthday. The full, unbounded event log is never materialized.
    return (_database.select(_database.deliveryEvents)
          ..where((r) => r.birthdayId.isIn(birthdayIds))
          ..orderBy([(r) => OrderingTerm.desc(r.handedOffAt)]))
        .watch()
        .map((rows) {
          final latest = <String, DeliveryHandoff>{};
          for (final row in rows) {
            final handoff = _handoffFromRow(row);
            final key = handoff.draftId ?? handoff.birthdayId;
            final existing = latest[key];
            if (existing == null || handoff.at.isAfter(existing.at)) {
              latest[key] = handoff;
            }
          }
          return latest.values.toList();
        });
  }

  static DeliveryHandoff _handoffFromRow(db.DeliveryEvent row) {
    return DeliveryHandoff(
      birthdayId: row.birthdayId,
      draftId: row.draftId,
      draftRevision: row.draftRevision,
      bodyHash: row.bodyHash,
      channel: DeliveryChannel.fromString(row.channel),
      outcome: DeliveryOutcome.fromString(row.outcome),
      at: row.handedOffAt.toUtc(),
    );
  }
}
