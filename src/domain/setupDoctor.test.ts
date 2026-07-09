import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { buildSetupDoctorReport } from './setupDoctor';

describe('setup doctor contract', () => {
  it('prioritizes failed message recovery over lower-impact setup gaps', () => {
    const state = createInitialState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        messages: [
          {
            ...state.messages[0],
            id: 'msg-failed',
            status: 'Failed',
            body: 'Sensitive failed message body'
          },
          ...state.messages
        ]
      },
      { aiEndpointConfigured: false, emailEndpointConfigured: false },
      new Date('2026-07-09T10:00:00.000Z')
    );

    assert.equal(report.recommendedCheck?.id, 'failed-messages');
    assert.equal(report.recommendedCheck?.targetScreen, 'messages');
  });

  it('routes weak personalization to the contact that needs enrichment', () => {
    const state = createInitialState();
    const sparseContact = {
      ...state.contacts[0],
      id: 'c-empty',
      name: 'Empty Contact',
      relationship: 'Friend',
      healthScore: 30,
      notesSummary: '',
      language: 'English' as const,
      tone: ['Warm' as const]
    };
    const report = buildSetupDoctorReport(
      {
        ...state,
        contacts: [sparseContact],
        events: [],
        memories: [],
        gifts: [],
        messages: [],
        activity: [],
        backups: []
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: false },
      new Date('2026-07-09T10:00:00.000Z')
    );

    assert.equal(report.recommendedCheck?.id, 'personalization');
    assert.equal(report.recommendedCheck?.targetScreen, 'contactDetail');
    assert.equal(report.recommendedCheck?.contactId, 'c-empty');
  });

  it('keeps dry runs side-effect safe and redacted', () => {
    const state = createInitialState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        messages: [{ ...state.messages[0], body: 'Do not expose this message body' }]
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );

    assert.equal(report.dryRun.safe, true);
    assert.match(report.dryRun.message, /does not create, approve, schedule, or send/i);
    assert.doesNotMatch(JSON.stringify(report), /Do not expose this message body/);
  });
});
