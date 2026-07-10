import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  createEncryptedBackup,
  countBackupRecords,
  previewEncryptedBackup,
  totalBackupRecords
} from '../data/encryptedBackup';
import type { AppState, MessageDraft, SystemPermissionCapability } from '../domain/types';
import { evaluateProviderEndpointReadiness } from '../domain/providerEndpointReadiness';
import { createProductionInitialState } from '../data/productionState';
import { createTestState } from '../test/testState';
import { relateReducer, type RelateAction } from '../state/relateReducer';
import { HarnessCommandRuntime } from './commandRuntime';
import { parseHarnessCommand } from './commandRuntimeParser';
import type { CommandRuntimeDependencies } from './commandRuntimeTypes';
import { OperationCoordinator } from './operationCoordinator';
import {
  createPermissionAuthorizationRecords,
  type PermissionAuthorizationRecords
} from './permissionReminderCoordinator';

const now = new Date('2026-07-10T10:00:00.000Z');

const grantedPermissionRecords = (state: AppState): PermissionAuthorizationRecords => {
  const records = createPermissionAuthorizationRecords(state.privacy);
  (Object.keys(records) as SystemPermissionCapability[]).forEach(capability => {
    records[capability] = {
      ...records[capability],
      systemAuthorization: 'granted',
      lastKnownAuthorization: 'granted',
      systemCheckedAt: now.toISOString(),
      canAskAgain: true
    };
  });
  return records;
};

const fixture = (initialState: AppState = createTestState()) => {
  let state = structuredClone(initialState);
  let operationTick = 0;
  let requestId = 0;
  const actions: RelateAction[] = [];
  const installedStates: AppState[] = [];
  const preflightCalls: SystemPermissionCapability[] = [];
  const permissionRecords = grantedPermissionRecords(state);
  const operations = new OperationCoordinator({
    now: () => `2026-07-10T10:00:${String(operationTick++).padStart(2, '0')}.000Z`,
    createRequestId: () => `command-request-${++requestId}`
  });

  const dependencies: CommandRuntimeDependencies = {
    getState: () => state,
    dispatch: action => {
      actions.push(action);
      state = relateReducer(state, action);
    },
    installVerifiedState: next => {
      installedStates.push(next);
      state = next;
    },
    operations,
    createConfirmationToken: () => `restore-confirmation-${requestId + 1}`,
    now: () => new Date(now),
    importContacts: async () => [],
    importCalendar: async () => [],
    exportCalendar: async () => 0,
    pickEventImportFile: async () => undefined,
    reconcileReminders: async current => ({
      status: 'reconciled',
      reason: 'permission-change',
      records: permissionRecords,
      plannedReminders: current.reminderPlans,
      desiredNativeReminders: current.reminderPlans,
      nativeResult: {
        scheduled: current.reminderPlans.length,
        skipped: 0,
        cancelled: 0,
        unchanged: 0
      }
    }),
    requestAiDraft: async () => ({
      ok: true,
      variants: {
        short: 'A warm short message for this occasion.',
        standard: 'A thoughtful standard message for this important occasion.',
        warm: 'A very warm and thoughtful message for this important occasion.'
      }
    }),
    sendEmail: async () => ({ ok: true, status: 'sent', deliveryId: 'delivery-safe-1' }),
    reconcileEmail: async attempt => ({
      ok: true,
      status: 'sent',
      deliveryId: attempt.deliveryId ?? 'delivery-reconciled-safe-1'
    }),
    openHandoff: async () => ({
      outcome: 'opened-destination',
      usedFallback: false,
      needsSentConfirmation: true
    }),
    exportBackup: async current => ({
      uri: '/private/path/that-must-not-be-returned',
      fileName: 'RelateAI-backup-2026-07-10.relateai',
      byteCount: 4096,
      shared: true,
      preview: {
        format: 'relateai.encrypted-backup',
        version: 2,
        app: 'RelateAI',
        createdAt: now.toISOString(),
        encrypted: true,
        persistenceVersion: 3,
        recordCounts: countBackupRecords(current),
        recordCount: totalBackupRecords(countBackupRecords(current)),
        warnings: []
      },
      disposition: 'temporary-shared',
      temporaryFileRemoved: true,
      verifiedPortableCopy: false
    }),
    selectBackup: async () => undefined,
    decryptBackup: async () => structuredClone(state),
    restoreData: async restoredState => ({ status: 'restored', state: restoredState }),
    clearData: async previousState => {
      const cleared = createProductionInitialState();
      cleared.settings.locale = previousState.settings.locale;
      cleared.persistence.status = 'Ready';
      return cleared;
    },
    refreshPermissions: async () => permissionRecords,
    preflightPermission: async (_current, capability) => {
      preflightCalls.push(capability);
      const record = permissionRecords[capability];
      return {
        capability,
        allowed: true,
        authorization: record.systemAuthorization,
        checkedAt: record.systemCheckedAt,
        record,
        records: permissionRecords
      };
    },
    requestPermission: async (current, request) => ({
      status: request.userIntent === 'decline' ? 'declined' : 'granted',
      capability: request.capability,
      records: permissionRecords,
      decisions: current.privacy.permissionDecisions
    }),
    authenticateBiometric: async () => true,
    shareAnalyticsSummary: async () => 'shared',
    shareAnalyticsCsv: async () => ({ opened: true, temporaryFileRemoved: true }),
    setupEnvironment: () => ({ aiEndpointConfigured: false, emailEndpointConfigured: false })
  };
  const runtime = new HarnessCommandRuntime(dependencies);
  return {
    runtime,
    dependencies,
    operations,
    actions,
    installedStates,
    preflightCalls,
    getState: () => state,
    setState: (next: AppState) => {
      state = next;
    },
    permissionRecords
  };
};

const approvedMessage = (source: MessageDraft, overrides: Partial<MessageDraft>): MessageDraft => ({
  ...source,
  status: 'Scheduled',
  scheduledFor: undefined,
  approvedAt: '2026-07-10T09:00:00.000Z',
  approvalExpiresAt: '2026-07-17T09:00:00.000Z',
  ...overrides
});

