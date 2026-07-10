import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { productionInitialState } from '../data/productionState';
import { createTestState } from '../test/testState';
import {
  MAX_PERSISTED_RECOVERY_ISSUES,
  MAX_PERSISTED_RECORDS_PER_AGGREGATE,
  PERSISTED_STATE_SCHEMA_VERSION,
  PersistedStateValidationError,
  assertValidPersistedState,
  createPersistenceRecoveryManifest,
  decodePersistedState
} from './persistenceSchema';

type MutableRecordAggregates = Record<string, unknown> & {
  contacts: Record<string, unknown>[];
  events: Record<string, unknown>[];
  memories: Record<string, unknown>[];
  gifts: Record<string, unknown>[];
  messages: Record<string, unknown>[];
  activity: Record<string, unknown>[];
  backups: Record<string, unknown>[];
  setupChecks: Record<string, unknown>[];
  reminderPlans: Record<string, unknown>[];
};

describe('versioned persisted state runtime schema', () => {
  it('accepts and canonicalizes every current persisted aggregate', () => {
    const state = createTestState();
    const decoded = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);

    assert.equal(decoded.issueCount, 0);
    assert.equal(decoded.excludedRecordCount, 0);
    assert.deepEqual(decoded.defaultedAggregates, []);
    assert.equal(decoded.state.contacts.length, state.contacts.length);
    assert.equal(decoded.state.settings.locale, state.settings.locale);
    assert.equal(decoded.state.persistence.status, 'Ready');
    assert.equal(decoded.state.styleProfile.enabledForAiDrafts, true);
    assert.deepEqual(decoded.state.styleProfile.commonGreetings, ['Hi', 'Hey']);
  });

  it('persists every exact secondary destination and rejects unsupported screen names', () => {
    for (const activeScreen of [
      'analytics',
      'settings',
      'backup',
      'styleCoach',
      'activityHistory',
      'setupCheck'
    ] as const) {
      const state = createTestState();
      state.activeScreen = activeScreen;
      const decoded = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);
      assert.equal(decoded.issueCount, 0);
      assert.equal(decoded.state.activeScreen, activeScreen);
    }

    const malformed = structuredClone(createTestState()) as unknown as MutableRecordAggregates;
    malformed.activeScreen = 'setup';
    const recovered = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(recovered.issueCount, 1);
    assert.equal(recovered.issues[0]?.field, 'activeScreen');
    assert.equal(recovered.state.activeScreen, 'onboarding');
  });

  it('supplies backward-compatible Style Coach fields without exposing history', () => {
    const legacy = structuredClone(createTestState()) as unknown as MutableRecordAggregates;
    const profile = legacy.styleProfile as Record<string, unknown>;
    delete profile.enabledForAiDrafts;
    delete profile.commonGreetings;
    delete profile.representativePreview;
    profile.profileHistory = [{ privateSample: 'must-not-survive' }];

    const decoded = decodePersistedState(legacy, PERSISTED_STATE_SCHEMA_VERSION - 1);

    assert.equal(decoded.issueCount, 0);
    assert.equal(decoded.state.styleProfile.enabledForAiDrafts, true);
    assert.deepEqual(decoded.state.styleProfile.commonGreetings, []);
    assert.equal(decoded.state.styleProfile.representativePreview, '');
    assert.equal('profileHistory' in decoded.state.styleProfile, false);
  });

  it('derives safe activity statuses for legacy records and preserves resolution metadata', () => {
    const legacy = structuredClone(createTestState()) as unknown as MutableRecordAggregates;
    legacy.activity = [
      {
        id: 'legacy-warning',
        type: 'AI',
        title: 'Legacy warning',
        detail: 'Needs attention.',
        severity: 'Warning',
        createdAt: '2026-07-09T08:00:00.000Z'
      },
      {
        id: 'legacy-info',
        type: 'Backup',
        title: 'Legacy completed action',
        detail: 'Completed.',
        severity: 'Info',
        createdAt: '2026-07-09T07:00:00.000Z'
      },
      {
        id: 'resolved-error',
        type: 'Setup',
        title: 'Resolved issue',
        detail: 'Resolved.',
        severity: 'Error',
        status: 'Resolved',
        resolvedAt: '2026-07-09T09:00:00.000Z',
        createdAt: '2026-07-09T06:00:00.000Z'
      }
    ];

    const decoded = decodePersistedState(legacy, PERSISTED_STATE_SCHEMA_VERSION - 1);

    assert.equal(decoded.issueCount, 0);
    assert.equal(decoded.state.activity.find(item => item.id === 'legacy-warning')?.status, 'Open');
    assert.equal(decoded.state.activity.find(item => item.id === 'legacy-info')?.status, 'Completed');
    assert.equal(decoded.state.activity.find(item => item.id === 'resolved-error')?.status, 'Resolved');
    assert.equal(
      decoded.state.activity.find(item => item.id === 'resolved-error')?.resolvedAt,
      '2026-07-09T09:00:00.000Z'
    );
  });

  it('preserves bounded contact routes, source identities, and archive metadata', () => {
    const state = createTestState();
    state.contacts[0] = {
      ...state.contacts[0],
      routes: [
        {
          id: 'route-phone-1',
          type: 'Phone',
          value: '+919876543210',
          primary: true,
          verified: true
        },
        {
          id: 'route-email-1',
          type: 'Email',
          value: 'asha@example.com',
          primary: true,
          verified: false
        }
      ],
      sourceIdentities: [{ provider: 'Device contacts', sourceId: 'device-asha' }],
      archivedAt: '2026-07-10T00:00:00.000Z'
    };
    const decoded = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);
    assert.deepEqual(decoded.state.contacts[0].routes, state.contacts[0].routes);
    assert.deepEqual(decoded.state.contacts[0].sourceIdentities, state.contacts[0].sourceIdentities);
    assert.equal(decoded.state.contacts[0].archivedAt, state.contacts[0].archivedAt);
  });

  it('persists contact detail preferences and supplies safe legacy defaults', () => {
    const state = createTestState();
    state.contacts[0] = {
      ...state.contacts[0],
      relationshipSubtype: 'Older sister',
      jobTitle: 'Finance manager',
      customSendTime: '18:45',
      quietHoursBehavior: 'Block',
      skipAuto: true
    };
    const decoded = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(decoded.state.contacts[0].relationshipSubtype, 'Older sister');
    assert.equal(decoded.state.contacts[0].jobTitle, 'Finance manager');
    assert.equal(decoded.state.contacts[0].customSendTime, '18:45');
    assert.equal(decoded.state.contacts[0].quietHoursBehavior, 'Block');
    assert.equal(decoded.state.contacts[0].skipAuto, true);
    assert.equal(decoded.issueCount, 0);

    const legacy = structuredClone(state) as unknown as MutableRecordAggregates;
    delete legacy.contacts[0].customSendTime;
    delete legacy.contacts[0].quietHoursBehavior;
    delete legacy.contacts[0].skipAuto;
    const migrated = decodePersistedState(legacy, PERSISTED_STATE_SCHEMA_VERSION - 1);
    assert.equal(migrated.state.contacts[0].customSendTime, undefined);
    assert.equal(migrated.state.contacts[0].quietHoursBehavior, 'Defer');
    assert.equal(migrated.state.contacts[0].skipAuto, false);
    assert.equal(migrated.issueCount, 0);
  });

  it('recovers unsafe contact scheduling preferences without weakening safeguards', () => {
    const malformed = structuredClone(createTestState()) as unknown as MutableRecordAggregates;
    malformed.contacts[0].customSendTime = '25:00';
    malformed.contacts[0].quietHoursBehavior = 'Allow';
    malformed.contacts[0].skipAuto = 'yes';

    const decoded = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);

    assert.equal(decoded.state.contacts[0].customSendTime, undefined);
    assert.equal(decoded.state.contacts[0].quietHoursBehavior, 'Defer');
    assert.equal(decoded.state.contacts[0].skipAuto, false);
    assert.equal(decoded.issueCount, 3);
  });

  it('persists schedule time-zone identity and migrates unidentified live schedules to review', () => {
    const state = createTestState();
    state.messages[0] = {
      ...state.messages[0],
      status: 'Scheduled',
      scheduledFor: '2026-08-12T09:00:00.000Z',
      scheduledTimeZone: 'UTC',
      approvedAt: '2026-07-10T09:00:00.000Z',
      approvalExpiresAt: '2026-07-17T09:00:00.000Z'
    };

    const current = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(current.issueCount, 0);
    assert.equal(current.state.messages[0].scheduledTimeZone, 'UTC');

    const legacy = structuredClone(state) as unknown as MutableRecordAggregates;
    delete legacy.messages[0].scheduledTimeZone;
    const migrated = decodePersistedState(legacy, PERSISTED_STATE_SCHEMA_VERSION - 1);
    const migratedMessage = migrated.state.messages[0];
    assert.equal(migrated.issueCount, 0);
    assert.equal(migratedMessage.status, 'Needs review');
    assert.equal(migratedMessage.scheduledFor, undefined);
    assert.equal(migratedMessage.approvedAt, undefined);
    assert.match(migratedMessage.lastError ?? '', /no trusted time-zone identity/i);

    const malformed = structuredClone(state) as unknown as MutableRecordAggregates;
    delete malformed.messages[0].scheduledTimeZone;
    const recovered = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(recovered.issueCount, 1);
    assert.equal(recovered.issues[0]?.field, 'scheduledTimeZone');
    assert.equal(recovered.state.messages[0].status, 'Needs review');
    assert.equal(recovered.state.messages[0].scheduledFor, undefined);
  });

  it('rejects invalid or unpaired persisted schedule time-zone identities', () => {
    const malformed = structuredClone(createTestState()) as unknown as MutableRecordAggregates;
    malformed.messages[0].scheduledTimeZone = 'Not/A-Time-Zone';
    const invalid = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(invalid.issueCount, 1);
    assert.equal(invalid.issues[0]?.field, 'scheduledTimeZone');
    assert.equal(invalid.state.messages[0].scheduledTimeZone, undefined);

    malformed.messages[0].scheduledTimeZone = 'UTC';
    delete malformed.messages[0].scheduledFor;
    const unpaired = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(unpaired.issueCount, 1);
    assert.equal(unpaired.issues[0]?.field, 'scheduledTimeZone');
    assert.equal(unpaired.state.messages[0].scheduledTimeZone, undefined);
  });

  it('preserves bounded imported event source identities for idempotent provider reconciliation', () => {
    const state = createTestState();
    state.events[0] = {
      ...state.events[0],
      sourceIdentities: [
        { provider: 'Device contacts', sourceId: 'device-birthday-asha' },
        { provider: 'Calendar', sourceId: 'calendar-event-asha' }
      ]
    };

    const decoded = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);

    assert.deepEqual(decoded.state.events[0].sourceIdentities, state.events[0].sourceIdentities);
    assert.equal(decoded.issueCount, 0);
  });

  it('preserves occurrence-bound event preparation completion while accepting legacy checklist rows', () => {
    const state = createTestState();
    state.events[0] = {
      ...state.events[0],
      checklist: [
        {
          id: 'write-wish',
          label: 'Legacy write-wish label',
          done: true,
          completedForOccurrence: '2026-08-12'
        },
        { id: 'choose-channel', label: 'Legacy channel label', done: true }
      ]
    };

    const decoded = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);

    assert.deepEqual(decoded.state.events[0].checklist, state.events[0].checklist);
    assert.equal(decoded.issueCount, 0);
  });

  it('rejects an invalid event preparation occurrence key without trusting stale completion', () => {
    const state = createTestState();
    const malformed = structuredClone(state) as unknown as MutableRecordAggregates;
    malformed.events[0].checklist = [
      {
        id: 'write-message',
        label: 'Write message',
        done: true,
        completedForOccurrence: '2026-02-31'
      }
    ];

    const decoded = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);

    assert.equal(
      decoded.state.events.some(event => event.id === state.events[0].id),
      false
    );
    assert.ok(
      decoded.issues.some(
        issue => issue.aggregate === 'events' && issue.field === 'checklist' && issue.code === 'invalid-field'
      )
    );
  });

  it('preserves occurrence-scoped message identity and safely accepts legacy drafts without it', () => {
    const state = createTestState();
    state.messages[0].occurrenceDate = '2026-07-15';

    const current = decodePersistedState(state, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(current.state.messages[0].occurrenceDate, '2026-07-15');

    const legacy = structuredClone(state) as unknown as MutableRecordAggregates;
    delete legacy.messages[0].occurrenceDate;
    const decodedLegacy = decodePersistedState(legacy, 1);
    assert.equal(decodedLegacy.state.messages[0].occurrenceDate, undefined);
    assert.equal(decodedLegacy.state.messages.length, state.messages.length);

    const malformed = structuredClone(state) as unknown as MutableRecordAggregates;
    malformed.messages[0].occurrenceDate = '2026-02-30';
    const recovered = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    assert.equal(recovered.state.messages[0].occurrenceDate, undefined);
    assert.ok(
      recovered.issues.some(
        issue => issue.aggregate === 'messages' && issue.field === 'occurrenceDate' && issue.code === 'invalid-field'
      )
    );
  });

  it('validates every normalized record aggregate and preserves valid siblings', () => {
    const state = createTestState();
    const malformed = structuredClone(state) as unknown as MutableRecordAggregates;
    malformed.contacts.push({ ...malformed.contacts[0], id: 'invalid-contact', name: 42 });
    malformed.events.push({ ...malformed.events[0], id: 'invalid-event', date: 'not-a-date' });
    malformed.memories.push({ ...malformed.memories[0], id: 'invalid-memory', pinned: 'yes' });
    malformed.gifts.push({ ...malformed.gifts[0], id: 'invalid-gift', cost: Number.POSITIVE_INFINITY });
    malformed.messages.push({ ...malformed.messages[0], id: 'invalid-message', variants: { short: 1 } });
    malformed.activity.push({ ...malformed.activity[0], id: 'invalid-activity', severity: 'Fatal' });
    malformed.backups.push({ ...malformed.backups[0], id: 'invalid-backup', recordCount: -1 });
    malformed.setupChecks.push({ ...malformed.setupChecks[0], id: 'invalid-check', status: 'Broken' });
    malformed.reminderPlans.push({ id: 'invalid-reminder', eventId: 2 });

    const decoded = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    const affected = new Set(decoded.issues.map(issue => issue.aggregate));

    for (const aggregate of [
      'contacts',
      'events',
      'memories',
      'gifts',
      'messages',
      'activity',
      'backups',
      'setupChecks',
      'reminderPlans'
    ]) {
      assert.ok(affected.has(aggregate as never), `Expected a bounded issue for ${aggregate}`);
    }
    assert.equal(decoded.state.contacts.length, state.contacts.length);
    assert.equal(decoded.state.events.length, state.events.length);
    assert.equal(decoded.state.messages.length, state.messages.length);
    assert.ok(decoded.excludedRecordCount >= 9);
  });

  it('defaults malformed singleton aggregates without importing unknown private fields', () => {
    const malformed = structuredClone(createTestState()) as unknown as Record<string, unknown>;
    malformed.styleProfile = { confidence: 'Impossible', privateText: 'SECRET_STYLE' };
    malformed.settings = { locale: 'bad-locale', privateText: 'SECRET_SETTINGS' };
    malformed.onboarding = { completed: 'yes', privateText: 'SECRET_ONBOARDING' };
    malformed.privacy = { permissionDecisions: 'all', privateText: 'SECRET_PRIVACY' };
    malformed.aiProvider = { status: 'Leaking', privateText: 'SECRET_AI' };
    malformed.emailDelivery = { status: 200, privateText: 'SECRET_EMAIL' };
    malformed.calendarSync = { exportedCount: -1, importedCount: 0, privateText: 'SECRET_CALENDAR' };
    malformed.persistence = { status: 'Ready', storageHealth: { privateText: 'SECRET_STORAGE' } };

    const decoded = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    const defaulted = new Set(decoded.defaultedAggregates);

    for (const aggregate of ['styleProfile', 'settings', 'onboarding', 'aiProvider', 'emailDelivery', 'calendarSync']) {
      assert.ok(defaulted.has(aggregate as never), `Expected ${aggregate} to use its safe default`);
    }
    assert.deepEqual(decoded.state.settings, productionInitialState.settings);
    assert.equal(decoded.state.aiProvider.status, 'Not configured');
    assert.equal(decoded.state.emailDelivery.status, 'Not configured');
    assert.doesNotMatch(JSON.stringify(decoded.state), /SECRET_/);
  });

  it('enforces duplicate, aggregate-count, and referential-integrity rules', () => {
    const state = createTestState();
    const overLimitContacts = Array.from({ length: MAX_PERSISTED_RECORDS_PER_AGGREGATE + 1 }, (_, index) => ({
      ...state.contacts[0],
      id: `bounded-contact-${index}`
    }));
    const malformed = {
      ...state,
      contacts: [...state.contacts, ...overLimitContacts],
      events: [...state.events, { ...state.events[0], id: 'orphan-event', contactId: 'missing-contact' }],
      messages: [
        ...state.messages,
        { ...state.messages[0] },
        { ...state.messages[0], id: 'stale-event-message', eventId: 'missing-event' }
      ],
      activity: [
        ...state.activity,
        { ...state.activity[0], id: 'stale-activity', contactId: 'missing-contact', messageId: 'missing-message' }
      ]
    };

    const decoded = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);

    assert.equal(decoded.state.contacts.length, MAX_PERSISTED_RECORDS_PER_AGGREGATE);
    assert.ok(decoded.issues.some(issue => issue.aggregate === 'contacts' && issue.code === 'record-limit-exceeded'));
    assert.ok(decoded.issues.some(issue => issue.aggregate === 'messages' && issue.code === 'duplicate-id'));
    assert.equal(
      decoded.state.events.some(event => event.id === 'orphan-event'),
      false
    );
    const recoveredMessage = decoded.state.messages.find(message => message.id === 'stale-event-message');
    assert.ok(recoveredMessage);
    assert.equal(recoveredMessage.eventId, undefined);
    const recoveredActivity = decoded.state.activity.find(item => item.id === 'stale-activity');
    assert.equal(recoveredActivity?.contactId, undefined);
    assert.equal(recoveredActivity?.messageId, undefined);
  });

  it('supports legacy defaults while rejecting unsupported schema versions', () => {
    const legacy = structuredClone(createTestState()) as unknown as Record<string, unknown>;
    delete legacy.aiProvider;
    delete legacy.emailDelivery;
    delete legacy.onboarding;
    legacy.privacy = {
      permissionDecisions: {},
      whatsappAutomationConsent: true
    };
    legacy.settings = {
      accountMode: 'Local',
      locale: 'hi-IN',
      aiEnabled: false,
      notificationsEnabled: false,
      smsEnabled: true,
      automationMode: 'Always ask',
      quietHours: { start: '21:30', end: '07:15' }
    };
    legacy.onboarding = {
      completed: true,
      currentStepId: 'finish',
      completedStepIds: ['intro', 'finish'],
      skippedStepIds: []
    };

    const decoded = decodePersistedState(legacy, 1);
    assert.equal(decoded.state.aiProvider.status, 'Not configured');
    assert.equal(decoded.state.emailDelivery.status, 'Not configured');
    assert.equal(decoded.state.settings.locale, 'hi-IN');
    assert.equal(decoded.state.settings.aiEnabled, false);
    assert.equal(decoded.state.settings.notificationsEnabled, false);
    assert.equal(decoded.state.settings.quietHours.start, '21:30');
    assert.equal(decoded.state.onboarding.completed, true);
    assert.equal(decoded.state.onboarding.currentStepId, 'finish');
    assert.equal(decoded.state.onboarding.selectedGoal, productionInitialState.onboarding.selectedGoal);
    assert.equal(decoded.state.privacy.whatsappHandoffConsent, true);
    assert.throws(() => decodePersistedState(legacy, PERSISTED_STATE_SCHEMA_VERSION + 1), /version.*supported/i);
  });

  it('strict mode rejects recoverable defects and issue manifests stay bounded and redacted', () => {
    const malformed = structuredClone(createTestState()) as unknown as Record<string, unknown>;
    malformed.contacts = Array.from({ length: MAX_PERSISTED_RECOVERY_ISSUES + 50 }, (_, index) => ({
      id: `private-${index}`,
      name: `PRIVATE NAME ${index}`
    }));
    const decoded = decodePersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION);
    const manifest = createPersistenceRecoveryManifest(
      decoded,
      PERSISTED_STATE_SCHEMA_VERSION,
      '2026-07-10T00:00:00.000Z'
    );

    assert.throws(
      () => assertValidPersistedState(malformed, PERSISTED_STATE_SCHEMA_VERSION),
      PersistedStateValidationError
    );
    assert.equal(manifest.redacted, true);
    assert.equal(manifest.outcome, 'selective');
    assert.equal(manifest.issues.length, MAX_PERSISTED_RECOVERY_ISSUES);
    assert.equal(manifest.issuesTruncated, true);
    assert.doesNotMatch(JSON.stringify(manifest), /PRIVATE NAME|private-/);
    assert.equal('raw' in manifest, false);
  });
});
