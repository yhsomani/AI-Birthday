/// Durable `ai_generate` handler: idempotent apply, stale guard, cancel
/// discard, and producer-failure mapping (background-jobs audit).
library;

import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/database/app_database.dart' show AppDatabase;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
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

  setUp(() async {
    db = AppDatabase(NativeDatabase.memory());
    peopleRepo = DriftPeopleRepository(db);
    birthdaysRepo = DriftBirthdaysRepository(db);
    draftsRepo = DriftDraftsRepository(db);
    jobsRepo = DriftJobsRepository(db);
    generateCalls = 0;
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

  test('applies the generated message idempotently onto the stable draft row',
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
  });

  test('duplicate delivery converges instead of duplicating or clobbering',
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
  });

  test('stale guard: text written after enqueue wins, AI result is dropped',
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

    expect(outcome, isA<JobSucceeded>());
    final draft = (await draftsRepo.getDraftForBirthday('b1'))!;
    expect(draft.body, 'user edits made while drafting'); // never clobbered
    final birthday = (await birthdaysRepo.getBirthday('b1'))!;
    expect(birthday.status, BirthdayStatus.messageNotPrepared); // untouched
  });

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

  test('cancel while running discards the result (worker leaves row canceled)',
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

    expect(outcome, isA<JobSucceeded>()); // discarded, not an error
    expect(generateCalls, 1); // the call itself happened
    expect(await draftsRepo.getDraftForBirthday('b1'), isNull); // no apply
    expect((await birthdaysRepo.getBirthday('b1'))!.status,
        BirthdayStatus.messageNotPrepared);
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
}