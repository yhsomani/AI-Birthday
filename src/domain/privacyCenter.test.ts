import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildPrivacyCenterReport } from './privacyCenter';

describe('privacy center contract', () => {
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

  it('surfaces permission rationale and denied-state fallbacks', () => {
    const state = createTestState();
    const denied = relateReducer(state, {
      type: 'recordPermissionDecision',
      capability: 'Notifications',
      decision: 'Denied'
    });
    const report = buildPrivacyCenterReport(denied);
    const notifications = report.rows.find(row => row.capability === 'Notifications');

    assert.equal(notifications?.status, 'Denied');
    assert.match(notifications?.fallback ?? '', /in-app reminder/i);
    assert.ok(report.highRiskCount > 0);
  });

  it('keeps limited and restricted OS detail visible beside legacy readiness', () => {
    const state = createTestState();
    state.privacy.permissionDecisions.Contacts = 'Granted';
    state.privacy.permissionDecisions.Calendar = 'Denied';
    state.privacy.permissionRecords = {
      Contacts: {
        capability: 'Contacts',
        userIntent: 'allow',
        lastPromptOutcome: 'granted',
        systemAuthorization: 'limited',
        lastKnownAuthorization: 'limited',
        canAskAgain: false,
        platformStatus: 'granted; access=limited'
      },
      Calendar: {
        capability: 'Calendar',
        userIntent: 'allow',
        lastPromptOutcome: 'denied',
        systemAuthorization: 'restricted',
        lastKnownAuthorization: 'restricted',
        canAskAgain: false,
        platformStatus: 'restricted'
      }
    };

    const report = buildPrivacyCenterReport(state);
    const contacts = report.rows.find(row => row.capability === 'Contacts');
    const calendar = report.rows.find(row => row.capability === 'Calendar');

    assert.equal(contacts?.status, 'Enabled');
    assert.equal(contacts?.systemAuthorization, 'limited');
    assert.match(contacts?.systemDetail ?? '', /access=limited/);
    assert.equal(calendar?.status, 'Denied');
    assert.equal(calendar?.systemAuthorization, 'restricted');
    assert.match(calendar?.systemDetail ?? '', /device settings/);
    assert.equal(calendar?.lastPromptOutcome, 'denied');
  });

  it('keeps manual WhatsApp handoff consent explicit and revocable', () => {
    const state = createTestState();
    const granted = relateReducer(state, { type: 'toggleWhatsAppHandoffConsent' });
    const revoked = relateReducer(granted, { type: 'toggleWhatsAppHandoffConsent' });

    assert.equal(granted.privacy.whatsappHandoffConsent, true);
    assert.equal(revoked.privacy.whatsappHandoffConsent, false);
    assert.match(granted.activity[0].detail, /granted/i);
    assert.match(revoked.activity[0].detail, /revoked/i);
    const beforeConsent = buildPrivacyCenterReport(state).rows.find(row => row.capability === 'WhatsApp handoff');
    const afterConsent = buildPrivacyCenterReport(granted).rows.find(row => row.capability === 'WhatsApp handoff');
    assert.notEqual(beforeConsent?.status, 'Enabled');
    assert.equal(afterConsent?.status, 'Enabled');
  });

  it('offers biometric lock after private notes without making it a blocker', () => {
    const state = privacyCleanState();
    const report = buildPrivacyCenterReport(state);
    const biometricLock = report.rows.find(row => row.capability === 'Biometric lock');

    assert.equal(biometricLock?.status, 'Recommended');
    assert.match(biometricLock?.purpose ?? '', /Private notes|provider setup/i);
    assert.equal(report.recommendationCount, 1);
    assert.equal(report.highRiskCount, 0);
    assert.match(report.summary, /privacy recommendation/i);

    const enabled = {
      ...state,
      settings: {
        ...state.settings,
        biometricLockEnabled: true
      }
    };
    const enabledReport = buildPrivacyCenterReport(enabled);
    const enabledLock = enabledReport.rows.find(row => row.capability === 'Biometric lock');

    assert.equal(enabledLock?.status, 'Needs review');
    assert.equal(enabledReport.recommendationCount, 0);
  });

  it('clears local relationship data only through an explicit confirmed action', () => {
    const state = createTestState();
    const cleared = relateReducer(state, { type: 'clearLocalDataConfirmed' });

    assert.equal(cleared.activeScreen, 'onboarding');
    assert.equal(cleared.settings.accountMode, 'Local');
    assert.equal(cleared.contacts.length, 0);
    assert.equal(cleared.events.length, 0);
    assert.equal(cleared.messages.length, 0);
    assert.equal(cleared.memories.length, 0);
    assert.equal(cleared.gifts.length, 0);
    assert.equal(cleared.backups.length, 0);
    assert.match(cleared.activity[0].title, /cleared/i);
    assert.ok(cleared.privacy.localDataClearConfirmedAt);
  });
});
