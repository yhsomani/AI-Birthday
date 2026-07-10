import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { evaluateProviderEndpointReadiness } from './providerEndpointReadiness';
import { buildSetupDoctorDryRunSnapshot, buildSetupDoctorReport } from './setupDoctor';

describe('setup doctor contract', () => {
  const privacyCleanState = () => {
    const state = createTestState();

    return {
      ...state,
      privacy: {
        ...state.privacy,
        permissionDecisions: {
          ...state.privacy.permissionDecisions,
          Contacts: 'Granted' as const,
          Notifications: 'Granted' as const,
          SMS: 'Granted' as const,
          'WhatsApp handoff': 'Granted' as const,
          'Backup export': 'Granted' as const
        }
      }
    };
  };

  it('prioritizes failed message recovery over lower-impact setup gaps', () => {
    const state = createTestState();
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
    const state = createTestState();
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
        backups: [],
        aiProvider: {
          status: 'Ready',
          lastCheckedAt: '2026-07-09T09:00:00.000Z'
        }
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: false, aiProviderSessionReady: true },
      new Date('2026-07-09T10:00:00.000Z')
    );

    assert.equal(report.recommendedCheck?.id, 'personalization');
    assert.equal(report.recommendedCheck?.targetScreen, 'contactDetail');
    assert.equal(report.recommendedCheck?.contactId, 'c-empty');
  });

  it('keeps dry runs side-effect safe and redacted', () => {
    const state = createTestState();
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

  it('treats local AI endpoints as development-only setup blockers', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      state,
      {
        aiEndpointReadiness: evaluateProviderEndpointReadiness('http://localhost:8787/draft', {
          allowLocalDevelopment: true
        }),
        emailEndpointConfigured: false
      },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const aiProvider = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'ai-provider');

    assert.equal(aiProvider?.status, 'Needs action');
    assert.equal(aiProvider?.command, 'testAiProvider');
    assert.match(aiProvider?.impact ?? '', /local-development only/i);
    assert.doesNotMatch(aiProvider?.impact ?? '', /localhost|8787/);
  });

  it('adds an email provider diagnostic when email delivery is enabled', () => {
    const initial = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...initial,
        settings: {
          ...initial.settings,
          emailEnabled: true
        }
      },
      {
        aiEndpointConfigured: true,
        emailEndpointReadiness: evaluateProviderEndpointReadiness('http://email.example.test/send')
      },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const emailProvider = report.checksByGroup
      .flatMap(group => group.checks)
      .find(check => check.id === 'email-provider');

    assert.equal(emailProvider?.status, 'Needs action');
    assert.equal(emailProvider?.targetScreen, 'settings');
    assert.match(emailProvider?.impact ?? '', /not safe to use/i);
    assert.doesNotMatch(emailProvider?.impact ?? '', /email\.example|send/);
  });

  it('does not report public provider endpoints ready without authenticated sessions', () => {
    const report = buildSetupDoctorReport(
      createTestState(),
      {
        aiEndpointReadiness: evaluateProviderEndpointReadiness('https://ai.example.test/draft'),
        emailEndpointReadiness: evaluateProviderEndpointReadiness('https://email.example.test/send'),
        aiProviderSessionReady: false,
        emailProviderSessionReady: false
      },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const checks = report.checksByGroup.flatMap(group => group.checks);
    const ai = checks.find(check => check.id === 'ai-provider');
    const email = checks.find(check => check.id === 'email-provider');
    assert.equal(ai?.status, 'Needs action');
    assert.equal(ai?.command, undefined);
    assert.match(ai?.impact ?? '', /authenticated provider session/i);
    assert.equal(email?.status, 'Needs action');
    assert.equal(email?.group, 'Required');
    assert.match(email?.impact ?? '', /authenticated provider session/i);
  });

  it('keeps an authenticated but untested provider actionable after onboarding', () => {
    const state = createTestState();
    state.onboarding.completed = true;
    state.setupChecks = state.setupChecks.map(check => ({ ...check, status: 'Ready' }));
    state.aiProvider = { status: 'Not configured' };
    const environment = {
      aiEndpointReadiness: evaluateProviderEndpointReadiness('https://ai.example.test/draft'),
      aiProviderSessionReady: true,
      emailEndpointConfigured: false,
      emailProviderSessionReady: false
    };

    const untested = buildSetupDoctorReport(state, environment, new Date('2026-07-09T10:00:00.000Z'));
    const untestedAi = untested.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'ai-provider');
    assert.equal(untestedAi?.status, 'Needs action');
    assert.equal(untestedAi?.command, 'testAiProvider');
    assert.match(untestedAi?.impact ?? '', /readiness test/i);

    state.aiProvider = { status: 'Ready', lastCheckedAt: '2026-07-09T09:00:00.000Z' };
    const tested = buildSetupDoctorReport(state, environment, new Date('2026-07-09T10:00:00.000Z'));
    const testedAi = tested.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'ai-provider');
    assert.equal(testedAi?.status, 'Ready');
  });

  it('recommends the highest-priority warning when no required blocker remains', () => {
    const state = privacyCleanState();
    state.onboarding.completed = true;
    state.messages = state.messages.map(message => ({
      ...message,
      status: 'Sent',
      sentAt: '2026-07-09T09:00:00.000Z'
    }));
    state.aiProvider = { status: 'Ready', lastCheckedAt: '2026-07-09T09:00:00.000Z' };
    state.backups = [{ id: 'backup-current', createdAt: '2026-07-09T09:00:00.000Z', recordCount: 1, encrypted: true }];
    const report = buildSetupDoctorReport(
      state,
      {
        aiEndpointReadiness: evaluateProviderEndpointReadiness('https://ai.example.test/draft'),
        aiProviderSessionReady: true,
        emailEndpointConfigured: false,
        emailProviderSessionReady: false
      },
      new Date('2026-07-09T10:00:00.000Z')
    );

    assert.equal(
      report.checksByGroup.flatMap(group => group.checks).some(check => check.status === 'Needs action'),
      false
    );
    assert.equal(report.recommendedCheck?.status, 'Warning');
    assert.match(report.summary, /No required blockers found\. Next review:/i);
  });

  it('surfaces unsafe configured email endpoints even when email delivery is disabled', () => {
    const report = buildSetupDoctorReport(
      createTestState(),
      {
        aiEndpointConfigured: true,
        emailEndpointReadiness: evaluateProviderEndpointReadiness('https://user:secret@email.example.test/send')
      },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const emailProvider = report.checksByGroup
      .flatMap(group => group.checks)
      .find(check => check.id === 'email-provider');

    assert.equal(emailProvider?.status, 'Needs action');
    assert.equal(emailProvider?.group, 'Required');
    assert.equal(emailProvider?.title, 'Email provider endpoint');
    assert.match(emailProvider?.impact ?? '', /not safe to use/i);
    assert.doesNotMatch(emailProvider?.impact ?? '', /secret|email\.example|send/);
  });

  it('treats missing email provider setup as optional while manual handoff is available', () => {
    const initial = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...initial,
        settings: {
          ...initial.settings,
          emailEnabled: true
        }
      },
      {
        aiEndpointConfigured: true,
        emailEndpointConfigured: false
      },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const emailProvider = report.checksByGroup
      .flatMap(group => group.checks)
      .find(check => check.id === 'email-provider');

    assert.equal(emailProvider?.status, 'Ready');
    assert.equal(emailProvider?.group, 'Reliability');
    assert.equal(emailProvider?.title, 'Email provider optional');
    assert.match(emailProvider?.impact ?? '', /optional/i);
    assert.doesNotMatch(report.recommendedCheck?.id ?? '', /email-provider/);
  });

  it('builds a redacted dry-run snapshot for activity logging', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        messages: [{ ...state.messages[0], body: 'Do not expose this dry-run body' }]
      },
      { aiEndpointConfigured: false, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const snapshot = buildSetupDoctorDryRunSnapshot(report);

    assert.equal(snapshot.safe, true);
    assert.equal(snapshot.readyCount, report.readyCount);
    assert.equal(snapshot.totalCount, report.totalCount);
    assert.match(snapshot.activityDetail, /checks ready/i);
    assert.match(snapshot.activityDetail, /blocker\(s\)|No required blockers/i);
    assert.doesNotMatch(JSON.stringify(snapshot), /Do not expose this dry-run body/);
  });

  it('surfaces privacy permission fallbacks in setup diagnostics', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        privacy: {
          ...state.privacy,
          permissionDecisions: {
            ...state.privacy.permissionDecisions,
            Notifications: 'Denied'
          }
        }
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const privacy = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'privacy-controls');

    assert.equal(privacy?.status, 'Warning');
    assert.match(privacy?.impact ?? '', /need review|fallback/i);
    assert.equal(privacy?.targetScreen, 'settings');
  });

  it('keeps contextual biometric recommendations non-blocking in setup diagnostics', () => {
    const state = privacyCleanState();
    const report = buildSetupDoctorReport(
      state,
      { aiEndpointConfigured: true, emailEndpointConfigured: false },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const privacy = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'privacy-controls');

    assert.equal(privacy?.status, 'Ready');
    assert.match(privacy?.impact ?? '', /privacy recommendation/i);
    assert.notEqual(report.recommendedCheck?.id, 'privacy-controls');
  });

  it('routes denied notification readiness to settings instead of scheduling', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        privacy: {
          ...state.privacy,
          permissionDecisions: {
            ...state.privacy.permissionDecisions,
            Notifications: 'Denied'
          }
        }
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const reminders = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'reminders');

    assert.equal(reminders?.status, 'Warning');
    assert.equal(reminders?.command, undefined);
    assert.equal(reminders?.actionLabel, 'Open privacy settings');
    assert.match(reminders?.impact ?? '', /permission unavailable|in-app/i);
  });

  it('surfaces stale notification routes as a required setup fix', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        privacy: {
          ...state.privacy,
          permissionDecisions: {
            ...state.privacy.permissionDecisions,
            Notifications: 'Granted'
          }
        },
        reminderPlans: [
          {
            id: 'reminder-stale',
            eventId: 'missing-event',
            contactId: state.contacts[0].id,
            title: 'RelateAI reminder',
            body: 'Open RelateAI to review.',
            triggerAt: '2026-07-10T09:00:00.000Z'
          }
        ],
        messages: [],
        backups: state.backups
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const reminders = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'reminders');

    assert.equal(reminders?.status, 'Needs action');
    assert.equal(reminders?.command, undefined);
    assert.match(reminders?.impact ?? '', /Notification route is stale/i);
    assert.match(reminders?.impact ?? '', /missing, archived, mismatched, or already handled/i);
  });

  it('surfaces missing React Native release evidence as a reliability warning', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      state,
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const release = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'release-evidence');

    assert.equal(release?.status, 'Warning');
    assert.equal(release?.targetScreen, 'setupCheck');
    assert.match(release?.impact ?? '', /not been attached/i);
  });

  it('surfaces React Native release blockers and legacy artifact warnings without exposing build secrets', () => {
    const state = createTestState();
    const warningReport = buildSetupDoctorReport(
      state,
      {
        aiEndpointConfigured: true,
        emailEndpointConfigured: true,
        releaseEvidence: {
          blockers: [],
          warnings: ['signed android pending', 'legacy archive pending'],
          legacyKotlinGradleArtifactPaths: ['app', 'core']
        }
      },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const warning = warningReport.checksByGroup
      .flatMap(group => group.checks)
      .find(check => check.id === 'release-evidence');

    assert.equal(warning?.status, 'Warning');
    assert.match(warning?.impact ?? '', /2 React Native release evidence warning/);
    assert.match(warning?.impact ?? '', /2 legacy Android artifact path/);
    assert.doesNotMatch(JSON.stringify(warning), /signed android pending|legacy archive pending/);

    const blockerReport = buildSetupDoctorReport(
      state,
      {
        aiEndpointConfigured: true,
        emailEndpointConfigured: true,
        releaseEvidence: { blockers: ['secret endpoint failure'], warnings: [] }
      },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const blocker = blockerReport.checksByGroup
      .flatMap(group => group.checks)
      .find(check => check.id === 'release-evidence');

    assert.equal(blocker?.status, 'Needs action');
    assert.match(blocker?.impact ?? '', /1 React Native release blocker/);
    assert.doesNotMatch(JSON.stringify(blocker), /secret endpoint failure/);
  });

  it('surfaces verified normalized local storage as a reliability check', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        persistence: {
          ...state.persistence,
          storageHealth: {
            status: 'Ready',
            storageFormat: 'Normalized',
            payloadBytes: 24000,
            entryCount: 42,
            chunkCount: 3,
            largestEntryBytes: 900,
            envelopeVersion: 2,
            lastVerifiedAt: '2026-07-09T10:00:00.000Z'
          }
        }
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const storage = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'local-storage');

    assert.equal(storage?.status, 'Ready');
    assert.equal(storage?.targetScreen, 'settings');
    assert.match(storage?.impact ?? '', /42 normalized storage item\(s\)/);
    assert.doesNotMatch(JSON.stringify(storage), /24000|900|2026-07-09T10/);
  });

  it('surfaces a verified encrypted entity repository without exposing storage metadata', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        persistence: {
          ...state.persistence,
          storageHealth: {
            status: 'Ready',
            storageFormat: 'Encrypted entity repository',
            payloadBytes: 48000,
            entryCount: 73,
            chunkCount: 0,
            largestEntryBytes: 1900,
            envelopeVersion: 1,
            lastVerifiedAt: '2026-07-09T10:00:00.000Z'
          }
        }
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const storage = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'local-storage');

    assert.equal(storage?.status, 'Ready');
    assert.match(storage?.impact ?? '', /73 encrypted repository record\(s\)/);
    assert.doesNotMatch(JSON.stringify(storage), /48000|1900|2026-07-09T10/);
  });

  it('surfaces corrupt local storage as a recovery blocker without exposing payload data', () => {
    const state = createTestState();
    const report = buildSetupDoctorReport(
      {
        ...state,
        messages: [],
        persistence: {
          status: 'Ready',
          storageHealth: {
            status: 'Corrupt',
            storageFormat: 'Corrupt',
            payloadBytes: 18000,
            entryCount: 0,
            chunkCount: 0,
            largestEntryBytes: 0,
            issue: 'Saved state entry contacts integrity check failed.'
          }
        }
      },
      { aiEndpointConfigured: true, emailEndpointConfigured: true },
      new Date('2026-07-09T10:00:00.000Z')
    );
    const storage = report.checksByGroup.flatMap(group => group.checks).find(check => check.id === 'local-storage');

    assert.equal(storage?.status, 'Needs action');
    assert.equal(storage?.actionLabel, 'Open persistence');
    assert.match(storage?.impact ?? '', /integrity failed/i);
    assert.doesNotMatch(JSON.stringify(storage), /18000|message body|raw/i);
  });
});
