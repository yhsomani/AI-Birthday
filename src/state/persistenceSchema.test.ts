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
  });

  it('validates every normalized record aggregate and preserves valid siblings', () => {
    const state = createTestState();
    const malformed = structuredClone(state) as unknown as Record<string, any>;
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
      'contacts', 'events', 'memories', 'gifts', 'messages', 'activity', 'backups', 'setupChecks', 'reminderPlans'
    ]) {
      assert.ok(affected.has(aggregate as never), `Expected a bounded issue for ${aggregate}`);
    }
    assert.equal(decoded.state.contacts.length, state.contacts.length);
    assert.equal(decoded.state.events.length, state.events.length);
    assert.equal(decoded.state.messages.length, state.messages.length);
    assert.ok(decoded.excludedRecordCount >= 9);
  });

  it('defaults malformed singleton aggregates without importing unknown private fields', () => {
    const malformed = structuredClone(createTestState()) as unknown as Record<string, any>;
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
    const overLimitContacts = Array.from(
      { length: MAX_PERSISTED_RECORDS_PER_AGGREGATE + 1 },
      (_, index) => ({ ...state.contacts[0], id: `bounded-contact-${index}` })
    );
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
    assert.equal(decoded.state.events.some(event => event.id === 'orphan-event'), false);
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
    const malformed = structuredClone(createTestState()) as unknown as Record<string, any>;
    malformed.contacts = Array.from(
      { length: MAX_PERSISTED_RECOVERY_ISSUES + 50 },
      (_, index) => ({ id: `private-${index}`, name: `PRIVATE NAME ${index}` })
    );
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
