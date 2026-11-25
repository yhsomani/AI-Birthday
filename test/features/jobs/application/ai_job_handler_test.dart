/// Durable `ai_generate` handler: idempotent apply, stale guard, cancel
/// discard, and producer-failure mapping (background-jobs audit).
library;

import 'dart:async';
import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/database/app_database.dart' show AppDatabase;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/jobs/application/ai_job_handler.dart';
import 'package:ai_birthday/features/jobs/application/job_worker.dart';
import 'package:ai_birthday/features/jobs/data/jobs_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

import '../../../helpers/counting_query_interceptor.dart';

void main() {
  allowMultipleInMemoryDatabases();
  late AppDatabase db;
  late DriftPeopleRepository peopleRepo;
  late DriftBirthdaysRepository birthdaysRepo;
  late DriftDraftsRepository draftsRepo;
  late DriftJobsRepository jobsRepo;
  late int generateCalls;
  late String? generatedExistingMessage;

  setUp(() async {
    db = AppDatabase(NativeDatabase.memory());
    peopleRepo = DriftPeopleRepository(db);
    birthdaysRepo = DriftBirthdaysRepository(db);
    draftsRepo = DriftDraftsRepository(db);
    jobsRepo = DriftJobsRepository(db);
    generateCalls = 0;
    generatedExistingMessage = null;
  });

  tearDown(() => db.close());

  final now = DateTime.now();

  Future<Person> seedPerson() async {
    final person = Person(
      id: 'p1',
      name: 'Test Person',
      birthdayMonth: now.month,
      birthdayDay: now.day,
      birthYear: 1990,
      relationship: RelationshipCategory.friend,
      relationshipCloseness: RelationshipCloseness.close,
      preferredLanguage: 'en',
      preferredTone: MessageTone.warm,
      createdAt: now,
      updatedAt: now,
      version: 1,
    );
    await peopleRepo.savePerson(person);
    return person;
  }

  Future<Birthday> seedBirthday() async {
    final birthday = Birthday(
      id: 'b1',
      personId: 'p1',
      cycleYear: now.year,
      date: now,
      status: BirthdayStatus.messageNotPrepared,
      createdAt: now,
      updatedAt: now,
    );
    await birthdaysRepo.saveBirthday(birthday);
    return birthday;
  }

  AiGenerationJobHandler handler() {
    return AiGenerationJobHandler(
      peopleRepository: peopleRepo,
      draftsRepository: draftsRepo,
      birthdaysRepository: birthdaysRepo,
      jobsRepository: jobsRepo,
      generate: (request, {required bool forceNano}) async {
        generateCalls++;
        generatedExistingMessage = request.existingMessage;
        expect(forceNano, isFalse);
        expect(request.person.id, 'p1');
        return const AiGenerationResult(
          message: 'AI drafted message',
          providerType: 'user_gemini',
        );
      },
    );
  }

  Future<JobRecord> claimedJob({
    required AiJobPayload payload,
    int maxAttempts = 1,
  }) async {
    await jobsRepo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: payload.birthdayId,
      payload: jsonEncode(payload.toJson()),
      maxAttempts: maxAttempts,
    );
    return (await jobsRepo.claimNext(DateTime.now()))!;
  }

  test(
    'A2-022: rewrite job stores a flag and reloads the draft text',
    () async {
      await seedPerson();
      await seedBirthday();
      final initial = MessageDraft(
        id: 'b1',
        birthdayId: 'b1',
        personId: 'p1',
        body: 'Private draft text',
        createdAt: now,
        updatedAt: now,
      );
      await draftsRepo.saveDraftAndUpdateBirthday(
        initial,
        BirthdayStatus.messageDrafted,
      );
      final persisted = (await draftsRepo.getDraftForBirthday('b1'))!;
      final payload = AiJobPayload(
        birthdayId: 'b1',
        personId: 'p1',
        tone: MessageTone.warm,
        length: MessageLength.standard,
        rewriteExisting: true,
        draftUpdatedAt: persisted.updatedAt.toIso8601String(),
        draftRevision: persisted.revision,
      );

      expect(payload.toJson(), isNot(contains('existingMessage')));
      final outcome = await handler().run(await claimedJob(payload: payload));

      expect(outcome, isA<JobSucceeded>());
      expect(generatedExistingMessage, 'Private draft text');
    },
  );

  test(
    'unresolved leap policy blocks generation without deleting the job',
    () async {
      final person = Person(
        id: 'p1',
        name: 'Legacy Leap',
        birthdayMonth: 2,
        birthdayDay: 29,
        createdAt: now,
        updatedAt: now,
      );
      await peopleRepo.savePerson(person);
      final birthday = await seedBirthday();
      final payload = AiJobPayload(
        birthdayId: birthday.id,
        personId: person.id,
        tone: MessageTone.warm,
        length: MessageLength.standard,
      );
      final job = await claimedJob(payload: payload);

      final outcome = await handler().run(job);

      expect(outcome, isA<JobFailed>());
      expect(generateCalls, 0);
      expect(await jobsRepo.getById(job.id), isNotNull);
    },
  );

  test(
    'applies the generated message idempotently onto the stable draft row',
    () async {
      await seedPerson();
      await seedBirthday();

      final outcome = await handler().run(
        await claimedJob(
          payload: AiJobPayload(
            birthdayId: 'b1',
            personId: 'p1',
            tone: MessageTone.warm,
            length: MessageLength.standard,
          ),
        ),
      );

      expect(outcome, isA<JobSucceeded>());
      expect((outcome as JobSucceeded).resultRef, 'b1');
      expect(generateCalls, 1);
      final draft = (await draftsRepo.getDraftForBirthday('b1'))!;
      expect(draft.id, 'b1'); // stable id = birthday id, never an orphan
      expect(draft.body, 'AI drafted message');
      expect(draft.birthdayId, 'b1');
      expect(draft.personId, 'p1');
      expect(draft.providerType, 'user_gemini');
      final birthday = (await birthdaysRepo.getBirthday('b1'))!;
      expect(birthday.status, BirthdayStatus.messageDrafted);
      expect(birthday.draftId, 'b1');
    },
  );

  test(
    'rollover with null draft link preserves confirmed canonical row',
    () async {
      await seedPerson();
      final birthday = await seedBirthday();
      final confirmedAt = DateTime.fromMillisecondsSinceEpoch(
        now.subtract(const Duration(days: 365)).millisecondsSinceEpoch ~/
            1000 *
            1000,
        isUtc: true,
      );
      final confirmed = MessageDraft(
        id: birthday.id,
        birthdayId: birthday.id,
        personId: birthday.personId,
        body: 'Last cycle confirmed message',
        status: DraftStatus.confirmedSent,
        createdAt: confirmedAt,
        updatedAt: confirmedAt,
      );
      await draftsRepo.saveDraft(confirmed);
      // Rollover/new-cycle state intentionally has no active draft link while
      // retaining prior immutable history at the canonical id.
      await birthdaysRepo.saveBirthday(birthday.copyWith(clearDraftId: true));

      final outcome = await handler().run(
        await claimedJob(
          payload: AiJobPayload(
            birthdayId: birthday.id,
            personId: birthday.personId,
            tone: MessageTone.warm,
            length: MessageLength.standard,
            draftUpdatedAt: confirmedAt.toUtc().toIso8601String(),
          ),
        ),
      );

      expect(outcome, isA<JobSucceeded>());
      final resultId = (outcome as JobSucceeded).resultRef;
      expect(resultId, isNotNull, reason: 'AI result must not be discarded');
      expect(resultId, isNot(birthday.id));
      final drafts = await draftsRepo.getAllDrafts();
      expect(drafts, hasLength(2));
      final canonical = drafts.singleWhere((draft) => draft.id == birthday.id);
      expect(canonical.body, 'Last cycle confirmed message');
      expect(canonical.status, DraftStatus.confirmedSent);
      expect(
        drafts.singleWhere((draft) => draft.id == resultId).body,
        'AI drafted message',
      );
      expect((await birthdaysRepo.getBirthday(birthday.id))?.draftId, resultId);
    },
  );

  test(
    'duplicate delivery converges instead of duplicating or clobbering',
    () async {
      await seedPerson();
      await seedBirthday();

      final h = handler();
      final payload = AiJobPayload(
        birthdayId: 'b1',
        personId: 'p1',
        tone: MessageTone.warm,
        length: MessageLength.standard,
      );
      final job = await claimedJob(payload: payload);
      await h.run(job);
      // The same job delivered again (crash recovery): its own first apply has
      // since bumped the draft's updatedAt, so the stale guard skips — the
      // effect is already durable and nothing is rewritten or clobbered.
      await h.run(job);

      expect(generateCalls, 2); // delivered twice
      final drafts = await draftsRepo.getAllDrafts();
      expect(drafts, hasLength(1)); // one row, not an orphan, not a duplicate
      expect(drafts.single.id, 'b1');
      expect(drafts.single.body, 'AI drafted message');
    },
  );

  test(
    'stale guard: text written after enqueue wins, AI result is dropped',
    () async {
      await seedPerson();
      await seedBirthday();
      // The user has a draft before generating (enqueued at t0).
      final t0 = now.subtract(const Duration(minutes: 1));
      await draftsRepo.saveDraft(
        MessageDraft(
          id: 'b1',
          birthdayId: 'b1',
          personId: 'p1',
          body: 'original draft',
          createdAt: t0,
          updatedAt: t0,
        ),
      );

      // While the job is in flight, the user edits the draft again (t1).
      final t1 = now.subtract(const Duration(seconds: 30));
      await draftsRepo.saveDraft(
        MessageDraft(
          id: 'b1',
          birthdayId: 'b1',
          personId: 'p1',
          body: 'user edits made while drafting',
          createdAt: t0,
          updatedAt: t1,
        ),
      );

      final outcome = await handler().run(
        await claimedJob(
          payload: AiJobPayload(
            birthdayId: 'b1',
            personId: 'p1',
            tone: MessageTone.warm,
            length: MessageLength.standard,
            draftUpdatedAt: t0.toUtc().toIso8601String(),
          ),
        ),
      );

      expect(outcome, isA<JobSkipped>());
      expect((outcome as JobSkipped).reason, 'stale');
      final draft = (await draftsRepo.getDraftForBirthday('b1'))!;
      expect(draft.body, 'user edits made while drafting'); // never clobbered
      final birthday = (await birthdaysRepo.getBirthday('b1'))!;
      expect(birthday.status, BirthdayStatus.messageNotPrepared); // untouched
    },
  );

  test(
    'cycle rollover guard: birthday advanced to new cycle while job in flight drops result',
    () async {
      await seedPerson();
      final birthday = await seedBirthday();

      final h = AiGenerationJobHandler(
        peopleRepository: peopleRepo,
        draftsRepository: draftsRepo,
        birthdaysRepository: birthdaysRepo,
        jobsRepository: jobsRepo,
        generate: (request, {required bool forceNano}) async {
          await birthdaysRepo.saveBirthday(
            birthday.copyWith(cycleYear: now.year + 1),
          );
          return const AiGenerationResult(
            message: 'stale cycle message',
            providerType: 'user_gemini',
          );
        },
      );

      final outcome = await h.run(
        await claimedJob(
          payload: AiJobPayload(
            birthdayId: 'b1',
            personId: 'p1',
            tone: MessageTone.warm,
            length: MessageLength.standard,
            targetCycleYear: now.year,
          ),
        ),
      );

      // Dropped results are reported neutrally, not as success.
      expect(outcome, isA<JobSkipped>());
      expect((outcome as JobSkipped).reason, 'cycleChanged');
      final draft = await draftsRepo.getDraftForBirthday('b1');
      expect(draft, isNull);
    },
  );

  test('missing person or birthday fails permanently with notFound', () async {
    // No person seeded.
    final missingPerson = await handler().run(
      await claimedJob(
        payload: AiJobPayload(
          birthdayId: 'b1',
          personId: 'nobody',
          tone: MessageTone.warm,
          length: MessageLength.standard,
        ),
      ),
    );
    expect(missingPerson, isA<JobFailed>());
    expect((missingPerson as JobFailed).retryable, isFalse);
    expect(missingPerson.code, 'notFound');
    expect(generateCalls, 0);

    await seedPerson(); // but still no birthday
    final missingBirthday = await handler().run(
      await claimedJob(
        payload: AiJobPayload(
          birthdayId: 'b2',
          personId: 'p1',
          tone: MessageTone.warm,
          length: MessageLength.standard,
        ),
      ),
    );
    expect(missingBirthday, isA<JobFailed>());
    expect((missingBirthday as JobFailed).code, 'notFound');
    expect(generateCalls, 0);
  });

  test(
    'cancel while running discards the result (worker leaves row canceled)',
    () async {
      await seedPerson();
      await seedBirthday();

      final job = await claimedJob(
        payload: AiJobPayload(
          birthdayId: 'b1',
          personId: 'p1',
          tone: MessageTone.warm,
          length: MessageLength.standard,
        ),
      );
      await jobsRepo.cancel(job.id);

      final outcome = await handler().run(job);

      expect(outcome, isA<JobSkipped>()); // discarded, not an error
      expect((outcome as JobSkipped).reason, 'canceled');
      expect(generateCalls, 1); // the call itself happened
      expect(await draftsRepo.getDraftForBirthday('b1'), isNull); // no apply
      expect(
        (await birthdaysRepo.getBirthday('b1'))!.status,
        BirthdayStatus.messageNotPrepared,
      );
    },
  );

  test('timed-out attempt cannot apply its late result after retry succeeds', () async {
    await seedPerson();
    await seedBirthday();
    final lateResult = Completer<AiGenerationResult>();
    var calls = 0;
    final fencedHandler = AiGenerationJobHandler(
      peopleRepository: peopleRepo,
      draftsRepository: draftsRepo,
      birthdaysRepository: birthdaysRepo,
      jobsRepository: jobsRepo,
      generate: (request, {required bool forceNano}) async {
        calls++;
        if (calls == 1) return lateResult.future;
        return const AiGenerationResult(
          message: 'retry result',
          providerType: 'user_gemini',
        );
      },
    );
    final worker = JobWorker(
      repository: jobsRepo,
      handlers: {JobTypes.aiGenerate: fencedHandler},
      handlerTimeout: const Duration(milliseconds: 20),
      backoffStep: Duration.zero,
    );
    final completed = Completer<JobRecord>();
    final subscription = worker.events.listen((job) {
      if (job.status == JobStatus.succeeded && !completed.isCompleted) {
        completed.complete(job);
      }
    });
    worker.start();
    final job = await worker.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'b1',
      payload: jsonEncode(
        AiJobPayload(
          birthdayId: 'b1',
          personId: 'p1',
          tone: MessageTone.warm,
          length: MessageLength.standard,
        ).toJson(),
      ),
      maxAttempts: 2,
    );

    final succeeded = await completed.future.timeout(
      const Duration(seconds: 2),
    );
    expect(succeeded.attempts, 2);
    expect(calls, 2);

    lateResult.complete(
      const AiGenerationResult(
        message: 'stale first attempt',
        providerType: 'user_gemini',
      ),
    );
    await Future<void>.delayed(const Duration(milliseconds: 30));

    expect((await jobsRepo.getById(job.id))!.status, JobStatus.succeeded);
    expect((await draftsRepo.getDraftForBirthday('b1'))!.body, 'retry result');
    await subscription.cancel();
    await worker.stop();
  });

  test('producer failures propagate for the worker to classify', () async {
    await seedPerson();
    await seedBirthday();

    final failing = AiGenerationJobHandler(
      peopleRepository: peopleRepo,
      draftsRepository: draftsRepo,
      birthdaysRepository: birthdaysRepo,
      jobsRepository: jobsRepo,
      generate: (request, {required bool forceNano}) async {
        throw const AppFailure.credentialInvalid(action: 'Fix the key.');
      },
    );

    await expectLater(
      failing.run(
        await claimedJob(
          payload: AiJobPayload(
            birthdayId: 'b1',
            personId: 'p1',
            tone: MessageTone.warm,
            length: MessageLength.standard,
          ),
        ),
      ),
      throwsA(
        isA<AppFailure>().having(
          (f) => f.code,
          'code',
          AppFailureCode.aiCredentialInvalid,
        ),
      ),
    );
  });

  test(
    'prompt targetCycleYear comes from the payload, falling back to the birthday',
    () async {
      await seedPerson();
      await seedBirthday();
      // A second birthday whose cycleYear differs from the current calendar
      // year proves the fallback reads the birthday row, not DateTime.now().
      final fallbackYear = now.year - 2;
      await birthdaysRepo.saveBirthday(
        Birthday(
          id: 'b-fallback',
          // birthdays.person_id is UNIQUE since schema v7.
          personId: 'p-fallback',
          cycleYear: fallbackYear,
          date: now,
          status: BirthdayStatus.messageNotPrepared,
          createdAt: now,
          updatedAt: now,
        ),
      );

      AiGenerationRequest? captured;
      final h = AiGenerationJobHandler(
        peopleRepository: peopleRepo,
        draftsRepository: draftsRepo,
        birthdaysRepository: birthdaysRepo,
        jobsRepository: jobsRepo,
        generate: (request, {required bool forceNano}) async {
          captured = request;
          return const AiGenerationResult(
            message: 'drafted',
            providerType: 'user_gemini',
          );
        },
      );

      await h.run(
        await claimedJob(
          payload: AiJobPayload(
            birthdayId: 'b1',
            personId: 'p1',
            tone: MessageTone.warm,
            length: MessageLength.standard,
            targetCycleYear: 2027,
          ),
        ),
      );
      expect(captured!.targetCycleYear, 2027);

      // Jobs queued before the field existed fall back to the birthday row.
      await h.run(
        await claimedJob(
          payload: AiJobPayload(
            birthdayId: 'b-fallback',
            personId: 'p1',
            tone: MessageTone.warm,
            length: MessageLength.standard,
          ),
        ),
      );
      expect(captured!.targetCycleYear, fallbackYear);
    },
  );
}
