import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { relateReducer } from '../state/relateReducer';
import { automationModes } from '../domain/schedulingPolicy';
import {
  availableAccountModes,
  isAccountModeAvailable,
  isAutomationModeAvailable,
  productAvailability
} from './productAvailability';

describe('truthful release capability availability', () => {
  it('exposes only implemented account and automation choices', () => {
    assert.deepEqual(availableAccountModes, ['Local']);
    assert.equal(isAccountModeAvailable('Google sync'), false);
    assert.equal(productAvailability.googleSync.available, false);
    assert.doesNotMatch(productAvailability.googleSync.reason, /connect|ready/i);

    assert.deepEqual(automationModes, ['Always ask', 'Smart approve', 'VIP approve']);
    assert.equal(isAutomationModeAvailable('Fully auto'), false);
    assert.equal(productAvailability.durableUnattendedAutomation.available, false);
    assert.match(productAvailability.durableUnattendedAutomation.reason, /not available/i);
    assert.match(productAvailability.durableUnattendedAutomation.reason, /review-controlled/i);
    assert.equal(productAvailability.authenticatedAiProvider.available, false);
    assert.match(productAvailability.authenticatedAiProvider.reason, /local review-first/i);
    assert.equal(productAvailability.authenticatedEmailProvider.available, false);
    assert.match(productAvailability.authenticatedEmailProvider.reason, /mail-app handoff/i);
  });

  it('rejects unavailable choices and explains the active release boundary', () => {
    const state = createTestState();
    const sync = relateReducer(state, { type: 'setAccountMode', mode: 'Google sync' });
    const automation = relateReducer(state, { type: 'setAutomationMode', mode: 'Fully auto' });

    assert.equal(sync.settings.accountMode, 'Local');
    assert.match(sync.activity[0].title, /unavailable/i);
    assert.equal(automation.settings.automationMode, state.settings.automationMode);
    assert.match(automation.activity[0].detail, /review-controlled/i);
  });

  it('normalizes legacy unavailable selections during hydration', () => {
    const state = createTestState();
    state.settings.accountMode = 'Google sync';
    state.settings.automationMode = 'Fully auto';

    const hydrated = relateReducer(state, { type: 'hydrate', state });

    assert.equal(hydrated.settings.accountMode, 'Local');
    assert.equal(hydrated.settings.automationMode, 'Always ask');
  });
});
