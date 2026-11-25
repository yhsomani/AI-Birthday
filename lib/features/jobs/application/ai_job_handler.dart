/// Durable `ai_generate` job handler.
///
/// The handler SPLITS generation from application so cancel-while-running and
/// stale-result semantics stay honest:
/// 1. Generate the message (pure — the call itself has no app-visible effect).
/// 2. Discard the result if the job was canceled mid-flight.
/// 3. Discard the result if the draft changed after enqueue (the user edited
///    while the request was in flight — newer text always wins).
/// 4. Otherwise apply via an idempotent upsert (stable draft id = birthday
///    id, like every other save path in the studio), then mark the birthday.
///
/// Cases 2-3 (and a lost compare-and-set on apply) return [JobSkipped] so the
/// caller can say "result not applied" instead of claiming success.
///
/// Duplicate delivery after a crash is safe by construction: the apply is an
/// upsert of the same row with the same body, and the guards make a stale
/// re-apply impossible.
library;

import 'dart:convert';

import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/domain/repositories/drafts_repository.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/domain/repositories/people_repository.dart';

import '../domain/job.dart';
import '../data/jobs_repository.dart';
import 'job_worker.dart';

/// Non-secret inputs for one generation, persisted in the job payload so the
/// handler can re-run after a restart.
class AiJobPayload {
  const AiJobPayload({
    required this.birthdayId,
    required this.personId,
    required this.tone,
    required this.length,
    this.customInstruction,
    this.rewriteExisting = false,
    this.targetLanguage,
    this.forceNano = false,
    this.draftUpdatedAt,
    this.draftRevision,
    this.targetCycleYear,
  });

  final String birthdayId;
  final String personId;
  final MessageTone tone;
  final MessageLength length;
  final String? customInstruction;
  final bool rewriteExisting;
  final String? targetLanguage;
  final bool forceNano;

  /// UTC ISO-8601 `updatedAt` of the draft at enqueue time (null when no
  /// draft row existed — a fresh generation). The stale guard applies the
  /// result only if the draft is still unchanged.
  final String? draftUpdatedAt;

  /// Monotonic `revision` of that draft at enqueue time. Unlike [draftUpdatedAt]
  /// (1 s precision) it distinguishes two writes inside the same second.
  final int? draftRevision;

  /// Calendar year this message targets (the birthday row's `cycleYear`),
  /// persisted so "Turning age" in the prompt is stable across restarts and
  /// year boundaries instead of drifting to `DateTime.now().year`.
  final int? targetCycleYear;

  Map<String, dynamic> toJson() => {
    'birthdayId': birthdayId,
    'personId': personId,
    'tone': tone.name,
    'length': length.name,
    'customInstruction': customInstruction,
    'rewriteExisting': rewriteExisting,
    'targetLanguage': targetLanguage,
    'forceNano': forceNano,
    'draftUpdatedAt': draftUpdatedAt,
    'draftRevision': draftRevision,
    'targetCycleYear': targetCycleYear,
  };

  static AiJobPayload fromJson(String? raw) {
    final json = raw == null ? const <String, dynamic>{} : jsonDecode(raw);
    return AiJobPayload(
      birthdayId: json['birthdayId'] as String,
      personId: json['personId'] as String,
      tone: MessageTone.fromString(json['tone'] as String?),
      length: MessageLength.fromString(json['length'] as String?),
      customInstruction: json['customInstruction'] as String?,
      rewriteExisting:
          json['rewriteExisting'] as bool? ??
          ((json['existingMessage'] as String?)?.trim().isNotEmpty ?? false),
      targetLanguage: json['targetLanguage'] as String?,
      forceNano: json['forceNano'] as bool? ?? false,
      draftUpdatedAt: json['draftUpdatedAt'] as String?,
      draftRevision: json['draftRevision'] as int?,
      targetCycleYear: json['targetCycleYear'] as int?,
    );
  }
}

class AiGenerationJobHandler implements JobHandler {
  AiGenerationJobHandler({
    required this._peopleRepository,
    required this._draftsRepository,
    required this._birthdaysRepository,
    required this._jobsRepository,
    required this._generate,
  });

  final PeopleRepository _peopleRepository;
  final DraftsRepository _draftsRepository;
  final BirthdaysRepository _birthdaysRepository;
  final JobsRepository _jobsRepository;
  final Future<AiGenerationResult> Function(
    AiGenerationRequest request, {
    required bool forceNano,
  })
  _generate;

