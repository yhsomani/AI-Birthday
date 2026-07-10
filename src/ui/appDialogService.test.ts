import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import { appDialogController, showAppAlert } from './appDialogService';

describe('cross-platform app alert compatibility service', () => {
  it('maps cancel/destructive actions and executes only the chosen callback', async () => {
    let chosen = '';
    showAppAlert('Confirm', 'Choose one action.', [
      { text: 'Cancel', style: 'cancel', onPress: () => { chosen = 'cancel'; } },
      { text: 'Delete', style: 'destructive', onPress: () => { chosen = 'delete'; } }
    ]);
    const active = appDialogController.getState().active;
    assert.equal(active?.actions[0].role, 'cancel');
    assert.equal(active?.actions[1].role, 'destructive');
    assert.equal(appDialogController.chooseAction('action-1'), true);
    await Promise.resolve();
    await Promise.resolve();
    assert.equal(chosen, 'delete');
  });

  it('keeps the application free from React Native Alert dependencies', () => {
    const appSource = readFileSync(new URL('../App.tsx', import.meta.url), 'utf8');
    assert.doesNotMatch(appSource, /Alert\.alert|\bAlert,/);
    assert.match(appSource, /<AppDialogHost/);
  });
});