describe('bounded harness command parser', () => {
  it('accepts strict typed JSON commands and rejects extra fields or unsafe clear confirmations', () => {
    const parsed = parseHarnessCommand(
      JSON.stringify({
        type: 'domain.dispatch',
        action: { type: 'navigate', screen: 'contacts', contactId: 'c-asha' }
      })
    );
    assert.equal(parsed.ok, true);

    assert.equal(parseHarnessCommand({ type: 'analytics.inspect', range: 'All time', secret: 'no' }).ok, false);
    assert.equal(parseHarnessCommand({ type: 'analytics.share-summary', range: 'This year' }).ok, true);
    assert.equal(parseHarnessCommand({ type: 'analytics.export-preview' }).ok, true);
    assert.equal(
      parseHarnessCommand({ type: 'analytics.export-confirm', confirmationToken: 'analytics-token-1234' }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({ type: 'analytics.open-action', insightId: 'pending-messages', range: 'All time' }).ok,
      true
    );
    assert.equal(parseHarnessCommand({ type: 'analytics.open-action', insightId: 'bad id' }).ok, false);
    assert.equal(parseHarnessCommand({ type: 'analytics.export-confirm', confirmationToken: 'bad token' }).ok, false);
    assert.equal(parseHarnessCommand({ type: 'data.clear', confirmation: 'yes' }).ok, false);
    assert.equal(
      parseHarnessCommand({
        type: 'events.import-text',
        raw: 'x'.repeat(2 * 1024 * 1024 + 1)
      }).ok,
      false
    );
    assert.equal(parseHarnessCommand({ type: 'events.import-file' }).ok, true);
    assert.equal(parseHarnessCommand({ type: 'events.import-file', path: '/private/file.csv' }).ok, false);
    assert.equal(parseHarnessCommand({ type: 'calendar.export' }).ok, true);
    assert.equal(parseHarnessCommand({ type: 'calendar.export', eventIds: ['event-1', 'event-2'] }).ok, true);
    assert.equal(parseHarnessCommand({ type: 'calendar.export', eventIds: [] }).ok, false);
    assert.equal(parseHarnessCommand({ type: 'calendar.export', eventIds: ['event-1', 'event-1'] }).ok, false);
    assert.equal(parseHarnessCommand({ type: 'calendar.export', eventIds: ['bad event'] }).ok, false);
    assert.equal(parseHarnessCommand({ type: 'calendar.export', eventIds: ['event-1'], full: true }).ok, false);
    assert.equal(
      parseHarnessCommand({
        type: 'calendar.export',
        eventIds: Array.from({ length: 501 }, (_, index) => `event-${index}`)
      }).ok,
      false
    );
    assert.deepEqual(parseHarnessCommand({ type: 'settings.set-locale', locale: 'hi-IN' }), {
      ok: true,
      command: { type: 'settings.set-locale', locale: 'hi-IN' }
    });
    assert.equal(parseHarnessCommand({ type: 'settings.set-locale', locale: 'fr-FR' }).ok, false);
    assert.deepEqual(parseHarnessCommand({ type: 'settings.set-email-sender', senderEmail: ' Sender@Example.COM ' }), {
      ok: true,
      command: { type: 'settings.set-email-sender', senderEmail: 'sender@example.com' }
    });
    assert.equal(parseHarnessCommand({ type: 'settings.set-email-sender', senderEmail: '' }).ok, true);
    assert.equal(parseHarnessCommand({ type: 'settings.set-email-sender', senderEmail: 'not-an-email' }).ok, false);
    assert.equal(
      parseHarnessCommand({ type: 'settings.set-email-sender', senderEmail: 'sender@example.com', reveal: true }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({ type: 'settings.set-email-sender', senderEmail: `${'a'.repeat(250)}@example.com` }).ok,
      false
    );
    const cyclic: Record<string, unknown> = { type: 'setup.inspect' };
    cyclic.self = cyclic;
    assert.equal(parseHarnessCommand(cyclic).ok, false);
  });

  it('accepts only bounded unique bulk-message previews and token-only applies', () => {
    assert.equal(
      parseHarnessCommand({
        type: 'messages.bulk-preview',
        action: 'Approve',
        messageIds: ['message-one', 'message-two']
      }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({
        type: 'messages.bulk-apply',
        confirmationToken: 'bulk-confirmation-token-1'
      }).ok,
      true
    );
    assert.equal(parseHarnessCommand({ type: 'messages.bulk-preview', action: 'Approve', messageIds: [] }).ok, false);
    assert.equal(
      parseHarnessCommand({
        type: 'messages.bulk-preview',
        action: 'Approve',
        messageIds: ['same-message', 'same-message']
      }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'messages.bulk-preview',
        action: 'Send',
        messageIds: ['message-one']
      }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'messages.bulk-preview',
        action: 'Reject',
        messageIds: Array.from({ length: 101 }, (_, index) => `message-${index}`)
      }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'messages.bulk-apply',
        confirmationToken: 'bulk-confirmation-token-1',
        messageIds: ['message-one']
      }).ok,
      false
    );
  });

  it('accepts every exact secondary destination through the typed navigation action', () => {
    for (const screen of ['analytics', 'settings', 'backup', 'styleCoach', 'activityHistory', 'setupCheck'] as const) {
      assert.equal(parseHarnessCommand({ type: 'domain.dispatch', action: { type: 'navigate', screen } }).ok, true);
    }
    assert.equal(
      parseHarnessCommand({ type: 'domain.dispatch', action: { type: 'navigate', screen: 'setup' } }).ok,
      false
    );
  });

  it('accepts only exact bounded lifecycle, paging, review, permission, and cancellation shapes', () => {
    assert.equal(parseHarnessCommand({ type: 'contacts.query', limit: 10 }).ok, true);
    assert.equal(parseHarnessCommand({ type: 'home.inspect' }).ok, true);
    assert.equal(
      parseHarnessCommand({
        type: 'contacts.add',
        input: {
          name: 'Local Person',
          relationship: 'Friend',
          group: 'Friends',
          preferredChannel: 'Manual',
          language: 'English',
          notesSummary: ''
        }
      }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({
        type: 'contacts.import-apply',
        sessionToken: 'contact-session-1234',
        decisions: [{ reviewItemId: 'review-1', action: 'merge' }]
      }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'calendar.import-apply',
        sessionToken: 'calendar-session-1',
        decisions: [{ reviewId: 'event-review-1', action: 'apply', title: 'unsupported' }]
      }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'permissions.request',
        capability: 'Biometric lock',
        userIntent: 'allow'
      }).ok,
      false
    );
    assert.equal(parseHarnessCommand({ type: 'operation.cancel', scope: 'email:unsafe' }).ok, true);
    assert.equal(
      parseHarnessCommand({ type: 'messages.edit', messageId: 'message-1', body: 'x'.repeat(10_001) }).ok,
      false
    );
    assert.equal(parseHarnessCommand({ type: 'contacts.query', limit: 101 }).ok, false);
    assert.equal(
      parseHarnessCommand({
        type: 'domain.dispatch',
        action: { type: 'toggleSetting', key: 'biometricLockEnabled' }
      }).ok,
      false
    );
    assert.equal(parseHarnessCommand({ type: 'biometric.enable' }).ok, true);
    assert.equal(parseHarnessCommand({ type: 'biometric.disable' }).ok, true);
    assert.equal(
      parseHarnessCommand({
        type: 'biometric.disable',
        recoveryConfirmation: 'DISABLE BIOMETRIC LOCK'
      }).ok,
      false
    );
    assert.equal(parseHarnessCommand({ type: 'biometric.disable', recoveryConfirmation: 'disable' }).ok, false);
    assert.equal(
      parseHarnessCommand({ type: 'domain.dispatch', action: { type: 'setDefaultSendTime', time: '09:30' } }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({ type: 'domain.dispatch', action: { type: 'setDefaultSendTime', time: '25:00' } }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'domain.dispatch',
        action: {
          type: 'addBlackout',
          label: 'Festival pause',
          startDate: '2026-10-20',
          endDate: '2026-10-22',
          behavior: 'Defer',
          channels: ['SMS', 'WhatsApp']
        }
      }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({
        type: 'domain.dispatch',
        action: {
          type: 'addBlackout',
          label: 'Festival pause',
          startDate: '2026-10-20',
          endDate: '2026-10-22',
          channels: ['SMS', 'SMS']
        }
      }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'domain.dispatch',
        action: { type: 'removeBlackout', blackoutId: 'blackout-1' }
      }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({
        type: 'backup.export-confirm',
        backupConfirmationToken: 'backup-confirmation-1234'
      }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({ type: 'backup.export-confirm', backupConfirmationToken: 'bad token' }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'contacts.import-apply',
        sessionToken: 'contact-session-1234',
        decisions: [
          {
            reviewItemId: 'birthday-review-1',
            action: 'replace',
            conflictingEventId: 'event-1',
            candidateContactId: 'contact-1'
          }
        ]
      }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({
        type: 'calendar.import-apply',
        sessionToken: 'calendar-session-1234',
        decisions: [{ reviewId: 'event-review-1', action: 'merge-contact', candidateContactId: 'contact-1' }]
      }).ok,
      true
    );
  });

  it('accepts the strict functionality-console catalog and rejects postponed or oversized variants', () => {
    const accepted = [
      { type: 'system.catalog' },
      { type: 'contacts.query', query: 'asha', group: 'Family', lowHealth: true, sort: 'Health', limit: 20 },
      { type: 'events.query', month: '2026-07', eventType: 'Birthday', sort: 'Contact' },
      { type: 'messages.query', tab: 'Review', channel: 'Manual', query: 'hello', sort: 'Status' },
      { type: 'contacts.inspect', contactId: 'c-asha' },
      { type: 'contacts.preferences.set-tone', contactId: 'c-asha', tone: 'Warm', enabled: true },
      {
        type: 'groups.set-default',
        group: 'Friends',
        defaults: { tone: ['Warm', 'Playful'], automationMode: 'Always ask' }
      },
      {
        type: 'contacts.enrichment.answer',
        contactId: 'c-asha',
        promptId: 'message-avoid',
        body: 'Avoid generic wording.'
      },
      { type: 'events.preparation.toggle', eventId: 'event-1', stepId: 'improve-context' },
      {
        type: 'messages.regenerate',
        messageId: 'message-1',
        instructions: ['Make it shorter'],
        excludedMemoryIds: ['memory-1'],
        includePriorMessages: false
      },
      { type: 'messages.set-channel', messageId: 'message-1', channel: 'Manual' },
      { type: 'templates.inspect', contactId: 'c-asha', reason: 'Birthday', tone: 'Warm' },
      { type: 'memories.query', contactId: 'c-asha', query: 'coffee', limit: 10 },
      { type: 'timeline.query', contactId: 'c-asha', filter: 'Memories' },
      { type: 'chat.query', contactId: 'c-asha', channel: 'All', query: '' },
      { type: 'gifts.set-budget', contactId: 'c-asha', annualGiftBudget: 5000 },
      { type: 'onboarding.set-goal', goal: 'Reminders first' },
      { type: 'account.disconnect', confirmation: 'DISCONNECT ACCOUNT' },
      { type: 'privacy.set-whatsapp-consent', enabled: true },
      { type: 'settings.set-automation', mode: 'VIP approve' },
      { type: 'settings.set-locale', locale: 'en-Hinglish' },
      { type: 'settings.set-email-sender', senderEmail: 'sender@example.com' },
      { type: 'setup.wizard.inspect', goal: 'AI drafts' },
      { type: 'setup.wizard.run-action', goal: 'AI drafts', stepId: 'ai-provider' },
      { type: 'style.set-enabled', enabled: false },
      { type: 'style.train-samples', samples: 'First meaningful sample.\n\nSecond meaningful sample.' },
      {
        type: 'activity.query',
        query: 'provider',
        activityType: 'Message',
        severity: 'Warning',
        status: 'Open',
        date: 'Last 7 days',
        limit: 50
      },
      { type: 'activity.open-action', activityId: 'activity-1' },
      { type: 'activity.resolve', activityId: 'activity-1' },
      { type: 'analytics.share-summary', range: 'All time' },
      { type: 'analytics.export-preview', range: 'This year' },
      { type: 'analytics.export-confirm', confirmationToken: 'analytics-confirmation-1234' },
      { type: 'analytics.open-action', insightId: 'pending-messages', range: 'Last 30 days' },
      { type: 'setup.open-action', checkId: 'reminders' },
      { type: 'home.open-action', actionId: 'create-backup' }
    ];
    accepted.forEach(command => assert.equal(parseHarnessCommand(command).ok, true, JSON.stringify(command)));

    const rejected = [
      { type: 'system.catalog', private: true },
      { type: 'contacts.query', sort: 'Name', unexpected: true },
      { type: 'events.query', month: '2026-13' },
      { type: 'messages.query', tab: 'Bulk approve' },
      { type: 'contacts.preferences.set-automation', contactId: 'c-asha', mode: 'Fully auto' },
      { type: 'groups.set-default', group: 'Friends', defaults: {} },
      { type: 'groups.set-default', group: 'Friends', defaults: { tone: ['Warm', 'Warm'] } },
      { type: 'messages.regenerate', messageId: 'message-1', instructions: [] },
      {
        type: 'messages.regenerate',
        messageId: 'message-1',
        instructions: Array.from({ length: 9 }, () => 'shorter')
      },
      { type: 'memories.delete', memoryId: 'memory-1', confirmation: 'yes' },
      { type: 'gifts.delete', giftId: 'gift-1', confirmation: 'DELETE ALL' },
      { type: 'settings.set-automation', mode: 'Fully auto' },
      { type: 'settings.set-locale', locale: 'en-US' },
      { type: 'settings.set-email-sender', senderEmail: 'invalid' },
      { type: 'setup.wizard.inspect' },
      { type: 'setup.wizard.inspect', goal: 'Everything' },
      { type: 'setup.wizard.run-action', goal: 'AI drafts', stepId: 'bad id' },
      { type: 'setup.wizard.run-action', goal: 'Everything', stepId: 'ai-provider' },
      { type: 'style.set-enabled', enabled: 'false' },
      { type: 'style.set-enabled', enabled: false, unexpected: true },
      { type: 'account.use-google' },
      { type: 'contacts.export-csv' },
      { type: 'messages.bulk-approve', messageIds: ['message-1'] },
      { type: 'whatsapp.send-unattended', messageId: 'message-1' },
      { type: 'activity.query', limit: 101 },
      { type: 'activity.query', date: 'Last month' },
      { type: 'activity.query', query: 'x'.repeat(241) },
      { type: 'activity.query', status: 'Pending' },
      { type: 'activity.open-action', activityId: 'bad id' },
      { type: 'activity.resolve', activityId: 'bad id' },
      { type: 'activity.resolve', activityId: 'activity-1', force: true },
      { type: 'analytics.share-summary', range: 'Last month' },
      { type: 'analytics.export-preview', range: 'All time', confirmed: true },
      { type: 'analytics.open-action', insightId: 'pending-messages', range: 'All time', contactId: 'c-asha' },
      { type: 'setup.open-action', checkId: 'bad id' }
    ];
    rejected.forEach(command => assert.equal(parseHarnessCommand(command).ok, false, JSON.stringify(command)));
  });
});

describe('application command runtime', () => {
  it('dispatches only parser-validated domain actions and exposes operation state', async () => {
    const test = fixture();
    let publications = 0;
    const unsubscribe = test.runtime.subscribeOperations(() => {
      publications += 1;
    });
    const result = await test.runtime.execute({
      type: 'domain.dispatch',
      action: { type: 'setSearch', query: 'private search phrase' }
    });
    unsubscribe();

    assert.equal(result.status, 'succeeded');
    assert.equal(test.getState().searchQuery, 'private search phrase');
    assert.equal(result.status === 'succeeded' && result.value.kind, 'domain-action');
    assert.doesNotMatch(JSON.stringify(result), /private search phrase/);
    assert.equal(test.runtime.operationSnapshot('domain:setSearch')?.status, 'succeeded');
    assert.ok(publications >= 2);

    const invalid = await test.runtime.execute({
      type: 'domain.dispatch',
      action: { type: 'hydrate', state: createTestState() }
    });
    assert.equal(invalid.status, 'invalid');
  });

  it('returns bounded private review pages only after the command lock boundary permits execution', async () => {
    const state = createTestState();
    state.contacts = state.contacts.map(contact =>
      contact.id === 'c-asha' ? { ...contact, archivedAt: '2026-07-01T00:00:00.000Z' } : contact
    );
    state.messages = state.messages.map(message =>
      message.id === 'msg-mira-checkin'
        ? { ...message, lastError: 'Exact route recovery is required before approval.' }
        : message
    );
    const test = fixture(state);

    const blocked = await test.runtime.execute({
      type: 'domain.dispatch',
      action: { type: 'editMessage', messageId: 'missing-message', body: 'Private body must not appear.' }
    });
    const first = await test.runtime.execute({ type: 'contacts.query', limit: 1 });
    const firstPage = first.status === 'succeeded' && first.value.kind === 'contacts-page' ? first.value : undefined;
    const second = await test.runtime.execute({
      type: 'contacts.query',
      limit: 1,
      cursor: firstPage?.nextCursor
    });
    const archived = await test.runtime.execute({ type: 'contacts.query', includeArchived: true });
    const events = await test.runtime.execute({ type: 'events.query' });
    const messages = await test.runtime.execute({ type: 'messages.query' });
    const checkIns = await test.runtime.execute({ type: 'checkins.query', status: 'Due' });
    const home = await test.runtime.execute({ type: 'home.inspect' });
    const badCursor = await test.runtime.execute({ type: 'contacts.query', cursor: 'missing-cursor' });
    const archivedPage =
      archived.status === 'succeeded' && archived.value.kind === 'contacts-page' ? archived.value : undefined;
    const eventPage = events.status === 'succeeded' && events.value.kind === 'events-page' ? events.value : undefined;
    const messagePage =
      messages.status === 'succeeded' && messages.value.kind === 'messages-page' ? messages.value : undefined;
    const archivedAsha = archivedPage?.items.find(item => item.id === 'c-asha');
    const miraMessage = messagePage?.items.find(item => item.id === 'msg-mira-checkin');
    const eventReview = eventPage?.items[0];
    const homeInspection =
      home.status === 'succeeded' && home.value.kind === 'home-inspection' ? home.value : undefined;

    assert.equal(
      blocked.status === 'succeeded' && blocked.value.kind === 'domain-action' && blocked.value.outcome,
      'blocked'
    );
    assert.equal(firstPage?.returnedCount, 1);
    assert.equal(firstPage?.totalCount, 2);
    assert.equal(second.status, 'succeeded');
    assert.equal(archivedPage?.totalCount, 3);
    assert.equal(archivedAsha?.name, 'Asha Mehra');
    assert.ok(archivedAsha?.routes.some(route => route.type === 'Phone' && route.value.startsWith('+91')));
    assert.equal(
      eventPage?.items.some(item => item.contactId === 'c-asha'),
      false
    );
    assert.ok(eventReview?.label);
    assert.ok(eventReview?.date);
    assert.ok(eventReview?.eventType);
    assert.ok(eventReview?.source);
    assert.ok(eventReview?.checklist.every(item => item.id && item.label && typeof item.done === 'boolean'));
    assert.equal(
      messagePage?.items.some(item => item.contactId === 'c-asha'),
      false
    );
    assert.equal(miraMessage?.body, state.messages.find(message => message.id === 'msg-mira-checkin')?.body);
    assert.deepEqual(
      miraMessage?.variants,
      state.messages.find(message => message.id === 'msg-mira-checkin')?.variants
    );
    assert.equal(miraMessage?.readiness, 'Use manual handoff');
    assert.equal(miraMessage?.error, 'Exact route recovery is required before approval.');
    assert.equal(miraMessage?.channel, 'Manual');
    assert.equal(miraMessage?.status, 'Draft');
    assert.equal(messagePage?.counts.All, 1);
    assert.equal(messagePage?.counts.Review, 1);
    assert.equal(messagePage?.emptyState, undefined);
    assert.equal(checkIns.status, 'succeeded');
    assert.equal(home.status, 'succeeded');
    assert.equal(homeInspection?.metrics.activeContacts, 2);
    assert.equal(
      homeInspection?.upcoming.some(item => item.contactId === 'c-asha'),
      false
    );
    assert.ok(homeInspection?.actions.every(action => action.title && action.detail && action.targetScreen));
    assert.ok(homeInspection?.summary);
    assert.ok(['never', 'fresh', 'stale'].includes(homeInspection?.backup.status ?? ''));
    assert.equal(badCursor.status, 'failed');
    assert.doesNotMatch(JSON.stringify(home), /Asha Mehra|Mira Kapoor|Rajesh Nair/);
    assert.doesNotMatch(JSON.stringify(test.runtime.operationSnapshots()), /Asha Mehra|Mira Kapoor|Use manual handoff/);
    assert.doesNotMatch(JSON.stringify([first, second, archived, events, messages]), /Private body must not appear/);
  });

  it('exposes the exhaustive non-private command catalog before entity-specific workflows', async () => {
    const test = fixture();
    const result = await test.runtime.execute({ type: 'system.catalog' });

    assert.equal(result.status, 'succeeded');
    assert.equal(result.status === 'succeeded' && result.value.kind, 'command-catalog');
    assert.ok(
      result.status === 'succeeded' &&
        result.value.kind === 'command-catalog' &&
        result.value.supportedTypes.includes('messages.preview')
    );
    assert.doesNotMatch(JSON.stringify(result), /Asha Mehra|\+919|rajesh@example|mango lassi/);
  });

  it('imports contacts and calendar events while exposing bounded staged event decisions', async () => {
    const test = fixture();
    test.dependencies.importContacts = async () => [
      {
        sourceId: 'device-new-person',
        name: 'Sensitive Person Name',
        phone: '+919999999999',
        birthday: '2026-12-05T09:00:00.000Z'
      }
    ];
    test.dependencies.importCalendar = async () => [
      {
        sourceId: 'calendar-new-person',
        title: 'Sensitive Person Anniversary',
        startDate: '2026-12-20T09:00:00.000Z'
      }
    ];
    test.dependencies.exportCalendar = async () => 2;

    const contactPreview = await test.runtime.execute({ type: 'contacts.import' });
    assert.equal(contactPreview.status, 'succeeded');
    const contactSessionToken =
      contactPreview.status === 'succeeded' && contactPreview.value.kind === 'contact-import-preview'
        ? contactPreview.value.sessionToken
        : '';
    const contacts = await test.runtime.execute({
      type: 'contacts.import-apply',
      sessionToken: contactSessionToken,
      decisions: []
    });
    const calendarPreview = await test.runtime.execute({ type: 'calendar.import' });
    assert.equal(calendarPreview.status, 'succeeded');
    const calendarSession =
      calendarPreview.status === 'succeeded' && calendarPreview.value.kind === 'calendar-import-preview'
        ? calendarPreview.value
        : undefined;
    const calendar = await test.runtime.execute({
      type: 'calendar.import-apply',
      sessionToken: calendarSession?.sessionToken ?? '',
      decisions: (calendarSession?.reviewItems ?? []).map(item => ({
        reviewId: item.reviewId,
        action: 'apply' as const
      }))
    });
    const exported = await test.runtime.execute({ type: 'calendar.export' });

    assert.equal(contacts.status, 'succeeded');
    assert.equal(calendar.status, 'succeeded');
    assert.equal(exported.status, 'succeeded');
    assert.ok(test.getState().contacts.some(contact => contact.name === 'Sensitive Person Name'));
    assert.ok(test.getState().events.some(event => event.label === 'Sensitive Person Anniversary'));
    assert.deepEqual(test.preflightCalls, ['Contacts', 'Calendar', 'Calendar']);
    assert.equal(calendarSession?.reviewItems[0]?.title, 'Sensitive Person Anniversary');
    assert.equal(calendarSession?.reviewItems[0]?.date, '2026-12-20T09:00:00.000Z');
    assert.deepEqual(calendarSession?.reviewItems[0]?.validationErrors, []);
    assert.doesNotMatch(JSON.stringify(calendarSession), /private event notes/i);
    assert.doesNotMatch(JSON.stringify(test.runtime.operationSnapshots()), /Sensitive Person|999999/);
  });

  it('exports a strict event selection without changing full-reconciliation command semantics', async () => {
    const state = createTestState();
    const selectedEventId = state.events[0].id;
    const test = fixture(state);
    let adapterRequest: Parameters<CommandRuntimeDependencies['exportCalendar']>[1] | undefined;
    test.dependencies.exportCalendar = async (_current, request) => {
      adapterRequest = request;
      return request.mode === 'selected' ? 1 : 0;
    };

    const selected = await test.runtime.execute({ type: 'calendar.export', eventIds: [selectedEventId] });
    const selectedValue = selected.status === 'succeeded' ? selected.value : undefined;

    assert.equal(selected.status, 'succeeded');
    assert.deepEqual(adapterRequest, { mode: 'selected', eventIds: [selectedEventId] });
    assert.equal(selectedValue?.kind, 'calendar-export');
    assert.equal(selectedValue?.kind === 'calendar-export' ? selectedValue.mode : undefined, 'selected');
    assert.equal(selectedValue?.kind === 'calendar-export' ? selectedValue.selectedCount : undefined, 1);
    assert.equal(
      selectedValue?.kind === 'calendar-export' ? selectedValue.eligibleCount : undefined,
      state.events.length
    );

    const callsBeforeInvalidSelection = test.preflightCalls.length;
    const missing = await test.runtime.execute({ type: 'calendar.export', eventIds: ['missing-event'] });
    assert.equal(missing.status, 'failed');
    assert.equal(missing.status === 'failed' ? missing.error.code : undefined, 'calendar-export-selection-invalid');
    assert.equal(test.preflightCalls.length, callsBeforeInvalidSelection);

    const full = await test.runtime.execute({ type: 'calendar.export' });
    const fullValue = full.status === 'succeeded' ? full.value : undefined;
    assert.equal(full.status, 'succeeded');
    assert.deepEqual(adapterRequest, { mode: 'full' });
    assert.equal(fullValue?.kind === 'calendar-export' ? fullValue.mode : undefined, 'full');
    assert.equal(fullValue?.kind === 'calendar-export' ? fullValue.selectedCount : undefined, 0);
  });

  it('stops an ambiguous same-name contact batch for explicit identity review', async () => {
    const test = fixture();
    const beforeContacts = test.getState().contacts.length;
    test.dependencies.importContacts = async () => [
      {
        sourceId: 'different-asha-runtime',
        name: 'Asha Mehra',
        email: 'different-person@example.com'
      }
    ];

    const result = await test.runtime.execute({ type: 'contacts.import' });

    assert.equal(result.status, 'succeeded');
    const preview =
      result.status === 'succeeded' && result.value.kind === 'contact-import-preview' ? result.value : undefined;
    assert.equal(preview?.unresolved, 1);
    assert.equal(test.getState().contacts.length, beforeContacts);
    assert.equal(
      test.actions.some(action => action.type === 'importContacts'),
      false
    );
    const applied = await test.runtime.execute({
      type: 'contacts.import-apply',
      sessionToken: preview?.sessionToken ?? '',
      decisions: preview?.reviewItems.map(item => ({ reviewItemId: item.reviewItemId, action: 'skip' as const })) ?? []
    });
    assert.equal(applied.status, 'succeeded');
    assert.equal(test.getState().contacts.length, beforeContacts);
    assert.equal(preview?.reviewItems[0]?.candidateName, 'Asha Mehra');
    assert.deepEqual(preview?.reviewItems[0]?.candidateRoutes, [
      {
        type: 'Email',
        value: 'different-person@example.com',
        label: undefined,
        primary: true,
        verified: false
      }
    ]);
    assert.doesNotMatch(JSON.stringify(test.runtime.operationSnapshots()), /Asha Mehra|different-person@example/i);
  });

  it('exposes and applies explicit contact birthday-conflict decisions with bounded event details', async () => {
    const state = createTestState();
    state.events = state.events.map(event =>
      event.id === 'e-asha-bday' ? { ...event, date: '1990-07-09T12:00:00.000Z', recurrence: undefined } : event
    );
    const test = fixture(state);
    test.dependencies.importContacts = async () => [
      {
        sourceId: 'device-asha-command-birthday',
        name: 'Asha Mehra',
        phone: '+91 98765 43210',
        birthday: '1991-08-04T09:30:00.000Z'
      }
    ];

    const previewResult = await test.runtime.execute({ type: 'contacts.import-preview' });
    const preview =
      previewResult.status === 'succeeded' && previewResult.value.kind === 'contact-import-preview'
        ? previewResult.value
        : undefined;
    const item = preview?.reviewItems[0];
    assert.equal(item?.reason, 'conflicting-birthday');
    assert.equal(item?.candidateName, 'Asha Mehra');
    assert.equal(item?.candidateBirthday, '1991-08-04T09:30:00.000Z');
    assert.equal(item?.importedBirthday, '1991-08-04T12:00:00.000Z');
    assert.deepEqual(item?.conflictingEvents, [
      {
        eventId: 'e-asha-bday',
        label: state.events.find(event => event.id === 'e-asha-bday')?.label,
        date: '1990-07-09T12:00:00.000Z',
        eventType: 'Birthday'
      }
    ]);

    const applied = await test.runtime.execute({
      type: 'contacts.import-apply',
      sessionToken: preview?.sessionToken ?? '',
      decisions: [
        {
          reviewItemId: item?.reviewItemId ?? '',
          action: 'replace',
          conflictingEventId: 'e-asha-bday',
          candidateContactId: 'c-asha'
        }
      ]
    });
    assert.equal(applied.status, 'succeeded');
    assert.equal(
      applied.status === 'succeeded' && applied.value.kind === 'contact-import-apply' ? applied.value.unresolved : -1,
      0
    );
    assert.equal(test.getState().events.find(event => event.id === 'e-asha-bday')?.date, '1991-08-04T12:00:00.000Z');
  });

  it('stages invalid calendar candidates for explicit edit decisions and clears import sessions on background', async () => {
    const test = fixture();
    test.dependencies.importCalendar = async () => [
      {
        sourceId: 'calendar-invalid-private',
        title: 'Private invalid event title',
        startDate: 'not-a-date',
        notes: 'Private event notes'
      }
    ];
    const preview = await test.runtime.execute({ type: 'calendar.import-preview' });
    const staged =
      preview.status === 'succeeded' && preview.value.kind === 'calendar-import-preview' ? preview.value : undefined;
    const applied = await test.runtime.execute({
      type: 'calendar.import-apply',
      sessionToken: staged?.sessionToken ?? '',
      decisions: (staged?.reviewItems ?? []).map(item => ({
        reviewId: item.reviewId,
        action: 'edit' as const,
        title: 'Private corrected event title',
        date: '2027-12-05',
        notes: 'Private corrected notes'
      }))
    });

    const contactPreview = await test.runtime.execute({ type: 'contacts.import-preview' });
    const contactSession =
      contactPreview.status === 'succeeded' && contactPreview.value.kind === 'contact-import-preview'
        ? contactPreview.value.sessionToken
        : '';
    test.runtime.onBackground();
    const cleared = await test.runtime.execute({
      type: 'contacts.import-apply',
      sessionToken: contactSession,
      decisions: []
    });

    assert.equal(staged?.invalid, 1);
    assert.equal(applied.status, 'succeeded');
    assert.equal(
      applied.status === 'succeeded' && applied.value.kind === 'calendar-import-apply' && applied.value.applied,
      1
    );
    assert.ok(test.getState().events.some(event => event.label === 'Private corrected event title'));
    assert.equal(cleared.status, 'failed');
    assert.equal(staged?.reviewItems[0]?.title, 'Private invalid event title');
    assert.equal(staged?.reviewItems[0]?.date, 'not-a-date');
    assert.deepEqual(staged?.reviewItems[0]?.validationErrors, ['Enter a valid event date.']);
    assert.doesNotMatch(JSON.stringify([preview, applied]), /Private corrected|event notes/i);
    assert.doesNotMatch(
      JSON.stringify(test.runtime.operationSnapshots()),
      /Private invalid|Private corrected|event notes/i
    );
  });

  it('exposes state-aware calendar conflicts and applies a bounded merge-contact decision', async () => {
    const test = fixture();
    test.dependencies.importCalendar = async () => [
      {
        sourceId: 'calendar-asha-graduation-command',
        title: 'Asha Mehra Graduation',
        startDate: '2027-05-20T12:00:00.000Z'
      }
    ];

    const previewResult = await test.runtime.execute({ type: 'calendar.import-preview' });
    const preview =
      previewResult.status === 'succeeded' && previewResult.value.kind === 'calendar-import-preview'
        ? previewResult.value
        : undefined;
    const item = preview?.reviewItems[0];
    assert.equal(preview?.conflictCount, 1);
    assert.equal(item?.conflictReason, 'same-name');
    assert.deepEqual(item?.allowedConflictActions, ['skip', 'create-separate', 'merge-contact']);
    assert.equal(item?.candidateContacts[0]?.contactId, 'c-asha');
    assert.equal(item?.candidateContacts[0]?.name, 'Asha Mehra');
    assert.ok(item?.candidateContacts[0]?.routes.some(route => route.type === 'Phone'));

    const applied = await test.runtime.execute({
      type: 'calendar.import-apply',
      sessionToken: preview?.sessionToken ?? '',
      decisions: [
        {
          reviewId: item?.reviewId ?? '',
          action: 'merge-contact',
          candidateContactId: 'c-asha'
        }
      ]
    });
    assert.equal(applied.status, 'succeeded');
    assert.equal(
      applied.status === 'succeeded' && applied.value.kind === 'calendar-import-apply' ? applied.value.unresolved : -1,
      0
    );
    assert.ok(
      test.getState().events.some(event => event.contactId === 'c-asha' && event.label === 'Asha Mehra Graduation')
    );
  });

  it('rejects oversized multi-route contact adapter output before applying it', async () => {
    const test = fixture();
    const beforeContacts = test.getState().contacts.length;
    test.dependencies.importContacts = async () => [
      {
        sourceId: 'oversized-routes',
        name: 'Adapter output',
        phones: Array.from({ length: 21 }, (_, index) => `+91999999${String(index).padStart(4, '0')}`),
        emails: ['valid@example.com']
      }
    ];

    const result = await test.runtime.execute({ type: 'contacts.import' });

    assert.equal(result.status, 'failed');
    assert.equal(result.status === 'failed' && result.error.code, 'contacts-invalid-result');
    assert.equal(test.getState().contacts.length, beforeContacts);
  });

  it('persists refreshed denial after a native contact prompt fails and keeps manual entry reachable', async () => {
    const initial = createTestState();
    initial.privacy.permissionDecisions.Contacts = 'Granted';
    initial.privacy.permissionRecords = grantedPermissionRecords(initial);
    const test = fixture(initial);
    const denied = structuredClone(test.permissionRecords);
    denied.Contacts = {
      ...denied.Contacts,
      systemAuthorization: 'denied',
      lastKnownAuthorization: 'denied',
      canAskAgain: false,
      systemCheckedAt: '2026-07-10T10:01:00.000Z'
    };
    test.dependencies.importContacts = async () => {
      throw new Error('Contacts permission was not granted.');
    };
    test.dependencies.refreshPermissions = async () => denied;

    const result = await test.runtime.execute({ type: 'contacts.import' });

    assert.equal(result.status, 'failed');
    assert.equal(result.status === 'failed' && result.error.code, 'contacts-permission-revoked');
    assert.match(result.status === 'failed' ? result.error.summary : '', /contacts\.add/);
    assert.equal(test.getState().privacy.permissionRecords?.Contacts?.systemAuthorization, 'denied');
    assert.equal(test.getState().privacy.permissionDecisions.Contacts, 'Denied');
  });

  it('distinguishes a dismissed Contacts prompt from denial and preserves manual recovery', async () => {
    const initial = createTestState();
    initial.privacy.permissionDecisions.Contacts = 'Not requested';
    initial.privacy.permissionRecords = grantedPermissionRecords(initial);
    initial.privacy.permissionRecords.Contacts = {
      ...initial.privacy.permissionRecords.Contacts,
      capability: 'Contacts',
      userIntent: 'not-expressed',
      systemAuthorization: 'undetermined',
      lastKnownAuthorization: 'undetermined',
      canAskAgain: true
    };
    const test = fixture(initial);
    const undetermined = structuredClone(test.permissionRecords);
    undetermined.Contacts = {
      ...undetermined.Contacts,
      systemAuthorization: 'undetermined',
      lastKnownAuthorization: 'undetermined',
      canAskAgain: true,
      systemCheckedAt: '2026-07-10T10:01:30.000Z'
    };
    test.dependencies.preflightPermission = async () => ({
      capability: 'Contacts',
      allowed: true,
      authorization: 'undetermined',
      checkedAt: undetermined.Contacts.systemCheckedAt,
      record: undetermined.Contacts,
      records: undetermined
    });
    test.dependencies.importContacts = async () => {
      throw new Error('Contacts permission was not granted.');
    };
    test.dependencies.refreshPermissions = async () => undetermined;

    const result = await test.runtime.execute({ type: 'contacts.import' });

    assert.equal(result.status, 'failed');
    assert.equal(result.status === 'failed' && result.error.code, 'contacts-permission-cancelled');
    assert.match(result.status === 'failed' ? result.error.summary : '', /contacts\.add/);
    assert.equal(test.getState().privacy.permissionDecisions.Contacts, 'Not requested');
  });

  it('persists refreshed Calendar revocation when native import or export throws', async () => {
    const run = async (operation: 'import' | 'export') => {
      const initial = createTestState();
      initial.privacy.permissionDecisions.Calendar = 'Granted';
      initial.privacy.permissionRecords = grantedPermissionRecords(initial);
      const test = fixture(initial);
      const denied = structuredClone(test.permissionRecords);
      denied.Calendar = {
        ...denied.Calendar,
        systemAuthorization: 'restricted',
        lastKnownAuthorization: 'restricted',
        canAskAgain: false,
        systemCheckedAt: '2026-07-10T10:02:00.000Z'
      };
      test.dependencies.refreshPermissions = async () => denied;
      if (operation === 'import') {
        test.dependencies.importCalendar = async () => {
          throw new Error('Calendar native read failed.');
        };
      } else {
        test.dependencies.exportCalendar = async () => {
          throw new Error('Calendar native write failed.');
        };
      }

      const result = await test.runtime.execute({
        type: operation === 'import' ? 'calendar.import' : 'calendar.export'
      });
      assert.equal(result.status, 'failed');
      assert.equal(result.status === 'failed' && result.error.code, `calendar-${operation}-permission-revoked`);
      assert.match(
        result.status === 'failed' ? result.error.summary : '',
        operation === 'import' ? /events\.add|events\.import-text/ : /Events remain local/
      );
      assert.equal(test.getState().privacy.permissionRecords?.Calendar?.systemAuthorization, 'restricted');
      assert.equal(test.getState().privacy.permissionDecisions.Calendar, 'Denied');
    };

    await run('import');
    await run('export');
  });

  it('returns nameless routable native contacts as bounded explicit review items', async () => {
    const test = fixture();
    test.dependencies.importContacts = async () => [
      {
        sourceId: 'native-nameless-routable',
        name: '',
        phone: '+91 90000 12345'
      }
    ];

    const result = await test.runtime.execute({ type: 'contacts.import-preview' });
    const preview =
      result.status === 'succeeded' && result.value.kind === 'contact-import-preview' ? result.value : undefined;

    assert.equal(preview?.unresolved, 1);
    assert.equal(preview?.reviewItems[0]?.reason, 'missing-name');
    assert.equal(preview?.reviewItems[0]?.candidateName, '');
    assert.match(preview?.reviewItems[0]?.validationErrors[0] ?? '', /contacts\.add/);
  });

  it('executes strict contact and event lifecycle preview/apply pairs with bounded opaque impacts', async () => {
    const state = createTestState();
    state.events.unshift({ ...state.events[0], id: 'e-asha-duplicate-runtime' });
    state.contacts.unshift({
      ...state.contacts[0],
      id: 'c-asha-duplicate-runtime',
      sourceIdentities: [{ provider: 'Device contacts', sourceId: 'duplicate-runtime-source' }]
    });
    const test = fixture(state);
    const contactInput = {
      name: 'Rajesh Nair',
      relationship: 'Mentor',
      phone: '',
      email: 'rajesh@example.com',
      language: 'English' as const,
      notesSummary: 'A private edited summary that must not be returned.'
    };

    const contactPreview = await test.runtime.execute({
      type: 'contacts.edit-preview',
      contactId: 'c-rajesh',
      input: contactInput
    });
    const contactToken =
      contactPreview.status === 'succeeded' && contactPreview.value.kind === 'contact-lifecycle-preview'
        ? contactPreview.value.confirmationToken
        : '';
    const contactApplied = await test.runtime.execute({
      type: 'contacts.edit-apply',
      contactId: 'c-rajesh',
      input: contactInput,
      confirmationToken: contactToken
    });
    const archivePreview = await test.runtime.execute({ type: 'contacts.archive-preview', contactId: 'c-mira' });
    const archiveToken =
      archivePreview.status === 'succeeded' && archivePreview.value.kind === 'contact-lifecycle-preview'
        ? archivePreview.value.confirmationToken
        : '';
    const archived = await test.runtime.execute({
      type: 'contacts.archive-apply',
      contactId: 'c-mira',
      confirmationToken: archiveToken
    });
    const restored = await test.runtime.execute({ type: 'contacts.restore', contactId: 'c-mira' });

    const added = await test.runtime.execute({
      type: 'contacts.add',
      input: {
        name: 'Temporary Local Person',
        relationship: 'Friend',
        group: 'Friends',
        preferredChannel: 'Manual',
        language: 'English',
        notesSummary: ''
      }
    });
    const addedContactId =
      added.status === 'succeeded' && added.value.kind === 'contact-action' ? added.value.createdContactId : undefined;
    const deletePreview = await test.runtime.execute({
      type: 'contacts.delete-preview',
      contactId: addedContactId ?? 'missing-contact'
    });
    const deleteToken =
      deletePreview.status === 'succeeded' && deletePreview.value.kind === 'contact-lifecycle-preview'
        ? deletePreview.value.confirmationToken
        : '';
    const deleted = await test.runtime.execute({
      type: 'contacts.delete-apply',
      contactId: addedContactId ?? 'missing-contact',
      confirmationToken: deleteToken
    });

    const eventInput = {
      contactId: 'c-rajesh',
      eventType: 'Work anniversary' as const,
      label: 'Private revised work milestone',
      date: '2027-10-20',
      verified: true
    };
    const eventPreview = await test.runtime.execute({
      type: 'events.edit-preview',
      eventId: 'e-rajesh-work',
      input: eventInput
    });
    const eventToken =
      eventPreview.status === 'succeeded' && eventPreview.value.kind === 'event-lifecycle-preview'
        ? eventPreview.value.confirmationToken
        : '';
    const eventApplied = await test.runtime.execute({
      type: 'events.edit-apply',
      eventId: 'e-rajesh-work',
      input: eventInput,
      confirmationToken: eventToken
    });
    const eventAdded = await test.runtime.execute({
      type: 'events.add',
      contactId: 'c-rajesh',
      eventType: 'Custom',
      label: 'Private temporary event',
      date: '2027-11-21'
    });
    const addedEventId =
      eventAdded.status === 'succeeded' && eventAdded.value.kind === 'event-action'
        ? eventAdded.value.createdEventId
        : undefined;
    const eventDeletePreview = await test.runtime.execute({
      type: 'events.delete-preview',
      eventId: addedEventId ?? 'missing-event'
    });
    const eventDeleteToken =
      eventDeletePreview.status === 'succeeded' && eventDeletePreview.value.kind === 'event-lifecycle-preview'
        ? eventDeletePreview.value.confirmationToken
        : '';
    const eventDeleted = await test.runtime.execute({
      type: 'events.delete-apply',
      eventId: addedEventId ?? 'missing-event',
      confirmationToken: eventDeleteToken
    });
    const mergePreview = await test.runtime.execute({
      type: 'events.merge-preview',
      survivorEventId: 'e-asha-bday',
      mergedEventId: 'e-asha-duplicate-runtime'
    });
    const mergeToken =
      mergePreview.status === 'succeeded' && mergePreview.value.kind === 'event-merge-preview'
        ? mergePreview.value.confirmationToken
        : '';
    const merged = await test.runtime.execute({
      type: 'events.merge-apply',
      survivorEventId: 'e-asha-bday',
      mergedEventId: 'e-asha-duplicate-runtime',
      confirmationToken: mergeToken
    });
    const contactMergePreview = await test.runtime.execute({
      type: 'contacts.merge-preview',
      survivorContactId: 'c-asha',
      mergedContactId: 'c-asha-duplicate-runtime'
    });
    const contactMergeToken =
      contactMergePreview.status === 'succeeded' && contactMergePreview.value.kind === 'contact-lifecycle-preview'
        ? contactMergePreview.value.confirmationToken
        : '';
    const contactsMerged = await test.runtime.execute({
      type: 'contacts.merge-apply',
      survivorContactId: 'c-asha',
      mergedContactId: 'c-asha-duplicate-runtime',
      confirmationToken: contactMergeToken
    });

    assert.equal(
      contactApplied.status === 'succeeded' &&
        contactApplied.value.kind === 'contact-action' &&
        contactApplied.value.outcome,
      'applied'
    );
    assert.equal(test.getState().contacts.find(contact => contact.id === 'c-rajesh')?.relationship, 'Mentor');
    assert.equal(
      archived.status === 'succeeded' && archived.value.kind === 'contact-action' && archived.value.outcome,
      'applied'
    );
    assert.equal(
      restored.status === 'succeeded' && restored.value.kind === 'contact-action' && restored.value.outcome,
      'applied'
    );
    assert.ok(addedContactId);
    assert.equal(
      deleted.status === 'succeeded' && deleted.value.kind === 'contact-action' && deleted.value.outcome,
      'applied'
    );
    assert.equal(
      eventApplied.status === 'succeeded' && eventApplied.value.kind === 'event-action' && eventApplied.value.outcome,
      'applied'
    );
    assert.equal(
      test
        .getState()
        .events.find(event => event.id === 'e-rajesh-work')
        ?.date.slice(0, 10),
      '2027-10-20'
    );
    assert.ok(addedEventId);
    assert.equal(
      eventDeleted.status === 'succeeded' && eventDeleted.value.kind === 'event-action' && eventDeleted.value.outcome,
      'applied'
    );
    assert.equal(
      merged.status === 'succeeded' && merged.value.kind === 'event-action' && merged.value.outcome,
      'applied'
    );
    assert.equal(
      test.getState().events.some(event => event.id === 'e-asha-duplicate-runtime'),
      false
    );
    assert.equal(
      contactsMerged.status === 'succeeded' &&
        contactsMerged.value.kind === 'contact-action' &&
        contactsMerged.value.outcome,
      'applied'
    );
    assert.doesNotMatch(
      JSON.stringify([contactPreview, contactApplied, eventPreview, eventApplied]),
      /private edited summary|private revised work milestone/i
    );
  });

  it('surfaces exact contact identity collisions and blocks edit apply without dispatching duplicate routes', async () => {
    const state = createTestState();
    const asha = state.contacts.find(contact => contact.id === 'c-asha');
    const mira = state.contacts.find(contact => contact.id === 'c-mira');
    assert.ok(asha?.phone);
    assert.ok(mira);
    const input = {
      name: mira.name,
      relationship: mira.relationship,
      phone: asha.phone,
      email: mira.email,
      language: mira.language,
      notesSummary: mira.notesSummary
    };
    const test = fixture(state);
    const previewResult = await test.runtime.execute({
      type: 'contacts.edit-preview',
      contactId: mira.id,
      input
    });
    const preview =
      previewResult.status === 'succeeded' && previewResult.value.kind === 'contact-lifecycle-preview'
        ? previewResult.value
        : undefined;
    const applied = await test.runtime.execute({
      type: 'contacts.edit-apply',
      contactId: mira.id,
      input,
      confirmationToken: preview?.confirmationToken ?? ''
    });

    assert.deepEqual(preview?.exactIdentityCandidateIds, [asha.id]);
    assert.equal(
      applied.status === 'succeeded' && applied.value.kind === 'contact-action' && applied.value.outcome,
      'blocked'
    );
    assert.equal(
      applied.status === 'succeeded' && applied.value.kind === 'contact-action'
        ? applied.value.blockedReason
        : undefined,
      'exact-identity-collision'
    );
    assert.deepEqual(
      applied.status === 'succeeded' && applied.value.kind === 'contact-action'
        ? applied.value.exactIdentityCandidateIds
        : undefined,
      [asha.id]
    );
    assert.equal(test.getState().contacts.find(contact => contact.id === mira.id)?.phone, mira.phone);
    assert.equal(
      test.actions.some(action => action.type === 'editContact'),
      false
    );
  });

  it('blocks archived contacts from composer inspection and check-in mutations before dispatch', async () => {
    const state = createTestState();
    state.contacts = state.contacts.map(contact =>
      contact.id === 'c-mira' ? { ...contact, archivedAt: '2026-07-10T09:00:00.000Z' } : contact
    );
    const test = fixture(state);
    const inspected = await test.runtime.execute({
      type: 'composer.inspect',
      contactId: 'c-mira',
      reason: 'Check-in'
    });
    const snoozed = await test.runtime.execute({ type: 'checkins.snooze', contactId: 'c-mira', days: 7 });
    const contacted = await test.runtime.execute({ type: 'checkins.mark-contacted', contactId: 'c-mira' });

    assert.equal(inspected.status, 'failed');
    assert.equal(inspected.status === 'failed' && inspected.error.code, 'composer-contact-archived');
    assert.equal(
      snoozed.status === 'succeeded' && snoozed.value.kind === 'checkin-action' && snoozed.value.outcome,
      'blocked'
    );
    assert.equal(
      contacted.status === 'succeeded' && contacted.value.kind === 'checkin-action' && contacted.value.outcome,
      'blocked'
    );
    assert.equal(
      test.actions.some(action => action.type === 'snoozeCheckIn' || action.type === 'markContactedElsewhere'),
      false
    );
  });

  it('rejects private review records that exceed output bounds instead of truncating decision content', async () => {
    const state = createTestState();
    state.messages = state.messages.map((message, index) =>
      index === 0
        ? {
            ...message,
            body: 'x'.repeat(10_001),
            variants: { ...message.variants, standard: 'x'.repeat(10_001) }
          }
        : message
    );
    const test = fixture(state);
    const result = await test.runtime.execute({ type: 'messages.query' });

    assert.equal(result.status, 'failed');
    assert.equal(result.status === 'failed' && result.error.code, 'query-state-invalid');
    assert.doesNotMatch(JSON.stringify(result), /x{100}/);
  });

  it('reconciles reminders and commits the returned live permission records and plans', async () => {
    const test = fixture();
    const plan = {
      id: 'reminder-runtime',
      eventId: test.getState().events[0].id,
      contactId: test.getState().contacts[0].id,
      title: 'RelateAI reminder',
      body: 'Open RelateAI to review.',
      triggerAt: '2026-07-20T09:00:00.000Z'
    };
    test.dependencies.reconcileReminders = async () => ({
      status: 'reconciled',
      reason: 'foreground',
      records: test.permissionRecords,
      plannedReminders: [plan],
      desiredNativeReminders: [plan],
      nativeResult: { scheduled: 1, skipped: 0, cancelled: 2, unchanged: 0 }
    });

    const result = await test.runtime.execute({
      type: 'reminders.reconcile',
      reason: 'foreground'
    });

    assert.equal(result.status, 'succeeded');
    assert.equal(test.getState().reminderPlans[0]?.id, 'reminder-runtime');
    assert.equal(test.getState().privacy.permissionRecords?.Notifications?.systemAuthorization, 'granted');
    assert.deepEqual(
      result.status === 'succeeded' && result.value.kind === 'reminder-reconciliation'
        ? { scheduled: result.value.scheduled, cancelled: result.value.cancelled }
        : undefined,
      { scheduled: 1, cancelled: 2 }
    );

    test.dependencies.reconcileReminders = async () => ({
      status: 'reconciliation-failed',
      reason: 'permission-change',
      records: test.permissionRecords,
      plannedReminders: [plan],
      desiredNativeReminders: [plan]
    });
    const failed = await test.runtime.execute({
      type: 'reminders.reconcile',
      reason: 'permission-change'
    });
    assert.equal(failed.status, 'failed');
    assert.equal(failed.status === 'failed' && failed.error.code, 'reminder-reconciliation-failed');
  });

  it('creates a validated AI draft without returning prompt context or generated bodies', async () => {
    const test = fixture();
    const before = test.getState().messages.length;
    const result = await test.runtime.execute({
      type: 'ai.draft',
      contactId: 'c-asha',
      reason: 'Birthday',
      excludedMemoryIds: ['m-asha-1'],
      includePriorMessages: false
    });

    assert.equal(result.status, 'succeeded');
    assert.equal(test.getState().messages.length, before + 1);
    assert.equal(test.getState().messages[0].status, 'Needs review');
    assert.doesNotMatch(JSON.stringify(result), /Asha|warm short message|mango lassi|Private note excluded/i);
  });

  it('previews and confirms partial bulk message actions without exposing message bodies', async () => {
    const state = createTestState();
    state.messages = state.messages.map(message =>
      message.id === 'msg-asha-bday'
        ? { ...message, status: 'Needs review' as const, body: 'Hi' }
        : { ...message, status: 'Needs review' as const }
    );
    const test = fixture(state);
    const preview = await test.runtime.execute({
      type: 'messages.bulk-preview',
      action: 'Approve',
      messageIds: ['msg-asha-bday', 'msg-mira-checkin', 'missing-message']
    });

    assert.equal(preview.status, 'succeeded');
    if (preview.status !== 'succeeded' || preview.value.kind !== 'bulk-message-preview') return;
    assert.equal(preview.value.selectedCount, 3);
    assert.equal(preview.value.eligibleCount, 1);
    assert.equal(preview.value.skippedCount, 2);
    assert.deepEqual(preview.value.eligibleMessageIds, ['msg-mira-checkin']);
    assert.match(preview.value.skipped.find(item => item.messageId === 'msg-asha-bday')?.reason ?? '', /too short/i);
    assert.match(
      preview.value.skipped.find(item => item.messageId === 'missing-message')?.reason ?? '',
      /no longer available/i
    );
    assert.match(preview.value.confirmationFingerprint, /^bulk-message-v1-/);
    assert.equal(preview.value.requiresConfirmation, true);
    assert.equal(test.actions.length, 0);
    assert.doesNotMatch(JSON.stringify(preview.value), /Pune treating|mango lassi|family chaos/i);

    const applied = await test.runtime.execute({
      type: 'messages.bulk-apply',
      confirmationToken: preview.value.confirmationToken
    });
    assert.equal(applied.status, 'succeeded');
    if (applied.status !== 'succeeded' || applied.value.kind !== 'bulk-message-apply') return;
    assert.equal(applied.value.appliedCount, 1);
    assert.equal(applied.value.skippedCount, 2);
    assert.deepEqual(applied.value.appliedMessageIds, ['msg-mira-checkin']);
    assert.equal(test.getState().messages.find(message => message.id === 'msg-mira-checkin')?.status, 'Scheduled');
    assert.equal(test.getState().messages.find(message => message.id === 'msg-asha-bday')?.status, 'Needs review');
    assert.doesNotMatch(JSON.stringify(applied.value), /Pune treating|mango lassi|family chaos/i);

    const repeated = await test.runtime.execute({
      type: 'messages.bulk-apply',
      confirmationToken: preview.value.confirmationToken
    });
    assert.equal(repeated.status, 'failed');
    assert.equal(repeated.status === 'failed' && repeated.error.code, 'bulk-preview-stale');
  });

  it('applies reject, retry, and revoke bulk lifecycle transitions through the same confirmation boundary', async () => {
    const cases = [
      { action: 'Reject' as const, initialStatus: 'Draft' as const, expectedStatus: 'Rejected' as const },
      { action: 'Retry' as const, initialStatus: 'Failed' as const, expectedStatus: 'Needs review' as const },
      {
        action: 'Revoke approval' as const,
        initialStatus: 'Scheduled' as const,
        expectedStatus: 'Needs review' as const
      }
    ];

    for (const scenario of cases) {
      const state = createTestState();
      state.messages = state.messages.map(message =>
        message.id === 'msg-mira-checkin'
          ? {
              ...message,
              status: scenario.initialStatus,
              scheduledFor: undefined,
              approvedAt: scenario.initialStatus === 'Scheduled' ? '2026-07-10T09:00:00.000Z' : undefined,
              approvalExpiresAt: scenario.initialStatus === 'Scheduled' ? '2026-07-17T09:00:00.000Z' : undefined
            }
          : message
      );
      const test = fixture(state);
      const preview = await test.runtime.execute({
        type: 'messages.bulk-preview',
        action: scenario.action,
        messageIds: ['msg-mira-checkin']
      });
      assert.equal(preview.status, 'succeeded');
      if (preview.status !== 'succeeded' || preview.value.kind !== 'bulk-message-preview') continue;
      const applied = await test.runtime.execute({
        type: 'messages.bulk-apply',
        confirmationToken: preview.value.confirmationToken
      });
      assert.equal(applied.status, 'succeeded');
      assert.equal(
        test.getState().messages.find(message => message.id === 'msg-mira-checkin')?.status,
        scenario.expectedStatus
      );
    }
  });

  it('revalidates bulk confirmations and clears them after state mutation or background', async () => {
    const test = fixture();
    const preview = await test.runtime.execute({
      type: 'messages.bulk-preview',
      action: 'Reject',
      messageIds: ['msg-asha-bday']
    });
    assert.equal(preview.status, 'succeeded');
    if (preview.status !== 'succeeded' || preview.value.kind !== 'bulk-message-preview') return;

    const changed = structuredClone(test.getState());
    changed.messages = changed.messages.map(message =>
      message.id === 'msg-asha-bday' ? { ...message, status: 'Sent' as const } : message
    );
    test.setState(changed);
    const stale = await test.runtime.execute({
      type: 'messages.bulk-apply',
      confirmationToken: preview.value.confirmationToken
    });
    assert.equal(stale.status, 'failed');
    assert.equal(stale.status === 'failed' && stale.error.code, 'bulk-preview-stale');
    assert.equal(test.actions.length, 0);

    const backgroundTest = fixture();
    const backgroundPreview = await backgroundTest.runtime.execute({
      type: 'messages.bulk-preview',
      action: 'Reject',
      messageIds: ['msg-asha-bday']
    });
    assert.equal(backgroundPreview.status, 'succeeded');
    if (backgroundPreview.status !== 'succeeded' || backgroundPreview.value.kind !== 'bulk-message-preview') return;
    backgroundTest.runtime.onBackground();
    const afterBackground = await backgroundTest.runtime.execute({
      type: 'messages.bulk-apply',
      confirmationToken: backgroundPreview.value.confirmationToken
    });
    assert.equal(afterBackground.status, 'failed');
    assert.equal(afterBackground.status === 'failed' && afterBackground.error.code, 'bulk-preview-stale');

    const expiryTest = fixture();
    const expiryPreview = await expiryTest.runtime.execute({
      type: 'messages.bulk-preview',
      action: 'Reject',
      messageIds: ['msg-asha-bday']
    });
    assert.equal(expiryPreview.status, 'succeeded');
    if (expiryPreview.status !== 'succeeded' || expiryPreview.value.kind !== 'bulk-message-preview') return;
    expiryTest.dependencies.now = () => new Date('2026-07-10T10:06:00.000Z');
    const expired = await expiryTest.runtime.execute({
      type: 'messages.bulk-apply',
      confirmationToken: expiryPreview.value.confirmationToken
    });
    assert.equal(expired.status, 'failed');
    assert.equal(expired.status === 'failed' && expired.error.code, 'bulk-preview-stale');

    const mutationTest = fixture();
    const mutationPreview = await mutationTest.runtime.execute({
      type: 'messages.bulk-preview',
      action: 'Reject',
      messageIds: ['msg-asha-bday']
    });
    assert.equal(mutationPreview.status, 'succeeded');
    if (mutationPreview.status !== 'succeeded' || mutationPreview.value.kind !== 'bulk-message-preview') return;
    await mutationTest.runtime.execute({
      type: 'messages.edit',
      messageId: 'msg-asha-bday',
      body: 'A changed but still valid message body for stale confirmation testing.'
    });
    const afterMutation = await mutationTest.runtime.execute({
      type: 'messages.bulk-apply',
      confirmationToken: mutationPreview.value.confirmationToken
    });
    assert.equal(afterMutation.status, 'failed');
    assert.equal(afterMutation.status === 'failed' && afterMutation.error.code, 'bulk-preview-stale');
  });

  it('surfaces pre-bulk channel verification guidance and keeps bulk tools behind the lock boundary', async () => {
    const state = createTestState();
    state.messages = state.messages.map(message => ({
      ...message,
      status: 'Needs review' as const,
      channel: 'SMS' as const
    }));
    const test = fixture(state);
    const preview = await test.runtime.execute({
      type: 'messages.bulk-preview',
      action: 'Approve',
      messageIds: ['msg-asha-bday', 'msg-mira-checkin']
    });
    assert.equal(preview.status, 'succeeded');
    if (preview.status !== 'succeeded' || preview.value.kind !== 'bulk-message-preview') return;
    assert.match(preview.value.verificationGuidance ?? '', /one low-risk message/i);
    assert.match(preview.value.verificationGuidance ?? '', /SMS/i);

    const lockedState = structuredClone(state);
    lockedState.settings.biometricLockEnabled = true;
    const locked = await fixture(lockedState).runtime.execute({
      type: 'messages.bulk-preview',
      action: 'Reject',
      messageIds: ['msg-asha-bday']
    });
    assert.equal(locked.status, 'locked');
  });

  it('supports bounded composer review, message actions, check-ins, and follow-ups', async () => {
    const test = fixture();
    const inspection = await test.runtime.execute({
      type: 'composer.inspect',
      contactId: 'c-rajesh',
      reason: 'Thanks'
    });
    const created = await test.runtime.execute({
      type: 'composer.create-template',
      contactId: 'c-rajesh',
      reason: 'Thanks',
      body: 'Private custom thank-you text that stays inside the local draft.'
    });
    const messageId =
      created.status === 'succeeded' && created.value.kind === 'message-action'
        ? created.value.createdMessageId
        : undefined;
    const duplicate = await test.runtime.execute({
      type: 'composer.create-template',
      contactId: 'c-rajesh',
      reason: 'Thanks',
      body: 'A second private local thank-you draft that requires duplicate review.'
    });
    const duplicateMessageId =
      duplicate.status === 'succeeded' && duplicate.value.kind === 'message-action'
        ? duplicate.value.createdMessageId
        : undefined;
    const acknowledged = await test.runtime.execute({
      type: 'messages.acknowledge-duplicate',
      messageId: duplicateMessageId ?? 'missing-message'
    });
    const variant = await test.runtime.execute({
      type: 'messages.select-variant',
      messageId: messageId ?? 'missing-message',
      variant: 'warm'
    });
    const channelChanged = await test.runtime.execute({
      type: 'messages.set-channel',
      messageId: messageId ?? 'missing-message',
      channel: 'Manual'
    });
    const edited = await test.runtime.execute({
      type: 'messages.edit',
      messageId: messageId ?? 'missing-message',
      body: 'Another private edited message body that must remain redacted.'
    });
    const approved = await test.runtime.execute({
      type: 'messages.approve',
      messageId: messageId ?? 'missing-message'
    });
    const revoked = await test.runtime.execute({ type: 'messages.revoke', messageId: messageId ?? 'missing-message' });
    const rejected = await test.runtime.execute({ type: 'messages.reject', messageId: messageId ?? 'missing-message' });
    const invalidAck = await test.runtime.execute({
      type: 'messages.acknowledge-duplicate',
      messageId: messageId ?? 'missing-message'
    });
    const snoozed = await test.runtime.execute({ type: 'checkins.snooze', contactId: 'c-mira', days: 14 });
    const contacted = await test.runtime.execute({ type: 'checkins.mark-contacted', contactId: 'c-mira' });

    const sentState = test.getState();
    sentState.messages = sentState.messages.map(message =>
      message.id === 'msg-mira-checkin' ? { ...message, status: 'Sent', sentAt: '2026-07-10T09:00:00.000Z' } : message
    );
    test.setState(sentState);
    const followUp = await test.runtime.execute({
      type: 'messages.schedule-follow-up',
      messageId: 'msg-mira-checkin',
      delayDays: 7
    });
    const serialized = JSON.stringify([
      inspection,
      created,
      duplicate,
      acknowledged,
      variant,
      channelChanged,
      edited,
      approved,
      revoked,
      rejected,
      invalidAck,
      snoozed,
      contacted,
      followUp
    ]);

    assert.equal(inspection.status, 'succeeded');
    assert.equal(
      inspection.status === 'succeeded' &&
        inspection.value.kind === 'composer-inspection' &&
        inspection.value.selectedTemplateTitle,
      'Thank you note'
    );
    assert.match(
      inspection.status === 'succeeded' && inspection.value.kind === 'composer-inspection'
        ? inspection.value.renderedTemplateBody
        : '',
      /Rajesh Nair/
    );
    if (inspection.status === 'succeeded' && inspection.value.kind === 'composer-inspection') {
      assert.equal(inspection.value.languageTarget, 'English');
      assert.match(inspection.value.contextDetail, /private note/i);
      assert.equal(inspection.value.templateAction.enabled, true);
      assert.match(inspection.value.templateAction.detail, /review-first/i);
      assert.ok(inspection.value.requestedTones.length > 0);
    }
    assert.ok(messageId);
    assert.equal(
      acknowledged.status === 'succeeded' && acknowledged.value.kind === 'message-action' && acknowledged.value.outcome,
      'applied'
    );
    assert.equal(
      variant.status === 'succeeded' && variant.value.kind === 'message-action' && variant.value.outcome,
      'applied'
    );
    assert.equal(
      channelChanged.status === 'succeeded' &&
        channelChanged.value.kind === 'message-action' &&
        channelChanged.value.outcome,
      'applied'
    );
    assert.equal(
      edited.status === 'succeeded' && edited.value.kind === 'message-action' && edited.value.outcome,
      'applied'
    );
    assert.equal(
      approved.status === 'succeeded' && approved.value.kind === 'message-action' && approved.value.outcome,
      'applied'
    );
    assert.equal(
      revoked.status === 'succeeded' && revoked.value.kind === 'message-action' && revoked.value.outcome,
      'applied'
    );
    assert.equal(
      rejected.status === 'succeeded' && rejected.value.kind === 'message-action' && rejected.value.outcome,
      'applied'
    );
    assert.equal(
      invalidAck.status === 'succeeded' && invalidAck.value.kind === 'message-action' && invalidAck.value.outcome,
      'blocked'
    );
    assert.equal(
      snoozed.status === 'succeeded' && snoozed.value.kind === 'checkin-action' && snoozed.value.outcome,
      'applied'
    );
    assert.equal(
      contacted.status === 'succeeded' && contacted.value.kind === 'checkin-action' && contacted.value.outcome,
      'applied'
    );
    assert.equal(
      followUp.status === 'succeeded' && followUp.value.kind === 'message-action' && followUp.value.outcome,
      'applied'
    );
    assert.doesNotMatch(serialized, /Private custom thank-you|Another private edited/i);
    assert.doesNotMatch(
      JSON.stringify(test.runtime.operationSnapshots()),
      /Private custom thank-you|Another private edited|Rajesh Nair/i
    );
  });

  it('uses a local review-first template when AI is disabled or its provider fails', async () => {
    const disabledState = createTestState();
    disabledState.settings.aiEnabled = false;
    const disabled = fixture(disabledState);
    let providerCalls = 0;
    disabled.dependencies.requestAiDraft = async () => {
      providerCalls += 1;
      throw new Error('provider must not be called');
    };
    const disabledResult = await disabled.runtime.execute({
      type: 'ai.draft',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    });

    const failed = fixture();
    failed.dependencies.requestAiDraft = async () => ({
      ok: false,
      error: { kind: 'network', message: 'Provider failed for mira@example.com at https://private.test.' }
    });
    const failedResult = await failed.runtime.execute({
      type: 'ai.draft',
      contactId: 'c-mira',
      reason: 'Check-in'
    });
    const beforeMismatch = failed.getState().messages.length;
    const mismatch = await failed.runtime.execute({
      type: 'ai.draft',
      contactId: 'c-mira',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    });

    assert.equal(providerCalls, 0);
    assert.equal(
      disabledResult.status === 'succeeded' && disabledResult.value.kind === 'ai-draft' && disabledResult.value.source,
      'local-template-fallback'
    );
    const disabledCreatedId =
      disabledResult.status === 'succeeded' && disabledResult.value.kind === 'ai-draft'
        ? disabledResult.value.createdMessageId
        : undefined;
    const disabledDraft = disabled.getState().messages.find(message => message.id === disabledCreatedId);
    assert.equal(disabledDraft?.eventId, 'e-asha-bday');
    assert.match(disabledDraft?.occurrenceDate ?? '', /^\d{4}-\d{2}-\d{2}$/);
    assert.ok(disabledDraft?.scheduledFor);
    assert.equal(
      failedResult.status === 'succeeded' && failedResult.value.kind === 'ai-draft' && failedResult.value.source,
      'local-template-fallback'
    );
    assert.equal(mismatch.status, 'failed');
    assert.equal(failed.getState().messages.length, beforeMismatch);
    assert.doesNotMatch(JSON.stringify(failedResult), /mira@example|private\.test|checking in/i);
  });

  it('records unknown email delivery and blocks every duplicate attempt in the runtime', async () => {
    const state = createTestState();
    state.settings.emailEnabled = true;
    state.emailDelivery.senderEmail = 'sender@example.com';
    state.messages.unshift(
      approvedMessage(state.messages[0], {
        id: 'email-runtime',
        contactId: 'c-rajesh',
        eventId: undefined,
        channel: 'Email',
        body: 'A professional approved email message.'
      })
    );
    const test = fixture(state);
    let sends = 0;
    let settle!: (value: Awaited<ReturnType<CommandRuntimeDependencies['sendEmail']>>) => void;
    test.dependencies.sendEmail = _request => {
      sends += 1;
      return new Promise(resolve => {
        settle = resolve;
      });
    };

    const firstPromise = test.runtime.execute({ type: 'email.deliver', messageId: 'email-runtime' });
    await Promise.resolve();
    const emailScope = test.runtime.operationSnapshots().find(operation => operation.scope.startsWith('email:'))?.scope;
    assert.ok(emailScope);
    assert.equal(test.runtime.cancelOperation(emailScope), false);
    settle({
      ok: false,
      outcome: 'unknown',
      idempotencyKey: `relateai-email-v1:email-runtime:2026-07-10T09:00:00.000Z`,
      error: {
        kind: 'delivery-unknown',
        message: 'Lost response for sender@example.com at https://private-provider.test.'
      }
    });
    const first = await firstPromise;
    const duplicate = await test.runtime.execute({ type: 'email.deliver', messageId: 'email-runtime' });

    assert.equal(first.status, 'unknown');
    assert.equal(duplicate.status, 'failed');
    assert.equal(sends, 1);
    assert.equal(test.getState().messages.find(message => message.id === 'email-runtime')?.status, 'Delivery unknown');
    assert.doesNotMatch(JSON.stringify(first), /sender@example|private-provider|professional approved/i);
    assert.doesNotMatch(
      test.getState().messages.find(message => message.id === 'email-runtime')?.lastError ?? '',
      /sender@example|private-provider/i
    );
  });

  it('commits delivery-unknown even if an external coordinator aborts in-flight email', async () => {
    const state = createTestState();
    state.settings.emailEnabled = true;
    state.emailDelivery.senderEmail = 'sender@example.com';
    state.messages.unshift(
      approvedMessage(state.messages[0], {
        id: 'email-aborted-runtime',
        contactId: 'c-rajesh',
        eventId: undefined,
        channel: 'Email',
        body: 'Another professional approved email message.'
      })
    );
    const test = fixture(state);
    test.dependencies.sendEmail = (_request, signal) =>
      new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new Error('transport aborted')), {
          once: true
        });
      });

    const execution = test.runtime.execute({
      type: 'email.deliver',
      messageId: 'email-aborted-runtime'
    });
    await Promise.resolve();
    const scope = test.runtime.operationSnapshots().find(operation => operation.scope.startsWith('email:'))?.scope;
    assert.ok(scope);
    assert.equal(test.operations.cancel(scope), true);
    assert.equal((await execution).status, 'cancelled');
    assert.equal(
      test.getState().messages.find(message => message.id === 'email-aborted-runtime')?.status,
      'Delivery unknown'
    );
  });

  it('commits a verified provider email result without exposing routing data', async () => {
    const state = createTestState();
    state.settings.emailEnabled = true;
    state.emailDelivery.senderEmail = 'sender@example.com';
    state.messages.unshift(
      approvedMessage(state.messages[0], {
        id: 'email-sent-runtime',
        contactId: 'c-rajesh',
        eventId: undefined,
        channel: 'Email',
        body: 'A verified professional provider email message.'
      })
    );
    const test = fixture(state);
    const result = await test.runtime.execute({
      type: 'email.deliver',
      messageId: 'email-sent-runtime'
    });

    assert.equal(result.status, 'succeeded');
    assert.equal(test.getState().messages.find(message => message.id === 'email-sent-runtime')?.status, 'Sent');
    assert.doesNotMatch(JSON.stringify(result), /sender@example|rajesh|provider email message/i);
  });

  it('checks the current scheduling policy immediately before provider email dispatch', async () => {
    const state = createTestState();
    state.settings.emailEnabled = true;
    state.emailDelivery.senderEmail = 'sender@example.com';
    state.messages.unshift(
      approvedMessage(state.messages[0], {
        id: 'email-future-runtime',
        contactId: 'c-rajesh',
        eventId: undefined,
        channel: 'Email',
        body: 'A professional approved email that is not due yet.',
        scheduledFor: '2026-07-10T11:00:00.000Z'
      })
    );
    const test = fixture(state);
    let sends = 0;
    test.dependencies.sendEmail = async () => {
      sends += 1;
      return { ok: true, status: 'sent', deliveryId: 'must-not-dispatch' };
    };

    const result = await test.runtime.execute({ type: 'email.deliver', messageId: 'email-future-runtime' });

    assert.equal(result.status, 'failed');
    assert.equal(result.status === 'failed' && result.error.code, 'email-schedule-blocked');
    assert.match(result.status === 'failed' ? result.error.summary : '', /not due yet/i);
    assert.equal(sends, 0);
    assert.equal(test.getState().messages.find(message => message.id === 'email-future-runtime')?.status, 'Scheduled');
  });

  it('blocks provider email when duplicate risk changes after approval', async () => {
    const state = createTestState();
    state.settings.emailEnabled = true;
    state.emailDelivery.senderEmail = 'sender@example.com';
    const target = approvedMessage(state.messages[0], {
      id: 'email-duplicate-runtime',
      contactId: 'c-rajesh',
      eventId: undefined,
      channel: 'Email',
      body: 'A professional approved email message for the same occasion.'
    });
    state.messages.unshift(target, {
      ...target,
      id: 'email-newly-sent-duplicate',
      status: 'Sent',
      sentAt: '2026-07-10T09:30:00.000Z',
      approvedAt: undefined,
      approvalExpiresAt: undefined
    });
    const test = fixture(state);
    let sends = 0;
    test.dependencies.sendEmail = async () => {
      sends += 1;
      return { ok: true, status: 'sent', deliveryId: 'must-not-send-duplicate' };
    };

    const result = await test.runtime.execute({ type: 'email.deliver', messageId: target.id });

    assert.equal(result.status, 'failed');
    assert.equal(result.status === 'failed' && result.error.code, 'email-duplicate-risk');
    assert.equal(sends, 0);
    assert.equal(test.getState().messages.find(message => message.id === target.id)?.status, 'Scheduled');
  });

  it('reconciles a saved unknown email attempt without resending and keeps unknown responses locked', async () => {
    const state = createTestState();
    state.messages.unshift({
      ...state.messages[0],
      id: 'email-reconcile-runtime',
      contactId: 'c-rajesh',
      eventId: undefined,
      channel: 'Email',
      status: 'Delivery unknown',
      body: 'Private email body for reconciliation.',
      emailDeliveryAttempt: {
        idempotencyKey: 'email-reconcile-attempt-1',
        status: 'Unknown',
        deliveryId: 'delivery-original',
        updatedAt: '2026-07-10T09:00:00.000Z'
      }
    });
    const test = fixture(state);
    let reconciliations = 0;
    let sends = 0;
    test.dependencies.sendEmail = async () => {
      sends += 1;
      return { ok: true, status: 'sent', deliveryId: 'must-not-send' };
    };
    test.dependencies.reconcileEmail = async () => {
      reconciliations += 1;
      return { ok: true, status: 'sent', deliveryId: 'delivery-reconciled' };
    };
    const sent = await test.runtime.execute({ type: 'email.reconcile', messageId: 'email-reconcile-runtime' });

    const unknownState = structuredClone(test.getState());
    unknownState.messages.unshift({
      ...unknownState.messages[0],
      id: 'email-unknown-runtime',
      contactId: 'c-rajesh',
      eventId: undefined,
      channel: 'Email',
      status: 'Delivery unknown',
      body: 'A second private reconciliation body.',
      emailDeliveryAttempt: {
        idempotencyKey: 'email-reconcile-attempt-2',
        status: 'Unknown',
        updatedAt: '2026-07-10T09:00:00.000Z'
      }
    });
    test.setState(unknownState);
    test.dependencies.reconcileEmail = async () => ({
      ok: false,
      outcome: 'unknown',
      error: {
        kind: 'delivery-unknown',
        message: 'Status lost for private@example.com at https://provider.test.'
      }
    });
    const stillUnknown = await test.runtime.execute({ type: 'email.reconcile', messageId: 'email-unknown-runtime' });

    assert.equal(sent.status, 'succeeded');
    assert.equal(test.getState().messages.find(message => message.id === 'email-reconcile-runtime')?.status, 'Sent');
    assert.equal(stillUnknown.status, 'unknown');
    assert.equal(
      test.getState().messages.find(message => message.id === 'email-unknown-runtime')?.status,
      'Delivery unknown'
    );
    assert.equal(reconciliations, 1);
    assert.equal(sends, 0);
    assert.doesNotMatch(JSON.stringify(stillUnknown), /private@example|provider\.test|Private email body/i);
  });

  it('requires an actual handoff-open result before accepting manual sent confirmation', async () => {
    const state = createTestState();
    state.messages = state.messages.map(message =>
      message.id === 'msg-mira-checkin' ? approvedMessage(message, { channel: 'Manual' }) : message
    );
    const test = fixture(state);

    const premature = await test.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });
    const opened = await test.runtime.execute({
      type: 'handoff.open',
      messageId: 'msg-mira-checkin'
    });
    assert.notEqual(test.getState().messages.find(message => message.id === 'msg-mira-checkin')?.status, 'Sent');
    const confirmed = await test.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });

    assert.equal(premature.status, 'failed');
    assert.equal(opened.status, 'succeeded');
    assert.equal(confirmed.status, 'succeeded');
    assert.equal(test.getState().messages.find(message => message.id === 'msg-mira-checkin')?.status, 'Sent');
  });

  it('keeps an explicit copy/share fallback usable when an approved recipient route disappears', async () => {
    const state = createTestState();
    state.messages = state.messages.map(message =>
      message.id === 'msg-mira-checkin' ? approvedMessage(message, { channel: 'SMS' }) : message
    );
    state.contacts = state.contacts.map(contact =>
      contact.id === 'c-mira' ? { ...contact, phone: undefined, routes: [] } : contact
    );
    const test = fixture(state);
    test.dependencies.openHandoff = async input => {
      assert.equal(input.preferFallback, true);
      assert.equal(input.body, state.messages.find(message => message.id === 'msg-mira-checkin')?.body);
      return { outcome: 'shared-fallback', usedFallback: true, needsSentConfirmation: true };
    };

    const direct = await test.runtime.execute({
      type: 'handoff.open',
      messageId: 'msg-mira-checkin',
      preferFallback: false
    });
    const fallback = await test.runtime.execute({
      type: 'handoff.open',
      messageId: 'msg-mira-checkin',
      preferFallback: true
    });
    const confirmed = await test.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });

    assert.equal(direct.status, 'failed');
    assert.equal(fallback.status, 'succeeded');
    assert.equal(confirmed.status, 'succeeded');
    assert.equal(test.getState().messages.find(message => message.id === 'msg-mira-checkin')?.status, 'Sent');
  });

  it('preserves a content-free handoff confirmation across background and requires unlock before confirming', async () => {
    const state = createTestState();
    state.settings.biometricLockEnabled = true;
    state.messages = state.messages.map(message =>
      message.id === 'msg-mira-checkin' ? approvedMessage(message, { channel: 'Manual' }) : message
    );
    const test = fixture(state);

    assert.equal((await test.runtime.execute({ type: 'biometric.unlock' })).status, 'succeeded');
    assert.equal(
      (await test.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' })).status,
      'succeeded'
    );
    test.runtime.onBackground();
    const locked = await test.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });
    assert.equal(locked.status, 'locked');

    await test.runtime.execute({ type: 'biometric.unlock' });
    const confirmed = await test.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });
    assert.equal(confirmed.status, 'succeeded');
    assert.equal(test.getState().messages.find(message => message.id === 'msg-mira-checkin')?.status, 'Sent');
    assert.doesNotMatch(JSON.stringify(test.runtime.operationSnapshots()), /Hey Mira|Pune|design role/i);
  });

  it('expires or invalidates handoff confirmation on explicit lock, message changes, and committed data changes', async () => {
    const buildTest = () => {
      const state = createTestState();
      state.messages = state.messages.map(message =>
        message.id === 'msg-mira-checkin' ? approvedMessage(message, { channel: 'Manual' }) : message
      );
      return fixture(state);
    };

    const expired = buildTest();
    await expired.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' });
    expired.dependencies.now = () => new Date('2026-07-10T10:05:00.001Z');
    const expiredConfirmation = await expired.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });

    const explicitlyLocked = buildTest();
    await explicitlyLocked.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' });
    explicitlyLocked.runtime.lockSensitiveSession();
    const lockConfirmation = await explicitlyLocked.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });

    const changed = buildTest();
    await changed.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' });
    const changedState = structuredClone(changed.getState());
    changedState.messages = changedState.messages.map(message =>
      message.id === 'msg-mira-checkin' ? { ...message, body: `${message.body} Changed.` } : message
    );
    changed.setState(changedState);
    const changedConfirmation = await changed.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });

    const committed = buildTest();
    await committed.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' });
    await committed.runtime.execute({
      type: 'domain.dispatch',
      action: { type: 'setSearch', query: 'a data change' }
    });
    const committedConfirmation = await committed.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });

    assert.equal(expiredConfirmation.status, 'failed');
    assert.equal(lockConfirmation.status, 'failed');
    assert.equal(changedConfirmation.status, 'failed');
    assert.equal(committedConfirmation.status, 'failed');
    for (const result of [expiredConfirmation, lockConfirmation, changedConfirmation, committedConfirmation]) {
      assert.equal(result.status === 'failed' && result.error.code, 'handoff-confirmation-stale');
    }
  });

  it('rechecks body, approval, route, and current dispatch timing at handoff open and confirm', async () => {
    const state = createTestState();
    state.messages = state.messages.map(message =>
      message.id === 'msg-mira-checkin' ? approvedMessage(message, { channel: 'Manual' }) : message
    );
    const test = fixture(state);
    const opened = await test.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' });
    assert.equal(opened.status, 'succeeded');

    const blockedState = structuredClone(test.getState());
    blockedState.settings.blackouts = [
      {
        id: 'blackout-runtime',
        label: 'Current manual pause',
        startDate: '2026-07-10',
        endDate: '2026-07-10',
        behavior: 'Block',
        channels: ['Manual']
      }
    ];
    test.setState(blockedState);
    const blockedConfirm = await test.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });
    assert.equal(blockedConfirm.status, 'failed');
    assert.equal(blockedConfirm.status === 'failed' && blockedConfirm.error.code, 'handoff-confirmation-blocked');
    assert.match(blockedConfirm.status === 'failed' ? blockedConfirm.error.summary : '', /scheduling policy|blackout/i);

    const futureState = createTestState();
    futureState.messages = futureState.messages.map(message =>
      message.id === 'msg-mira-checkin'
        ? approvedMessage(message, { channel: 'Manual', scheduledFor: '2026-07-10T11:00:00.000Z' })
        : message
    );
    const future = fixture(futureState);
    const blockedOpen = await future.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' });
    assert.equal(blockedOpen.status, 'failed');
    assert.equal(blockedOpen.status === 'failed' && blockedOpen.error.code, 'handoff-not-ready');
    assert.match(blockedOpen.status === 'failed' ? blockedOpen.error.summary : '', /not due yet/i);

    const invalidState = createTestState();
    invalidState.messages = invalidState.messages.map(message =>
      message.id === 'msg-mira-checkin' ? approvedMessage(message, { channel: 'SMS', body: 'short' }) : message
    );
    const invalid = fixture(invalidState);
    const invalidOpen = await invalid.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' });
    assert.equal(invalidOpen.status, 'failed');
    assert.match(invalidOpen.status === 'failed' ? invalidOpen.error.summary : '', /longer message/i);
  });

  it('rechecks duplicate risk at manual handoff open and confirmation', async () => {
    const buildState = () => {
      const state = createTestState();
      state.messages = state.messages.map(message =>
        message.id === 'msg-mira-checkin' ? approvedMessage(message, { channel: 'Manual' }) : message
      );
      return state;
    };
    const duplicateOf = (message: MessageDraft): MessageDraft => ({
      ...message,
      id: 'new-manual-duplicate',
      status: 'Sent',
      sentAt: '2026-07-10T09:30:00.000Z',
      approvedAt: undefined,
      approvalExpiresAt: undefined
    });

    const blockedAtOpenState = buildState();
    const blockedTarget = blockedAtOpenState.messages.find(message => message.id === 'msg-mira-checkin');
    assert.ok(blockedTarget);
    blockedAtOpenState.messages.push(duplicateOf(blockedTarget));
    const blockedAtOpen = fixture(blockedAtOpenState);
    let opens = 0;
    blockedAtOpen.dependencies.openHandoff = async () => {
      opens += 1;
      return { outcome: 'opened-destination', usedFallback: false, needsSentConfirmation: true };
    };
    const openResult = await blockedAtOpen.runtime.execute({ type: 'handoff.open', messageId: blockedTarget.id });
    assert.equal(openResult.status, 'failed');
    assert.match(openResult.status === 'failed' ? openResult.error.summary : '', /duplicate risk changed/i);
    assert.equal(opens, 0);

    const changedAfterOpen = fixture(buildState());
    assert.equal(
      (await changedAfterOpen.runtime.execute({ type: 'handoff.open', messageId: 'msg-mira-checkin' })).status,
      'succeeded'
    );
    const changedState = structuredClone(changedAfterOpen.getState());
    const changedTarget = changedState.messages.find(message => message.id === 'msg-mira-checkin');
    assert.ok(changedTarget);
    changedState.messages.push(duplicateOf(changedTarget));
    changedAfterOpen.setState(changedState);
    const confirmation = await changedAfterOpen.runtime.execute({
      type: 'handoff.confirm',
      messageId: 'msg-mira-checkin',
      sent: true
    });
    assert.equal(confirmation.status, 'failed');
    assert.equal(confirmation.status === 'failed' && confirmation.error.code, 'handoff-confirmation-blocked');
    assert.match(confirmation.status === 'failed' ? confirmation.error.summary : '', /duplicate risk changed/i);
    assert.equal(
      changedAfterOpen.getState().messages.find(message => message.id === 'msg-mira-checkin')?.status,
      'Scheduled'
    );
  });

  it('stages bounded event text for explicit review and applies it only through the shared import session', async () => {
    const test = fixture();
    const beforeEvents = test.getState().events.length;
    const preview = await test.runtime.execute({
      type: 'events.import-text',
      format: 'csv',
      raw: 'name,type,date,notes\nSensitive Name,Birthday,2026-12-05,Private-looking note'
    });

    assert.equal(preview.status, 'succeeded');
    assert.equal(test.getState().events.length, beforeEvents);
    const staged =
      preview.status === 'succeeded' && preview.value.kind === 'calendar-import-preview' ? preview.value : undefined;
    assert.equal(staged?.reviewItems[0]?.title, 'Sensitive Name Birthday');
    const applied = await test.runtime.execute({
      type: 'calendar.import-apply',
      sessionToken: staged?.sessionToken ?? '',
      decisions: (staged?.reviewItems ?? []).map(item => ({ reviewId: item.reviewId, action: 'apply' as const }))
    });
    assert.equal(applied.status, 'succeeded');
    const imported = test.getState().events.find(event => event.label === 'Sensitive Name Birthday');
    assert.equal(imported?.verified, false);
    assert.equal(imported?.source, 'Imported');
    assert.doesNotMatch(JSON.stringify(test.runtime.operationSnapshots()), /Sensitive Name|Private-looking/);
  });

  it('turns invalid event-text rows into editable review items instead of mutating or silently dropping them', async () => {
    const test = fixture();
    const beforeEvents = test.getState().events.length;
    const preview = await test.runtime.execute({
      type: 'events.import-text',
      format: 'csv',
      raw: 'name,type,date\nNeeds Correction,Birthday,not-a-date'
    });
    const staged =
      preview.status === 'succeeded' && preview.value.kind === 'calendar-import-preview' ? preview.value : undefined;
    assert.equal(test.getState().events.length, beforeEvents);
    assert.equal(staged?.invalid, 1);
    assert.equal(staged?.parseErrorCount, 1);
    assert.deepEqual(staged?.reviewItems[0]?.validationErrors, ['Enter a valid event date.']);

    const applied = await test.runtime.execute({
      type: 'calendar.import-apply',
      sessionToken: staged?.sessionToken ?? '',
      decisions: [
        {
          reviewId: staged?.reviewItems[0]?.reviewId ?? '',
          action: 'edit',
          title: 'Needs Correction Birthday',
          date: '2026-12-06'
        }
      ]
    });
    assert.equal(applied.status, 'succeeded');
    assert.ok(test.getState().events.some(event => event.label === 'Needs Correction Birthday'));
  });

  it('opens a selected native event file only into the staged review workflow', async () => {
    const test = fixture();
    const beforeEvents = test.getState().events.length;
    test.dependencies.pickEventImportFile = async () => ({
      name: 'relationship-events.csv',
      raw: 'name,type,date\nFile Contact,Birthday,2026-12-07'
    });

    const preview = await test.runtime.execute({ type: 'events.import-file' });
    const staged =
      preview.status === 'succeeded' && preview.value.kind === 'calendar-import-preview' ? preview.value : undefined;

    assert.equal(test.getState().events.length, beforeEvents);
    assert.equal(staged?.reviewItems[0]?.title, 'File Contact Birthday');
    assert.equal(parseHarnessCommand({ type: 'events.import-file', raw: 'private' }).ok, false);
  });

  it('returns explicit file-selection cancellation recovery and supports operation cancellation', async () => {
    const cancelledSelection = fixture();
    const noSelection = await cancelledSelection.runtime.execute({ type: 'events.import-file' });
    assert.equal(noSelection.status, 'failed');
    assert.equal(noSelection.status === 'failed' && noSelection.error.code, 'event-file-import-cancelled');
    assert.match(noSelection.status === 'failed' ? noSelection.error.summary : '', /events\.import-text/);

    const activeSelection = fixture();
    let release!: (value: { name: string; raw: string } | undefined) => void;
    activeSelection.dependencies.pickEventImportFile = () =>
      new Promise(resolve => {
        release = resolve;
      });
    const execution = activeSelection.runtime.execute({ type: 'events.import-file' });
    await Promise.resolve();
    const cancellation = await activeSelection.runtime.execute({
      type: 'operation.cancel',
      scope: 'events:import-file'
    });
    release(undefined);

    assert.equal(
      cancellation.status === 'succeeded' &&
        cancellation.value.kind === 'operation-cancellation' &&
        cancellation.value.cancelled,
      true
    );
    assert.equal((await execution).status, 'cancelled');
  });

  it('records backup freshness only after portable-copy confirmation, then restores and clears transactionally', async () => {
    const test = fixture();
    const passphrase = 'correct horse 123 battery';
    const backupsBefore = test.getState().backups.length;
    const exported = await test.runtime.execute({
      type: 'backup.export',
      passphrase,
      destination: 'share'
    });
    assert.equal(exported.status, 'succeeded');
    assert.doesNotMatch(JSON.stringify(exported), /private\/path|correct horse/);
    assert.equal(test.getState().backups.length, backupsBefore);
    const backupConfirmationToken =
      exported.status === 'succeeded' && exported.value.kind === 'backup-export'
        ? exported.value.backupConfirmationToken
        : undefined;
    assert.ok(backupConfirmationToken);
    assert.equal(
      exported.status === 'succeeded' && exported.value.kind === 'backup-export'
        ? exported.value.freshnessRecorded
        : undefined,
      false
    );
    assert.equal(
      exported.status === 'succeeded' && exported.value.kind === 'backup-export' ? exported.value.fileName : undefined,
      'RelateAI-backup-2026-07-10.relateai'
    );
    assert.equal(
      exported.status === 'succeeded' && exported.value.kind === 'backup-export' ? exported.value.byteCount : undefined,
      4096
    );
    test.runtime.onBackground();
    const confirmedExport = await test.runtime.execute({
      type: 'backup.export-confirm',
      backupConfirmationToken
    });
    assert.equal(confirmedExport.status, 'succeeded');
    assert.equal(test.getState().backups.length, backupsBefore + 1);

    const restoredState = createTestState();
    restoredState.onboarding.completed = true;
    const raw = await createEncryptedBackup(restoredState, passphrase, { iterations: 1_000 });
    test.dependencies.decryptBackup = async () => restoredState;
    test.dependencies.restoreData = async restored => ({
      status: 'reconciliation-required',
      state: restored,
      message: 'Native reconciliation required.'
    });
    const previewed = await test.runtime.execute({ type: 'backup.restore-preview', raw, passphrase });
    assert.equal(previewed.status, 'succeeded');
    assert.equal(test.installedStates.length, 0);
    const confirmationToken =
      previewed.status === 'succeeded' && previewed.value.kind === 'backup-restore-preview'
        ? previewed.value.confirmationToken
        : '';
    const restored = await test.runtime.execute({
      type: 'backup.restore-confirm',
      confirmationToken
    });
    assert.equal(restored.status, 'succeeded');
    assert.equal(test.installedStates.length, 1);
    assert.equal(test.getState().onboarding.completed, true);

    const cleared = await test.runtime.execute({
      type: 'data.clear',
      confirmation: 'CLEAR LOCAL DATA'
    });
    assert.equal(cleared.status, 'succeeded');
    assert.equal(test.installedStates.length, 2);
    assert.equal(test.getState().contacts.length, 0);
  });

  it('records a saved backup immediately only when the adapter verifies a user-accessible portable copy', async () => {
    const test = fixture();
    const passphrase = 'correct horse 123 battery';
    test.dependencies.exportBackup = async current => ({
      uri: '/private/verified-user-destination',
      fileName: 'RelateAI-backup-2026-07-10.relateai',
      byteCount: 8192,
      shared: false,
      preview: {
        format: 'relateai.encrypted-backup',
        version: 2,
        app: 'RelateAI',
        createdAt: now.toISOString(),
        encrypted: true,
        persistenceVersion: 3,
        recordCounts: countBackupRecords(current),
        recordCount: totalBackupRecords(countBackupRecords(current)),
        warnings: []
      },
      disposition: 'saved-export',
      temporaryFileRemoved: false,
      verifiedPortableCopy: true
    });
    const before = test.getState().backups.length;
    const result = await test.runtime.execute({ type: 'backup.export', passphrase, destination: 'save' });

    assert.equal(result.status, 'succeeded');
    assert.equal(test.getState().backups.length, before + 1);
    assert.equal(
      result.status === 'succeeded' && result.value.kind === 'backup-export'
        ? result.value.freshnessRecorded
        : undefined,
      true
    );
    assert.equal(
      result.status === 'succeeded' && result.value.kind === 'backup-export'
        ? result.value.backupConfirmationToken
        : undefined,
      undefined
    );
  });

  it('keeps a selected backup only in an expiring tokenized session and never returns file details', async () => {
    const test = fixture();
    const passphrase = 'correct horse 123 battery';
    const restoredState = createTestState();
    restoredState.onboarding.completed = true;
    const raw = await createEncryptedBackup(restoredState, passphrase, { iterations: 1_000 });
    test.dependencies.selectBackup = async () => ({
      name: 'private-backup-name.relateai',
      uri: '/private/backup/location.relateai',
      raw,
      preview: previewEncryptedBackup(raw),
      temporaryFileRemoved: true
    });
    test.dependencies.decryptBackup = async () => restoredState;

    const selected = await test.runtime.execute({ type: 'backup.select-file' });
    const selectionToken =
      selected.status === 'succeeded' && selected.value.kind === 'backup-file-selection'
        ? selected.value.selectionToken
        : '';
    const previewed = await test.runtime.execute({
      type: 'backup.restore-preview-selected',
      selectionToken,
      passphrase
    });
    const serialized = JSON.stringify([selected, previewed]);

    assert.equal(selected.status, 'succeeded');
    assert.equal(previewed.status, 'succeeded');
    assert.doesNotMatch(serialized, /private-backup-name|private\/backup|correct horse|ciphertext|salt/i);

    const selectedAgain = await test.runtime.execute({ type: 'backup.select-file' });
    const secondToken =
      selectedAgain.status === 'succeeded' && selectedAgain.value.kind === 'backup-file-selection'
        ? selectedAgain.value.selectionToken
        : '';
    test.runtime.onBackground();
    const cleared = await test.runtime.execute({
      type: 'backup.restore-preview-selected',
      selectionToken: secondToken,
      passphrase
    });
    assert.equal(cleared.status, 'failed');
  });

  it('does not retain sensitive backup commands as generic retry closures after failure', async () => {
    const test = fixture();
    const passphrase = 'never retain 123 this phrase';
    test.dependencies.exportBackup = async () => {
      throw new Error(`failed for ${passphrase} at /private/secret/path`);
    };

    const result = await test.runtime.execute({
      type: 'backup.export',
      passphrase,
      destination: 'save'
    });
    const retry = await test.operations.retry('backup:export');

    assert.equal(result.status, 'failed');
    assert.doesNotMatch(JSON.stringify(result), /never retain|private\/secret/);
    assert.equal(retry.status, 'failed');
    assert.equal(retry.status === 'failed' && retry.error.code, 'retry-unavailable');
  });

  it('refreshes and preflights permissions and requires fresh biometric unlock for protected exports', async () => {
    const state = createTestState();
    state.settings.biometricLockEnabled = true;
    const test = fixture(state);
    assert.equal(test.runtime.isApplicationLocked(), true);

    const lockedRefresh = await test.runtime.execute({ type: 'permissions.refresh' });
    const lockedPreflight = await test.runtime.execute({
      type: 'permissions.preflight',
      capability: 'Calendar'
    });
    const lockedDomain = await test.runtime.execute({
      type: 'domain.dispatch',
      action: { type: 'setSearch', query: 'must stay locked' }
    });
    const lockedQuery = await test.runtime.execute({ type: 'contacts.query' });
    const lockedAi = await test.runtime.execute({ type: 'ai.draft', contactId: 'c-mira', reason: 'Check-in' });
    const lockedImport = await test.runtime.execute({ type: 'contacts.import' });
    const blocked = await test.runtime.execute({
      type: 'backup.export',
      passphrase: 'correct horse 123 battery'
    });
    const unlock = await test.runtime.execute({ type: 'biometric.unlock' });
    const refresh = await test.runtime.execute({ type: 'permissions.refresh' });
    const preflight = await test.runtime.execute({
      type: 'permissions.preflight',
      capability: 'Calendar'
    });
    const exported = await test.runtime.execute({
      type: 'backup.export',
      passphrase: 'correct horse 123 battery'
    });
    test.runtime.onBackground();
    const relocked = await test.runtime.execute({
      type: 'backup.export',
      passphrase: 'correct horse 123 battery'
    });
    const unlockAgain = await test.runtime.execute({ type: 'biometric.unlock' });
    const exportedAgain = await test.runtime.execute({
      type: 'backup.export',
      passphrase: 'correct horse 123 battery'
    });
    const consumed = await test.runtime.execute({
      type: 'backup.export',
      passphrase: 'correct horse 123 battery'
    });

    assert.equal(lockedRefresh.status, 'locked');
    assert.equal(lockedPreflight.status, 'locked');
    assert.equal(lockedDomain.status, 'locked');
    assert.equal(lockedQuery.status, 'locked');
    assert.equal(lockedAi.status, 'locked');
    assert.equal(lockedImport.status, 'locked');
    assert.equal(test.runtime.isApplicationLocked(), false);
    assert.equal(blocked.status, 'locked');
    assert.equal(unlock.status, 'succeeded');
    assert.equal(refresh.status, 'succeeded');
    assert.equal(preflight.status, 'succeeded');
    assert.equal(exported.status, 'succeeded');
    assert.equal(relocked.status, 'locked');
    assert.equal(unlockAgain.status, 'succeeded');
    assert.equal(exportedAgain.status, 'succeeded');
    assert.equal(consumed.status, 'failed');
  });

  it('enables biometric lock only after live capability preflight and successful authentication', async () => {
    const test = fixture();
    let authentications = 0;
    test.dependencies.authenticateBiometric = async () => {
      authentications += 1;
      return true;
    };
    const enabled = await test.runtime.execute({ type: 'biometric.enable' });

    assert.equal(enabled.status, 'succeeded');
    assert.equal(
      enabled.status === 'succeeded' && enabled.value.kind === 'biometric-setting' && enabled.value.outcome,
      'applied'
    );
    assert.equal(test.getState().settings.biometricLockEnabled, true);
    assert.equal(test.runtime.isApplicationLocked(), false);
    assert.equal(authentications, 1);

    const unavailable = fixture();
    const records = structuredClone(unavailable.permissionRecords);
    records['Biometric lock'] = {
      ...records['Biometric lock'],
      systemAuthorization: 'not-enrolled',
      platformStatus: 'not-enrolled'
    };
    let unavailableAuthentications = 0;
    unavailable.dependencies.preflightPermission = async () => ({
      capability: 'Biometric lock',
      allowed: false,
      authorization: 'not-enrolled',
      checkedAt: now.toISOString(),
      record: records['Biometric lock'],
      records
    });
    unavailable.dependencies.authenticateBiometric = async () => {
      unavailableAuthentications += 1;
      return true;
    };
    const blocked = await unavailable.runtime.execute({ type: 'biometric.enable' });
    assert.equal(blocked.status, 'failed');
    assert.equal(blocked.status === 'failed' && blocked.error.code, 'biometric-enable-unavailable');
    assert.equal(unavailable.getState().settings.biometricLockEnabled, false);
    assert.equal(unavailableAuthentications, 0);
  });

  it('allows locked biometric disable only with authentication and uses destructive clear for unavailable recovery', async () => {
    const lockedState = createTestState();
    lockedState.settings.biometricLockEnabled = true;
    const locked = fixture(lockedState);
    let authentications = 0;
    locked.dependencies.authenticateBiometric = async () => {
      authentications += 1;
      return true;
    };
    const disabled = await locked.runtime.execute({ type: 'biometric.disable' });
    assert.equal(disabled.status, 'succeeded');
    assert.equal(locked.getState().settings.biometricLockEnabled, false);
    assert.equal(authentications, 1);

    const recoveryState = createTestState();
    recoveryState.settings.biometricLockEnabled = true;
    const recovery = fixture(recoveryState);
    const records = structuredClone(recovery.permissionRecords);
    records['Biometric lock'] = {
      ...records['Biometric lock'],
      systemAuthorization: 'unavailable',
      platformStatus: 'no-hardware'
    };
    recovery.dependencies.preflightPermission = async () => ({
      capability: 'Biometric lock',
      allowed: false,
      authorization: 'unavailable',
      checkedAt: now.toISOString(),
      record: records['Biometric lock'],
      records
    });
    const unavailableDisable = await recovery.runtime.execute({ type: 'biometric.disable' });
    assert.equal(unavailableDisable.status, 'failed');
    assert.equal(recovery.getState().settings.biometricLockEnabled, true);

    const recovered = await recovery.runtime.execute({
      type: 'data.clear',
      confirmation: 'CLEAR LOCAL DATA'
    });
    assert.equal(recovered.status, 'succeeded');
    assert.equal(recovery.getState().contacts.length, 0);
    assert.equal(recovery.getState().settings.biometricLockEnabled, false);
    assert.equal(recovery.runtime.isApplicationLocked(), false);
  });

  it('records explicit permission intent and returns only live authorization metadata', async () => {
    const test = fixture();
    const allowed = await test.runtime.execute({
      type: 'permissions.request',
      capability: 'Notifications',
      userIntent: 'allow'
    });
    const declined = await test.runtime.execute({
      type: 'permissions.request',
      capability: 'Calendar',
      userIntent: 'decline'
    });

    assert.equal(allowed.status, 'succeeded');
    assert.equal(
      allowed.status === 'succeeded' && allowed.value.kind === 'permission-request' && allowed.value.status,
      'granted'
    );
    assert.equal(
      declined.status === 'succeeded' && declined.value.kind === 'permission-request' && declined.value.status,
      'declined'
    );
  });

  it('returns aggregate-only analytics and setup inspection results', async () => {
    const test = fixture();
    const analytics = await test.runtime.execute({ type: 'analytics.inspect', range: 'All time' });
    const setup = await test.runtime.execute({ type: 'setup.inspect' });
    const serialized = JSON.stringify([analytics, setup]);

    assert.equal(analytics.status, 'succeeded');
    assert.equal(setup.status, 'succeeded');
    const analyticsValue =
      analytics.status === 'succeeded' && analytics.value.kind === 'analytics-inspection' ? analytics.value : undefined;
    const setupValue =
      setup.status === 'succeeded' && setup.value.kind === 'setup-inspection' ? setup.value : undefined;
    assert.ok(setupValue?.checksByGroup.some(group => group.checks.length > 0));
    assert.equal(analyticsValue?.insightCount, analyticsValue?.insights.length);
    assert.ok(analyticsValue?.insights.every(insight => insight.id && insight.actionLabel && insight.targetScreen));
    assert.ok(
      test.actions.some(action => action.type === 'setupDoctorDryRunRecorded'),
      'Setup Check dry runs must leave a redacted audit entry.'
    );
    assert.match(serialized, /"redacted":true/);
    test.getState().contacts.forEach(contact => {
      assert.doesNotMatch(serialized, new RegExp(contact.name, 'i'));
      if (contact.phone) assert.equal(serialized.includes(contact.phone), false);
      if (contact.email) assert.equal(serialized.includes(contact.email), false);
    });
    test.getState().messages.forEach(message => {
      assert.equal(serialized.includes(message.body), false);
    });
  });

  it('routes current analytics insights and shares only after the documented explicit actions', async () => {
    const test = fixture();
    let sharedSummary = '';
    let sharedCsv = '';
    test.dependencies.shareAnalyticsSummary = async summary => {
      sharedSummary = summary.body;
      return 'shared';
    };
    test.dependencies.shareAnalyticsCsv = async csv => {
      sharedCsv = csv;
      return { opened: true, temporaryFileRemoved: true };
    };

    const inspection = await test.runtime.execute({ type: 'analytics.inspect', range: 'All time' });
    const insight =
      inspection.status === 'succeeded' && inspection.value.kind === 'analytics-inspection'
        ? inspection.value.insights[0]
        : undefined;
    assert.ok(insight);
    const opened = await test.runtime.execute({
      type: 'analytics.open-action',
      range: 'All time',
      insightId: insight?.id ?? 'missing'
    });
    const summary = await test.runtime.execute({ type: 'analytics.share-summary', range: 'All time' });
    const preview = await test.runtime.execute({ type: 'analytics.export-preview', range: 'All time' });
    const previewValue =
      preview.status === 'succeeded' && preview.value.kind === 'analytics-export-preview' ? preview.value : undefined;
    const exported = await test.runtime.execute({
      type: 'analytics.export-confirm',
      confirmationToken: previewValue?.confirmationToken ?? 'missing'
    });

    assert.equal(opened.status, 'succeeded');
    assert.equal(summary.status, 'succeeded');
    assert.equal(
      summary.status === 'succeeded' && summary.value.kind === 'analytics-summary-share'
        ? summary.value.outcome
        : undefined,
      'shared'
    );
    assert.equal(exported.status, 'succeeded');
    assert.equal(
      exported.status === 'succeeded' && exported.value.kind === 'analytics-export'
        ? exported.value.rowCount
        : undefined,
      previewValue?.rowCount
    );
    assert.match(sharedSummary, /RelateAI relationship summary/);
    assert.match(sharedCsv, /^Section,Name,Value,Detail/m);
    assert.ok(test.actions.filter(action => action.type === 'analyticsExported').length >= 2);
    assert.doesNotMatch(JSON.stringify([summary, preview, exported]), /Asha Mehra|Mira Kapoor|Rajesh Nair/);

    const stalePreview = await test.runtime.execute({ type: 'analytics.export-preview', range: 'This year' });
    const staleToken =
      stalePreview.status === 'succeeded' && stalePreview.value.kind === 'analytics-export-preview'
        ? stalePreview.value.confirmationToken
        : 'missing';
    test.setState({ ...test.getState(), searchQuery: 'changed after preview' });
    const stale = await test.runtime.execute({
      type: 'analytics.export-confirm',
      confirmationToken: staleToken
    });
    assert.equal(stale.status, 'failed');
    assert.equal(stale.status === 'failed' && stale.error.code, 'analytics-export-confirmation-stale');
  });

  it('filters and sorts contact, month-event, and message-inbox reads with actionable private rows', async () => {
    const state = createTestState();
    state.contacts = state.contacts.map(contact =>
      contact.id === 'c-mira'
        ? {
            ...contact,
            healthScore: 99,
            lastContactedAt: '2025-01-01T00:00:00.000Z',
            routes: [
              {
                id: 'route-mira-private-email',
                type: 'Email',
                value: 'mira.private@example.com',
                primary: false,
                verified: true
              }
            ]
          }
        : contact
    );
    const yearlyMonth = state.events[0].date.slice(5, 7);
    state.events.push({
      id: 'event-old-one-time-custom',
      contactId: 'c-asha',
      type: 'Custom',
      label: 'One-time event from another year',
      date: `2029-${yearlyMonth}-20T12:00:00.000Z`,
      verified: true,
      source: 'Manual',
      checklist: []
    });
    const test = fixture(state);
    const contacts = await test.runtime.execute({
      type: 'contacts.query',
      query: 'MIRA.PRIVATE',
      lowHealth: true,
      sort: 'Health'
    });
    const month = `2030-${yearlyMonth}`;
    const events = await test.runtime.execute({
      type: 'events.query',
      month,
      sort: 'Contact'
    });
    const messages = await test.runtime.execute({
      type: 'messages.query',
      tab: 'Review',
      channel: 'Manual',
      query: 'Pune',
      sort: 'Status'
    });

    const contactPage =
      contacts.status === 'succeeded' && contacts.value.kind === 'contacts-page' ? contacts.value : undefined;
    const eventPage = events.status === 'succeeded' && events.value.kind === 'events-page' ? events.value : undefined;
    const messagePage =
      messages.status === 'succeeded' && messages.value.kind === 'messages-page' ? messages.value : undefined;
    assert.deepEqual(
      contactPage?.items.map(item => item.id),
      ['c-mira']
    );
    assert.ok(contactPage?.items[0]?.qualityLabels.includes('Low health'));
    assert.notEqual(contactPage?.items[0]?.healthScore, 99);
    assert.equal(contactPage?.items[0]?.relationship, 'College friend');
    assert.equal(eventPage?.items[0]?.contactName, 'Asha Mehra');
    assert.equal(eventPage?.items[0]?.eventType, 'Birthday');
    assert.equal(eventPage?.items[0]?.date.slice(0, 7), month);
    assert.equal(
      eventPage?.items.some(item => item.id === 'event-old-one-time-custom'),
      false
    );
    assert.deepEqual(
      messagePage?.items.map(item => item.id),
      ['msg-mira-checkin']
    );
    assert.equal(messagePage?.items[0]?.contactName, 'Mira Shah');
    assert.equal(messagePage?.items[0]?.eventLabel, 'Check in after move');
  });

  it('keeps every new private inspection locked, then exposes bounded detail without logging its content', async () => {
    const state = createTestState();
    state.settings.biometricLockEnabled = true;
    const test = fixture(state);
    const lockedCommands = [
      { type: 'contacts.inspect', contactId: 'c-asha' },
      { type: 'memories.query', contactId: 'c-asha', query: '' },
      { type: 'timeline.query', contactId: 'c-asha', filter: 'All' },
      { type: 'templates.inspect', contactId: 'c-asha', reason: 'Birthday' },
      { type: 'privacy.inspect' }
    ];
    for (const command of lockedCommands) {
      assert.equal((await test.runtime.execute(command)).status, 'locked');
    }

    assert.equal((await test.runtime.execute({ type: 'biometric.unlock' })).status, 'succeeded');
    const detail = await test.runtime.execute({ type: 'contacts.inspect', contactId: 'c-asha' });
    const memories = await test.runtime.execute({ type: 'memories.query', contactId: 'c-asha', query: 'mango' });
    assert.equal(detail.status, 'succeeded');
    assert.match(JSON.stringify(detail), /Asha Mehra/);
    assert.match(JSON.stringify(memories), /mango lassi/i);
    assert.doesNotMatch(JSON.stringify(test.runtime.operationSnapshots()), /Asha Mehra|mango lassi/i);
  });

  it('executes preferences, enrichment, preparation, Memory Vault, gifts, settings, onboarding, and Home actions', async () => {
    const state = createTestState();
    state.contacts = state.contacts.map(contact =>
      contact.id === 'c-mira' ? { ...contact, preferenceOverrides: { tone: ['Warm'] } } : contact
    );
    const test = fixture(state);

    assert.equal(
      (
        await test.runtime.execute({
          type: 'contacts.preferences.set-tone',
          contactId: 'c-mira',
          tone: 'Concise',
          enabled: true
        })
      ).status,
      'succeeded'
    );
    await test.runtime.execute({
      type: 'contacts.preferences.set-cadence',
      contactId: 'c-mira',
      days: 30
    });
    await test.runtime.execute({
      type: 'groups.set-default',
      group: 'Close friends',
      defaults: { preferredChannel: 'Manual', tone: ['Warm', 'Playful'], automationMode: 'Always ask' }
    });
    await test.runtime.execute({ type: 'contacts.preferences.use-group-defaults', contactId: 'c-mira' });

    const enrichmentBefore = await test.runtime.execute({
      type: 'contacts.enrichment.inspect',
      contactId: 'c-mira'
    });
    const enrichmentData =
      enrichmentBefore.status === 'succeeded' && enrichmentBefore.value.kind === 'feature-inspection'
        ? enrichmentBefore.value.data
        : {};
    const promptId = Array.isArray(enrichmentData.prompts)
      ? (enrichmentData.prompts[0] as { id?: string } | undefined)?.id
      : undefined;
    if (promptId) {
      const answer = await test.runtime.execute({
        type: 'contacts.enrichment.answer',
        contactId: 'c-mira',
        promptId,
        body: 'Use a specific memory about the new design role.'
      });
      assert.equal(answer.status, 'succeeded');
    }

    const preparation = await test.runtime.execute({
      type: 'events.preparation.toggle',
      eventId: 'e-mira-checkin',
      stepId: 'write-message'
    });
    assert.equal(preparation.status, 'succeeded');

    const addedMemory = await test.runtime.execute({
      type: 'memories.add',
      contactId: 'c-mira',
      category: 'Preference',
      body: 'Prefers short check-ins on weekday evenings.'
    });
    const memoryId =
      addedMemory.status === 'succeeded' &&
      addedMemory.value.kind === 'feature-action' &&
      addedMemory.value.feature === 'memory'
        ? addedMemory.value.createdId
        : undefined;
    assert.ok(memoryId);
    await test.runtime.execute({
      type: 'memories.edit',
      memoryId,
      category: 'Preference',
      body: 'Prefers concise check-ins on weekday evenings.'
    });
    await test.runtime.execute({ type: 'memories.set-pinned', memoryId, pinned: false });
    const memoryPage = await test.runtime.execute({ type: 'memories.query', contactId: 'c-mira', query: 'concise' });
    assert.match(JSON.stringify(memoryPage), /concise check-ins/i);
    assert.equal(
      (await test.runtime.execute({ type: 'memories.delete', memoryId, confirmation: 'DELETE MEMORY' })).status,
      'succeeded'
    );

    const addedGift = await test.runtime.execute({
      type: 'gifts.add',
      contactId: 'c-mira',
      name: 'Local bookstore voucher',
      category: 'Books',
      occasion: 'Birthday',
      cost: 1200,
      feedback: 'Unknown',
      notes: 'Confirm the preferred bookstore first.'
    });
    const giftId =
      addedGift.status === 'succeeded' && addedGift.value.kind === 'feature-action'
        ? addedGift.value.createdId
        : undefined;
    assert.ok(giftId);
    await test.runtime.execute({ type: 'gifts.set-budget', contactId: 'c-mira', annualGiftBudget: 7000 });
    const giftPage = await test.runtime.execute({ type: 'gifts.inspect', contactId: 'c-mira', occasion: 'Birthday' });
    assert.match(JSON.stringify(giftPage), /Local bookstore voucher/);
    await test.runtime.execute({ type: 'gifts.delete', giftId, confirmation: 'DELETE GIFT' });

    await test.runtime.execute({ type: 'privacy.set-whatsapp-consent', enabled: true });
    await test.runtime.execute({ type: 'settings.set-default-send-time', time: '10:30' });
    await test.runtime.execute({ type: 'settings.set-automation', mode: 'VIP approve' });
    await test.runtime.execute({ type: 'onboarding.set-goal', goal: 'Manual relationship manager' });
    await test.runtime.execute({ type: 'onboarding.advance' });
    const home = await test.runtime.execute({ type: 'home.inspect' });
    const homeActionId =
      home.status === 'succeeded' && home.value.kind === 'home-inspection' ? home.value.actions[0]?.id : undefined;
    assert.ok(homeActionId);
    assert.equal(
      (await test.runtime.execute({ type: 'home.open-action', actionId: homeActionId })).status,
      'succeeded'
    );

    assert.ok(test.getState().contacts.find(contact => contact.id === 'c-mira')?.tone.length);
    assert.equal(test.getState().contacts.find(contact => contact.id === 'c-mira')?.checkInCadenceDays, 30);
    assert.equal(test.getState().contacts.find(contact => contact.id === 'c-mira')?.annualGiftBudget, 7000);
    assert.equal(test.getState().privacy.whatsappHandoffConsent, true);
    assert.equal(test.getState().settings.defaultSendTime, '10:30');
    assert.equal(test.getState().settings.automationMode, 'VIP approve');
  });

  it('blocks required onboarding skips and requires goal-specific minimum setup before completion', async () => {
    const missingContactState = createTestState();
    missingContactState.contacts = [];
    missingContactState.onboarding = {
      ...missingContactState.onboarding,
      currentStepId: 'contacts',
      completedStepIds: ['account']
    };
    const missingContact = fixture(missingContactState);
    const skippedRequired = await missingContact.runtime.execute({
      type: 'onboarding.skip',
      stepId: 'contacts'
    });
    const jumpedForward = await missingContact.runtime.execute({
      type: 'onboarding.set-step',
      stepId: 'finish'
    });
    const blockedInspection = await missingContact.runtime.execute({ type: 'onboarding.inspect' });

    assert.equal(skippedRequired.status, 'failed');
    assert.equal(
      skippedRequired.status === 'failed' ? skippedRequired.error.code : undefined,
      'onboarding-transition-blocked'
    );
    assert.equal(jumpedForward.status, 'failed');
    assert.equal(missingContact.getState().onboarding.currentStepId, 'contacts');
    assert.match(JSON.stringify(blockedInspection), /"canComplete":false/);
    assert.match(JSON.stringify(blockedInspection), /Add at least one contact/);

    const manualState = createTestState();
    manualState.onboarding = {
      ...manualState.onboarding,
      selectedGoal: 'Manual relationship manager',
      currentStepId: 'finish',
      completedStepIds: ['account', 'contacts', 'channels']
    };
    const manual = fixture(manualState);
    const manualCompletion = await manual.runtime.execute({ type: 'onboarding.complete' });
    assert.equal(manualCompletion.status, 'succeeded');
    assert.equal(manual.getState().onboarding.completed, true);
    assert.equal(manual.getState().activeScreen, 'home');

    const aiState = createTestState();
    aiState.aiProvider = { status: 'Ready' };
    aiState.onboarding = {
      ...aiState.onboarding,
      selectedGoal: 'AI wishes',
      currentStepId: 'finish',
      completedStepIds: ['account', 'contacts', 'ai']
    };
    const ai = fixture(aiState);
    const missingReleaseProvider = await ai.runtime.execute({ type: 'onboarding.complete' });
    assert.equal(missingReleaseProvider.status, 'failed');
    assert.equal(
      missingReleaseProvider.status === 'failed' ? missingReleaseProvider.error.code : undefined,
      'onboarding-required-setup'
    );
    assert.match(
      missingReleaseProvider.status === 'failed' ? missingReleaseProvider.error.summary : '',
      /AI provider endpoint/
    );
    ai.dependencies.setupEnvironment = () => ({
      aiEndpointReadiness: evaluateProviderEndpointReadiness('https://ai.example.test/draft'),
      aiProviderSessionReady: true,
      emailEndpointConfigured: false
    });
    const aiCompletion = await ai.runtime.execute({ type: 'onboarding.complete' });
    assert.equal(aiCompletion.status, 'succeeded');
    assert.equal(ai.getState().onboarding.completed, true);
  });

  it('changes locale and sender settings with validation, queued-email recovery, and redacted inspection', async () => {
    const state = createTestState();
    state.settings.emailEnabled = true;
    state.emailDelivery = {
      status: 'Ready',
      senderEmail: 'old.sender@example.com',
      lastCheckedAt: now.toISOString()
    };
    state.messages.unshift(
      approvedMessage(state.messages[0], {
        id: 'settings-queued-email',
        contactId: 'c-rajesh',
        eventId: undefined,
        channel: 'Email',
        body: 'A review-safe provider email queued before its sender configuration changes.'
      })
    );
    const test = fixture(state);

    const beforeInspection = await test.runtime.execute({ type: 'settings.inspect' });
    const beforeInvalid = JSON.stringify(test.getState());
    const invalid = await test.runtime.execute({
      type: 'settings.set-email-sender',
      senderEmail: 'not-an-email'
    });
    const afterInvalid = JSON.stringify(test.getState());
    const locale = await test.runtime.execute({ type: 'settings.set-locale', locale: 'en-Hinglish' });
    const sender = await test.runtime.execute({
      type: 'settings.set-email-sender',
      senderEmail: ' NEW.SENDER@EXAMPLE.COM '
    });
    const configuredInspection = await test.runtime.execute({ type: 'settings.inspect' });
    const queued = test.getState().messages.find(message => message.id === 'settings-queued-email');

    assert.match(JSON.stringify(beforeInspection), /"senderConfigured":true/);
    assert.match(JSON.stringify(beforeInspection), /"ready":true/);
    assert.doesNotMatch(JSON.stringify(beforeInspection), /old\.sender@example\.com/i);
    assert.equal(invalid.status, 'invalid');
    assert.equal(afterInvalid, beforeInvalid);
    assert.equal(locale.status, 'succeeded');
    assert.equal(test.getState().settings.locale, 'en-Hinglish');
    assert.equal(sender.status, 'succeeded');
    assert.equal(sender.status === 'succeeded' ? sender.value.kind : undefined, 'feature-action');
    assert.equal(
      sender.status === 'succeeded' && sender.value.kind === 'feature-action' ? sender.value.action : undefined,
      'set-email-sender'
    );
    assert.equal(
      sender.status === 'succeeded' && sender.value.kind === 'feature-action' ? sender.value.outcome : undefined,
      'applied'
    );
    assert.equal(test.getState().emailDelivery.senderEmail, 'new.sender@example.com');
    assert.equal(queued?.status, 'Needs review');
    assert.equal(queued?.approvedAt, undefined);
    assert.match(queued?.lastError ?? '', /sender configuration changed/i);
    assert.doesNotMatch(JSON.stringify(sender), /new\.sender@example\.com/i);
    assert.doesNotMatch(JSON.stringify(configuredInspection), /new\.sender@example\.com/i);
    assert.match(JSON.stringify(configuredInspection), /"senderConfigured":true/);

    const clear = await test.runtime.execute({ type: 'settings.set-email-sender', senderEmail: '' });
    const clearedInspection = await test.runtime.execute({ type: 'settings.inspect' });
    assert.equal(clear.status, 'succeeded');
    assert.equal(test.getState().emailDelivery.senderEmail, undefined);
    assert.equal(test.getState().emailDelivery.status, 'Not configured');
    assert.match(JSON.stringify(clearedInspection), /"senderConfigured":false/);
    assert.match(JSON.stringify(clearedInspection), /"ready":false/);
  });

  it('previews privacy and tone, regenerates with bounded feedback, and serves templates, timeline, and chat history', async () => {
    const state = createTestState();
    state.memories.push({
      id: 'memory-private-regeneration',
      contactId: 'c-asha',
      category: 'Private',
      body: 'NEVER EXPOSE THIS PRIVATE REGENERATION NOTE',
      pinned: false,
      createdAt: now.toISOString()
    });
    state.messages.push(
      approvedMessage(state.messages[0], {
        id: 'message-sent-history',
        eventId: undefined,
        status: 'Sent',
        sentAt: '2026-07-09T10:00:00.000Z',
        body: 'A previous sent birthday message for timeline and chat history.'
      })
    );
    const test = fixture(state);
    let capturedRequest: Parameters<CommandRuntimeDependencies['requestAiDraft']>[0] | undefined;
    test.dependencies.requestAiDraft = async request => {
      capturedRequest = request;
      return {
        ok: true,
        variants: {
          short: 'A shorter regenerated birthday message.',
          standard: 'A specific regenerated birthday message for careful review.',
          warm: 'A warmer regenerated birthday message that remains review first.'
        }
      };
    };

    const preview = await test.runtime.execute({
      type: 'messages.preview',
      messageId: 'msg-asha-bday',
      excludedMemoryIds: ['m-asha-1'],
      includePriorMessages: false
    });
    assert.equal(preview.status, 'succeeded');
    assert.match(JSON.stringify(preview), /effectiveTone|excludedPrivateMemoryCount/);
    assert.doesNotMatch(JSON.stringify(preview), /NEVER EXPOSE/);

    const regenerated = await test.runtime.execute({
      type: 'messages.regenerate',
      messageId: 'msg-asha-bday',
      instructions: ['Make it shorter', 'Make it more specific'],
      customInstruction: 'Keep the family tone respectful.',
      excludedMemoryIds: ['m-asha-1'],
      includePriorMessages: false
    });
    assert.equal(regenerated.status, 'succeeded');
    assert.deepEqual(capturedRequest?.regenerationFeedback?.instructions, ['Make it shorter', 'Make it more specific']);
    assert.equal(capturedRequest?.memories.length, 0);
    assert.doesNotMatch(JSON.stringify(capturedRequest), /NEVER EXPOSE/);
    const regeneratedId =
      regenerated.status === 'succeeded' && regenerated.value.kind === 'ai-draft'
        ? regenerated.value.createdMessageId
        : undefined;
    const regeneratedMessage = test.getState().messages.find(message => message.id === regeneratedId);
    assert.ok(regeneratedId);
    assert.equal(test.getState().messages.find(message => message.id === 'msg-asha-bday')?.status, 'Rejected');
    assert.equal(regeneratedMessage?.status, 'Needs review');
    assert.equal(regeneratedMessage?.duplicateWarning, undefined);
    assert.equal(regeneratedMessage?.duplicateAcknowledged, undefined);
    assert.deepEqual(regeneratedMessage?.regenerationFeedback?.instructions, [
      'Make it shorter',
      'Make it more specific'
    ]);

    const templates = await test.runtime.execute({
      type: 'templates.inspect',
      contactId: 'c-asha',
      reason: 'Birthday',
      tone: 'Warm'
    });
    const timeline = await test.runtime.execute({ type: 'timeline.query', contactId: 'c-asha', filter: 'Messages' });
    const chat = await test.runtime.execute({
      type: 'chat.query',
      contactId: 'c-asha',
      query: 'previous sent',
      channel: 'SMS'
    });
    assert.match(JSON.stringify(templates), /Hinglish birthday wish/);
    if (templates.status === 'succeeded' && templates.value.kind === 'feature-inspection') {
      const data = templates.value.data as {
        templates?: { language?: string }[];
        templateSelection?: { exactLanguageMatch?: boolean; languageTarget?: string };
      };
      assert.equal(data.templates?.[0]?.language, 'Hinglish');
      assert.equal(data.templateSelection?.languageTarget, 'Hinglish');
      assert.equal(data.templateSelection?.exactLanguageMatch, true);
    }
    assert.match(JSON.stringify(timeline), /message-sent-history/);
    assert.match(JSON.stringify(chat), /previous sent birthday/i);
  });

  it('atomically supersedes regeneration sources for provider fallback and rejects stale provider results', async () => {
    const fallback = fixture();
    fallback.dependencies.requestAiDraft = async () => ({
      ok: false,
      error: { kind: 'network', message: 'The provider is temporarily unavailable.' }
    });
    const fallbackResult = await fallback.runtime.execute({
      type: 'messages.regenerate',
      messageId: 'msg-asha-bday',
      instructions: ['Make it warmer'],
      customInstruction: 'Keep the opening simple.'
    });
    const fallbackId =
      fallbackResult.status === 'succeeded' && fallbackResult.value.kind === 'ai-draft'
        ? fallbackResult.value.createdMessageId
        : undefined;
    const fallbackDraft = fallback.getState().messages.find(message => message.id === fallbackId);
    assert.equal(fallbackResult.status, 'succeeded');
    assert.equal(
      fallbackResult.status === 'succeeded' && fallbackResult.value.kind === 'ai-draft' && fallbackResult.value.source,
      'local-template-fallback'
    );
    assert.equal(fallback.getState().messages.find(message => message.id === 'msg-asha-bday')?.status, 'Rejected');
    assert.equal(fallbackDraft?.status, 'Needs review');
    assert.equal(fallbackDraft?.quality, 'Template fallback');
    assert.equal(fallbackDraft?.duplicateWarning, undefined);
    assert.deepEqual(fallbackDraft?.regenerationFeedback?.instructions, ['Make it warmer']);
    assert.doesNotMatch(JSON.stringify(fallback.getState().activity.slice(0, 2)), /opening simple/i);

    const stale = fixture();
    let finishProvider!: (value: Awaited<ReturnType<CommandRuntimeDependencies['requestAiDraft']>>) => void;
    stale.dependencies.requestAiDraft = () =>
      new Promise(resolve => {
        finishProvider = resolve;
      });
    const execution = stale.runtime.execute({
      type: 'messages.regenerate',
      messageId: 'msg-asha-bday',
      instructions: ['Make it shorter']
    });
    await Promise.resolve();
    assert.ok(finishProvider);
    const concurrentlyEdited = structuredClone(stale.getState());
    concurrentlyEdited.messages = concurrentlyEdited.messages.map(message =>
      message.id === 'msg-asha-bday' ? { ...message, body: `${message.body} Concurrent user edit.` } : message
    );
    stale.setState(concurrentlyEdited);
    finishProvider({
      ok: true,
      variants: {
        short: 'A stale short regeneration result.',
        standard: 'A stale standard regeneration result that must not be stored.',
        warm: 'A stale warm regeneration result that must not replace a concurrent user edit.'
      }
    });
    const staleResult = await execution;

    assert.equal(staleResult.status, 'failed');
    assert.equal(staleResult.status === 'failed' && staleResult.error.code, 'message-regeneration-stale');
    assert.equal(stale.getState().messages.length, concurrentlyEdited.messages.length);
    assert.match(
      stale.getState().messages.find(message => message.id === 'msg-asha-bday')?.body ?? '',
      /Concurrent user edit/
    );
    assert.notEqual(stale.getState().messages.find(message => message.id === 'msg-asha-bday')?.status, 'Rejected');
  });

  it('blocks archived and stale feature mutations and fails closed when a private page exceeds aggregate bounds', async () => {
    const state = createTestState();
    state.contacts = state.contacts.map(contact =>
      contact.id === 'c-asha' ? { ...contact, archivedAt: '2026-07-01T00:00:00.000Z' } : contact
    );
    state.activity = Array.from({ length: 100 }, (_, index) => ({
      id: `activity-bounded-${index}`,
      type: 'Message' as const,
      title: `Bounded activity ${index}`,
      detail: 'x'.repeat(1000),
      severity: 'Info' as const,
      createdAt: new Date(now.getTime() - index * 1000).toISOString()
    }));
    const test = fixture(state);
    const before = structuredClone(test.getState());
    const blockedCommands = [
      { type: 'contacts.preferences.set-vip', contactId: 'c-asha', enabled: false },
      { type: 'contacts.enrichment.answer', contactId: 'c-asha', promptId: 'message-mention', body: 'Blocked.' },
      { type: 'events.preparation.toggle', eventId: 'e-asha-bday', stepId: 'write-message' },
      { type: 'memories.add', contactId: 'c-asha', category: 'General', body: 'Blocked memory.' },
      {
        type: 'gifts.add',
        contactId: 'c-asha',
        name: 'Blocked gift',
        category: 'Other',
        occasion: 'Birthday',
        cost: 100,
        feedback: 'Unknown',
        notes: ''
      }
    ];
    for (const command of blockedCommands) {
      const result = await test.runtime.execute(command);
      assert.equal(result.status, 'succeeded');
      assert.equal(
        result.status === 'succeeded' && result.value.kind === 'feature-action' ? result.value.outcome : undefined,
        'blocked'
      );
    }
    assert.deepEqual(test.getState().contacts, before.contacts);
    assert.deepEqual(test.getState().memories, before.memories);
    assert.deepEqual(test.getState().gifts, before.gifts);

    const stale = await test.runtime.execute({
      type: 'messages.preview',
      messageId: 'msg-mira-checkin',
      excludedMemoryIds: ['missing-memory'],
      includePriorMessages: true
    });
    assert.equal(stale.status, 'failed');
    assert.equal(stale.status === 'failed' && stale.error.code, 'message-context-stale');

    const oversized = await test.runtime.execute({ type: 'activity.query', limit: 100 });
    assert.equal(oversized.status, 'failed');
    assert.equal(oversized.status === 'failed' && oversized.error.code, 'feature-output-too-large');
    assert.doesNotMatch(JSON.stringify(test.runtime.operationSnapshots()), /x{20}/);
  });

  it('searches Activity History by bounded date filters and opens only revalidated navigation targets', async () => {
    const state = createTestState();
    state.activity = [
      {
        id: 'activity-provider-today',
        type: 'Message',
        title: 'Provider recovery needed',
        detail: 'Review the current message route.',
        severity: 'Warning',
        createdAt: now.toISOString(),
        targetScreen: 'wishPreview',
        contactId: 'c-mira',
        messageId: 'msg-mira-checkin',
        actionLabel: 'Review message'
      },
      {
        id: 'activity-provider-old',
        type: 'Message',
        title: 'Provider recovery from last month',
        detail: 'Old recovery detail.',
        severity: 'Warning',
        createdAt: '2026-06-01T10:00:00.000Z'
      },
      {
        id: 'activity-stale-target',
        type: 'Message',
        title: 'Stale target',
        detail: 'The linked message disappeared.',
        severity: 'Error',
        createdAt: now.toISOString(),
        targetScreen: 'wishPreview',
        contactId: 'c-mira',
        messageId: 'missing-message',
        actionLabel: 'Review missing message'
      }
    ];
    const test = fixture(state);
    const filtered = await test.runtime.execute({
      type: 'activity.query',
      query: 'provider recovery',
      activityType: 'Message',
      severity: 'Warning',
      status: 'Open',
      date: 'Today'
    });
    const page = filtered.status === 'succeeded' && filtered.value.kind === 'feature-page' ? filtered.value : undefined;
    assert.deepEqual(
      page?.items.map(item => item.id),
      ['activity-provider-today']
    );
    assert.equal(page?.summary?.date, 'Today');
    assert.equal(page?.summary?.status, 'Open');
    assert.equal(page?.items[0]?.status, 'Open');

    const resolved = await test.runtime.execute({
      type: 'activity.resolve',
      activityId: 'activity-provider-today'
    });
    assert.equal(resolved.status, 'succeeded');
    assert.equal(test.getState().activity.find(item => item.id === 'activity-provider-today')?.status, 'Resolved');
    assert.equal(test.getState().activity[0].status, 'Completed');
    assert.match(test.getState().activity[0].title, /activity issue resolved/i);

    const repeatedResolution = await test.runtime.execute({
      type: 'activity.resolve',
      activityId: 'activity-provider-today'
    });
    assert.equal(repeatedResolution.status, 'failed');
    assert.equal(repeatedResolution.status === 'failed' && repeatedResolution.error.code, 'activity-not-open');

    const obsoleteResolution = await test.runtime.execute({
      type: 'activity.resolve',
      activityId: 'activity-stale-target'
    });
    assert.equal(obsoleteResolution.status, 'failed');
    assert.equal(obsoleteResolution.status === 'failed' && obsoleteResolution.error.code, 'activity-not-open');

    const target = await test.runtime.execute({
      type: 'activity.open-action',
      activityId: 'activity-provider-today'
    });
    assert.equal(
      target.status === 'succeeded' && target.value.kind === 'activity-navigation' ? target.value.outcome : undefined,
      'target'
    );
    assert.equal(test.getState().activeScreen, 'wishPreview');
    assert.equal(test.getState().selectedMessageId, 'msg-mira-checkin');

    const fallback = await test.runtime.execute({
      type: 'activity.open-action',
      activityId: 'activity-stale-target'
    });
    assert.equal(
      fallback.status === 'succeeded' && fallback.value.kind === 'activity-navigation'
        ? fallback.value.outcome
        : undefined,
      'fallback'
    );
    assert.equal(test.getState().activeScreen, 'messages');
    assert.equal(test.getState().selectedMessageId, undefined);
  });

  it('reports truthful local-mode/setup surfaces and trains only the current Style Coach profile', async () => {
    const test = fixture();
    const account = await test.runtime.execute({ type: 'account.inspect' });
    const onboarding = await test.runtime.execute({ type: 'onboarding.inspect' });
    const wizard = await test.runtime.execute({ type: 'setup.wizard.inspect', goal: 'AI drafts' });
    const privacy = await test.runtime.execute({ type: 'privacy.inspect' });
    const settings = await test.runtime.execute({ type: 'settings.inspect' });
    const disconnected = await test.runtime.execute({
      type: 'account.disconnect',
      confirmation: 'DISCONNECT ACCOUNT'
    });
    const trained = await test.runtime.execute({
      type: 'style.train-samples',
      samples:
        'Hi, I was thinking of you and hope your week is going well. No rush to reply.\n\nCongratulations on the meaningful milestone. I sincerely appreciate your effort and wish you continued success.\n\nHey yaar, bahut khushi hui. Hope aaj ka day feels special and warm.'
    });
    const disabled = await test.runtime.execute({ type: 'style.set-enabled', enabled: false });
    const style = await test.runtime.execute({ type: 'style.inspect' });
    const profileBeforeFailedTraining = structuredClone(test.getState().styleProfile);
    const failedTraining = await test.runtime.execute({ type: 'style.train-samples', samples: 'Too short.' });

    assert.match(JSON.stringify(account), /Google sync is not available/);
    assert.match(JSON.stringify(account), /"availableModes":\["Local"\]/);
    assert.equal(onboarding.status, 'succeeded');
    assert.equal(wizard.status, 'succeeded');
    assert.match(JSON.stringify(wizard), /AI provider endpoint/);
    assert.match(JSON.stringify(wizard), /Configure a secure backend endpoint/);
    assert.equal(privacy.status, 'succeeded');
    assert.equal(settings.status, 'succeeded');
    assert.equal(
      disconnected.status === 'succeeded' && disconnected.value.kind === 'feature-action'
        ? disconnected.value.outcome
        : undefined,
      'blocked'
    );
    assert.equal(trained.status, 'succeeded');
    assert.equal(disabled.status, 'succeeded');
    assert.equal(test.getState().styleProfile.enabledForAiDrafts, false);
    assert.match(JSON.stringify(style), /"sampleCount":3/);
    assert.match(JSON.stringify(style), /"commonGreetings":\["Hi","Hey"\]/);
    assert.match(JSON.stringify(style), /"representativePreview":/);
    assert.match(JSON.stringify(style), /"futureAiDraftUse":"Disabled"/);
    assert.doesNotMatch(JSON.stringify(style), /No rush to reply|bahut khushi/);
    if (style.status === 'succeeded' && style.value.kind === 'feature-inspection') {
      const exposedProfile = style.value.data.profile as { commonGreetings: string[] };
      exposedProfile.commonGreetings.push('Mutation attempt');
      assert.doesNotMatch(JSON.stringify(test.getState().styleProfile.commonGreetings), /Mutation attempt/);
    }
    assert.equal(failedTraining.status, 'failed');
    assert.equal(failedTraining.status === 'failed' ? failedTraining.error.retryable : false, true);
    assert.match(failedTraining.status === 'failed' ? failedTraining.error.summary : '', /at least two/i);
    assert.deepEqual(test.getState().styleProfile, profileBeforeFailedTraining);
  });

  it('records Setup Check cards and limits check actions to safe navigation or reminder reconciliation', async () => {
    const state = createTestState();
    state.privacy.permissionDecisions.Notifications = 'Granted';
    const test = fixture(state);
    const inspection = await test.runtime.execute({ type: 'setup.inspect' });
    const setup =
      inspection.status === 'succeeded' && inspection.value.kind === 'setup-inspection' ? inspection.value : undefined;
    const reminderCheck = setup?.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'reminders');
    assert.equal(reminderCheck?.command, 'planReminders');
    const planned = await test.runtime.execute({ type: 'setup.open-action', checkId: 'reminders' });
    assert.equal(
      planned.status === 'succeeded' && planned.value.kind === 'setup-action' ? planned.value.outcome : undefined,
      'reminders-reconciled'
    );
    assert.equal(
      test.actions.some(action => action.type === 'setupDoctorDryRunRecorded'),
      true
    );
    assert.equal(
      test.actions.some(action => action.type === 'reminderPlansReconciled'),
      true
    );
    const wizardPlanned = await test.runtime.execute({
      type: 'setup.wizard.run-action',
      goal: 'Reminders only',
      stepId: 'reminder-plans'
    });
    assert.equal(
      wizardPlanned.status === 'succeeded' && wizardPlanned.value.kind === 'setup-action'
        ? wizardPlanned.value.outcome
        : undefined,
      'reminders-reconciled'
    );

    for (const [checkId, targetScreen] of [
      ['style-profile', 'styleCoach'],
      ['backup-freshness', 'backup'],
      ['recent-warnings', 'activityHistory'],
      ['privacy-controls', 'settings']
    ] as const) {
      const opened = await test.runtime.execute({ type: 'setup.open-action', checkId });
      assert.equal(opened.status, 'succeeded');
      assert.equal(test.getState().activeScreen, targetScreen);
    }

    const stale = await test.runtime.execute({ type: 'setup.open-action', checkId: 'missing-check' });
    assert.equal(
      stale.status === 'succeeded' && stale.value.kind === 'setup-action' ? stale.value.outcome : undefined,
      'fallback'
    );
    assert.equal(test.getState().activeScreen, 'more');
  });

  it('executes wizard recommendations and proves AI readiness with synthetic context only', async () => {
    const state = createTestState();
    state.aiProvider = { status: 'Not configured' };
    state.privacy.permissionDecisions.Notifications = 'Granted';
    const test = fixture(state);
    test.dependencies.setupEnvironment = () => ({
      aiEndpointReadiness: evaluateProviderEndpointReadiness('https://ai.example.test/draft'),
      emailEndpointConfigured: false,
      aiProviderSessionReady: true,
      emailProviderSessionReady: false
    });
    let capturedRequest: Parameters<CommandRuntimeDependencies['requestAiDraft']>[0] | undefined;
    test.dependencies.requestAiDraft = async request => {
      capturedRequest = request;
      return {
        ok: true,
        variants: {
          short: 'A short provider readiness response.',
          standard: 'A standard provider readiness response for contract verification.',
          warm: 'A warm provider readiness response that verifies the provider contract.'
        }
      };
    };
    const beforeMessageIds = test.getState().messages.map(message => message.id);
    const inspection = await test.runtime.execute({ type: 'setup.wizard.inspect', goal: 'AI drafts' });
    const recommended =
      inspection.status === 'succeeded' && inspection.value.kind === 'feature-inspection'
        ? inspection.value.data.recommendedStep
        : undefined;
    assert.deepEqual((recommended as { runCommand?: unknown })?.runCommand, {
      type: 'setup.wizard.run-action',
      goal: 'AI drafts',
      stepId: 'ai-provider'
    });

    const tested = await test.runtime.execute({
      type: 'setup.wizard.run-action',
      goal: 'AI drafts',
      stepId: 'ai-provider'
    });
    assert.equal(
      tested.status === 'succeeded' && tested.value.kind === 'setup-action' ? tested.value.outcome : undefined,
      'ai-provider-ready'
    );
    assert.deepEqual(
      test.getState().messages.map(message => message.id),
      beforeMessageIds
    );
    assert.equal(test.getState().aiProvider.status, 'Ready');
    assert.ok(capturedRequest);
    assert.equal(capturedRequest.memories.length, 0);
    assert.equal(capturedRequest.giftHistory.length, 0);
    assert.equal(capturedRequest.priorApprovedMessages.length, 0);
    assert.equal(capturedRequest.contact.notesSummary, '');
    const serializedRequest = JSON.stringify(capturedRequest);
    state.contacts.forEach(contact => {
      assert.equal(serializedRequest.includes(contact.name), false);
      if (contact.phone) assert.equal(serializedRequest.includes(contact.phone), false);
      if (contact.email) assert.equal(serializedRequest.includes(contact.email), false);
    });
    state.memories.forEach(memory => assert.equal(serializedRequest.includes(memory.body), false));

    const refreshed = await test.runtime.execute({ type: 'setup.wizard.inspect', goal: 'AI drafts' });
    const refreshedData =
      refreshed.status === 'succeeded' && refreshed.value.kind === 'feature-inspection'
        ? refreshed.value.data
        : undefined;
    const providerStep = (refreshedData?.steps as { id: string; status: string }[] | undefined)?.find(
      step => step.id === 'ai-provider'
    );
    assert.equal(providerStep?.status, 'Ready');
  });

  it('reports a redacted actionable AI test failure without creating a draft', async () => {
    const test = fixture();
    test.dependencies.setupEnvironment = () => ({
      aiEndpointReadiness: evaluateProviderEndpointReadiness('https://ai.example.test/draft'),
      aiProviderSessionReady: true,
      emailEndpointConfigured: false,
      emailProviderSessionReady: false
    });
    test.dependencies.requestAiDraft = async () => ({
      ok: false,
      error: {
        kind: 'auth',
        message: 'Rejected https://private.example.test for secret.user@example.test.'
      }
    });
    const beforeMessageCount = test.getState().messages.length;

    const tested = await test.runtime.execute({ type: 'setup.open-action', checkId: 'ai-provider' });

    assert.equal(
      tested.status === 'succeeded' && tested.value.kind === 'setup-action' ? tested.value.outcome : undefined,
      'ai-provider-failed'
    );
    assert.equal(
      tested.status === 'succeeded' && tested.value.kind === 'setup-action'
        ? tested.value.aiTest?.errorKind
        : undefined,
      'auth'
    );
    assert.equal(test.getState().messages.length, beforeMessageCount);
    assert.equal(test.getState().aiProvider.status, 'Error');
    assert.doesNotMatch(JSON.stringify({ tested, state: test.getState().aiProvider }), /private\.example|secret\.user/);
    assert.match(test.getState().aiProvider.lastError ?? '', /session|reconnect/i);
  });

  it('keeps live setup blockers visible on Home after onboarding despite stale saved checks', async () => {
    const state = createTestState();
    state.onboarding.completed = true;
    state.setupChecks = state.setupChecks.map(check => ({ ...check, status: 'Ready' }));
    state.backups = [{ id: 'backup-current', createdAt: now.toISOString(), recordCount: 1, encrypted: true }];
    state.aiProvider = { status: 'Not configured' };
    const test = fixture(state);
    test.dependencies.setupEnvironment = () => ({
      aiEndpointReadiness: evaluateProviderEndpointReadiness('https://ai.example.test/draft'),
      aiProviderSessionReady: false,
      emailEndpointConfigured: false,
      emailProviderSessionReady: false
    });

    const home = await test.runtime.execute({ type: 'home.inspect' });
    const homeInspection =
      home.status === 'succeeded' && home.value.kind === 'home-inspection' ? home.value : undefined;
    const actions = homeInspection?.actions ?? [];
    assert.equal(
      actions.some(action => action.kind === 'complete-setup'),
      true
    );
    assert.equal(homeInspection?.setupNeedsAction, true);
    assert.equal(homeInspection?.onboardingCompleted, true);
    assert.equal(homeInspection?.backup.status, 'fresh');
    assert.ok((homeInspection?.metrics.activeContacts ?? 0) > 0);
    assert.ok((homeInspection?.metrics.upcomingEvents ?? 0) >= (homeInspection?.upcoming.length ?? 0));

    test.dependencies.setupEnvironment = () => {
      throw new Error('private provider-session adapter detail');
    };
    const unavailable = await test.runtime.execute({ type: 'home.inspect' });
    const unavailableActions =
      unavailable.status === 'succeeded' && unavailable.value.kind === 'home-inspection'
        ? unavailable.value.actions
        : [];
    assert.equal(
      unavailableActions.some(action => action.kind === 'complete-setup'),
      true
    );
    assert.doesNotMatch(JSON.stringify(unavailable), /provider-session adapter detail/i);
  });

  it('blocks duplicate and conflicting mutations while allowing read-only inspection', async () => {
    const test = fixture();
    let release!: (value: Awaited<ReturnType<CommandRuntimeDependencies['requestAiDraft']>>) => void;
    test.dependencies.requestAiDraft = () =>
      new Promise(resolve => {
        release = resolve;
      });

    const first = test.runtime.execute({ type: 'ai.draft', contactId: 'c-asha', reason: 'Birthday' });
    await Promise.resolve();
    const duplicate = await test.runtime.execute({ type: 'ai.draft', contactId: 'c-asha', reason: 'Birthday' });
    const conflict = await test.runtime.execute({ type: 'contacts.import' });
    const inspection = await test.runtime.execute({ type: 'analytics.inspect' });

    assert.equal(duplicate.status, 'already-running');
    assert.equal(conflict.status, 'conflict');
    assert.equal(inspection.status, 'succeeded');
    release({
      ok: true,
      variants: {
        short: 'A valid short birthday message.',
        standard: 'A valid standard birthday message for review.',
        warm: 'A valid warm birthday message that remains review first.'
      }
    });
    assert.equal((await first).status, 'succeeded');
  });

  it('exposes cancellation only for runtime-safe scopes and never exposes coordinator retry', async () => {
    const test = fixture();
    let release!: (value: Awaited<ReturnType<CommandRuntimeDependencies['requestAiDraft']>>) => void;
    test.dependencies.requestAiDraft = () =>
      new Promise(resolve => {
        release = resolve;
      });
    const execution = test.runtime.execute({ type: 'ai.draft', contactId: 'c-asha', reason: 'Birthday' });
    await Promise.resolve();
    const aiScope = test.runtime.operationSnapshots().find(operation => operation.scope.startsWith('ai:'))?.scope;
    assert.ok(aiScope);

    const unsafe = await test.runtime.execute({ type: 'operation.cancel', scope: 'email:unsafe' });
    const cancelled = await test.runtime.execute({ type: 'operation.cancel', scope: aiScope });
    release({
      ok: true,
      variants: {
        short: 'A valid short birthday message.',
        standard: 'A valid standard birthday message for review.',
        warm: 'A valid warm birthday message that remains review first.'
      }
    });

    assert.equal(
      unsafe.status === 'succeeded' && unsafe.value.kind === 'operation-cancellation' && unsafe.value.cancelled,
      false
    );
    assert.equal(
      cancelled.status === 'succeeded' &&
        cancelled.value.kind === 'operation-cancellation' &&
        cancelled.value.cancelled,
      true
    );
    assert.equal((await execution).status, 'cancelled');
    assert.equal(parseHarnessCommand({ type: 'operation.retry', scope: aiScope }).ok, false);
  });

  it('validates and executes complete contact detail and scheduling preferences', async () => {
    assert.equal(
      parseHarnessCommand({ type: 'contacts.preferences.set-send-time', contactId: 'c-mira', time: '18:45' }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({ type: 'contacts.preferences.set-send-time', contactId: 'c-mira', time: null }).ok,
      true
    );
    assert.equal(
      parseHarnessCommand({ type: 'contacts.preferences.set-send-time', contactId: 'c-mira', time: '25:00' }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({
        type: 'contacts.preferences.set-quiet-hours',
        contactId: 'c-mira',
        behavior: 'Allow'
      }).ok,
      false
    );
    assert.equal(
      parseHarnessCommand({ type: 'contacts.preferences.set-skip-auto', contactId: 'c-mira', enabled: true }).ok,
      true
    );

    const test = fixture();
    const mira = test.getState().contacts.find(contact => contact.id === 'c-mira')!;
    const input = {
      name: mira.name,
      relationship: mira.relationship,
      relationshipSubtype: 'Alumni mentor',
      jobTitle: 'Product design lead',
      phone: mira.phone,
      email: mira.email,
      language: mira.language,
      notesSummary: mira.notesSummary
    };
    const preview = await test.runtime.execute({
      type: 'contacts.edit-preview',
      contactId: mira.id,
      input
    });
    const confirmationToken =
      preview.status === 'succeeded' && preview.value.kind === 'contact-lifecycle-preview'
        ? preview.value.confirmationToken
        : '';
    assert.ok(confirmationToken);
    assert.equal(
      (
        await test.runtime.execute({
          type: 'contacts.edit-apply',
          contactId: mira.id,
          input,
          confirmationToken
        })
      ).status,
      'succeeded'
    );
    assert.equal(
      (await test.runtime.execute({ type: 'contacts.preferences.set-send-time', contactId: mira.id, time: '18:45' }))
        .status,
      'succeeded'
    );
    assert.equal(
      (
        await test.runtime.execute({
          type: 'contacts.preferences.set-quiet-hours',
          contactId: mira.id,
          behavior: 'Block'
        })
      ).status,
      'succeeded'
    );
    assert.equal(
      (
        await test.runtime.execute({
          type: 'contacts.preferences.set-skip-auto',
          contactId: mira.id,
          enabled: true
        })
      ).status,
      'succeeded'
    );

    const detail = await test.runtime.execute({ type: 'contacts.inspect', contactId: mira.id });
    const preferences = await test.runtime.execute({ type: 'contacts.preferences.inspect', contactId: mira.id });
    const detailData =
      detail.status === 'succeeded' && detail.value.kind === 'feature-inspection' ? detail.value.data : {};
    const preferenceData =
      preferences.status === 'succeeded' && preferences.value.kind === 'feature-inspection'
        ? preferences.value.data
        : {};
    assert.equal(detailData.relationshipSubtype, 'Alumni mentor');
    assert.equal(detailData.jobTitle, 'Product design lead');
    assert.equal(typeof detailData.relationshipHealth, 'object');
    assert.equal(detailData.healthScore, (detailData.relationshipHealth as { score?: number }).score);
    assert.equal(preferenceData.customSendTime, '18:45');
    assert.equal(preferenceData.effectiveSendTime, '18:45');
    assert.equal(preferenceData.quietHoursBehavior, 'Block');
    assert.equal(preferenceData.skipAuto, true);

    const beforeDraftCount = test.getState().messages.length;
    const requestedDraft = await test.runtime.execute({
      type: 'ai.draft',
      contactId: mira.id,
      eventId: 'e-mira-checkin',
      reason: 'Check-in'
    });
    assert.equal(requestedDraft.status, 'succeeded');
    assert.equal(test.getState().messages.length, beforeDraftCount + 1);

    await test.runtime.execute({ type: 'contacts.preferences.set-send-time', contactId: mira.id, time: null });
    assert.equal(test.getState().contacts.find(contact => contact.id === mira.id)?.customSendTime, undefined);
  });
});