  @override
  Future<JobOutcome> run(JobRecord job) async {
    final payload = AiJobPayload.fromJson(job.payload);

    final person = await _peopleRepository.getPerson(payload.personId);
    if (person == null) {
      return JobFailed(
        retryable: false,
        code: 'notFound',
        message: 'Recipient no longer exists, so a message was not drafted.',
      );
    }
    if (!person.hasResolvableBirthday) {
      return JobFailed(
        retryable: false,
        code: 'requiresRepair',
        message:
            'Choose a non-leap-year celebration date before drafting a message.',
      );
    }
    final birthday = await _birthdaysRepository.getBirthday(payload.birthdayId);
    if (birthday == null) {
      return JobFailed(
        retryable: false,
        code: 'notFound',
        message: 'The birthday event no longer exists.',
      );
    }

    final rewriteDraft = payload.rewriteExisting
        ? await _draftsRepository.getDraftForBirthday(payload.birthdayId)
        : null;
    if (payload.rewriteExisting && rewriteDraft == null) {
      return JobFailed(
        retryable: false,
        code: 'notFound',
        message: 'The draft to rewrite no longer exists.',
      );
    }

    final result = await _generate(
      AiGenerationRequest(
        person: person,
        tone: payload.tone,
        length: payload.length,
        customInstruction: payload.customInstruction,
        existingMessage: rewriteDraft?.body,
        targetLanguage: payload.targetLanguage,
        targetCycleYear: payload.targetCycleYear ?? birthday.cycleYear,
      ),
      forceNano: payload.forceNano,
    );

    // Cancel-while-running: a cancel that landed during generation discards
    // the result; the worker leaves the row canceled.
    if (await _jobsRepository.isCanceled(job.id)) {
      return const JobSkipped('canceled');
    }

    // Cycle rollover guard: re-verify the birthday still exists and targets the
    // same cycle year so generation results are never applied across year boundaries.
    final latestBirthday = await _birthdaysRepository.getBirthday(
      payload.birthdayId,
    );
    final targetCycleYear = payload.targetCycleYear ?? birthday.cycleYear;
    if (latestBirthday == null || latestBirthday.cycleYear != targetCycleYear) {
      return const JobSkipped('cycleChanged');
    }

    // Stale guard: if the draft changed after this job was enqueued, the
    // newer text wins and the AI text is deliberately dropped (no clobber).
    final current = await _draftsRepository.getDraftForBirthday(
      payload.birthdayId,
    );
    final enqueuedAt = payload.draftUpdatedAt == null
        ? null
        : DateTime.tryParse(payload.draftUpdatedAt!);
    final stale =
        current != null &&
        (enqueuedAt == null ||
            current.updatedAt.isAfter(enqueuedAt) ||
            (payload.draftRevision != null &&
                current.revision != payload.draftRevision));
    if (stale) return const JobSkipped('stale');

    // Apply: normal retries converge on the active row, while an immutable
    // confirmed-sent row is preserved by creating a new revision.
    final now = DateTime.now();
    final canonicalDraft = await _draftsRepository.getDraft(payload.birthdayId);
    final startsNewRevision =
        current?.status == DraftStatus.confirmedSent ||
        (latestBirthday.draftId == null && canonicalDraft != null);
    final revisionId = startsNewRevision
        ? '${payload.birthdayId}-revision-${now.microsecondsSinceEpoch}'
        : payload.birthdayId;
    final applied = await _draftsRepository.saveDraftAndUpdateBirthday(
      MessageDraft(
        id: revisionId,
        birthdayId: payload.birthdayId,
        personId: payload.personId,
        body: result.message,
        tone: payload.tone,
        length: payload.length,
        status: DraftStatus.draft,
        providerType: result.providerType,
        createdAt: startsNewRevision ? now : current?.createdAt ?? now,
        updatedAt: now,
      ),
      BirthdayStatus.messageDrafted,
      requireUnchanged: true,
      expectedCurrentDraft: current,
      jobFence: JobAttemptFence(jobId: job.id, attempt: job.attempts),
    );
    if (!applied) {
      return const JobSkipped('conflict');
    }

    return JobSucceeded(revisionId);
  }
}
