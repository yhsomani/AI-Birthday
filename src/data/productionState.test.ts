import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createProductionInitialState } from './productionState';

describe('fresh production state', () => {
  it('uses a supported device language without adding fixture relationship data', () => {
    const hindi = createProductionInitialState('hi-Deva-IN');
    const unsupported = createProductionInitialState('fr-FR');

    assert.equal(hindi.settings.locale, 'hi-IN');
    assert.equal(unsupported.settings.locale, 'en-IN');
    assert.equal(hindi.contacts.length, 0);
    assert.equal(hindi.messages.length, 0);
  });
});
