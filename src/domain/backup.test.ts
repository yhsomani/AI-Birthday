import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import {
  createEncryptedBackup,
  decryptEncryptedBackup,
  previewEncryptedBackup,
  validateBackupPassphrase
} from './backup';

const strongPassphrase = 'CorrectHorse123';

describe('encrypted backup contract', () => {
  it('validates backup passphrases before export', () => {
    assert.ok(validateBackupPassphrase('short1').length > 0);
    assert.ok(validateBackupPassphrase('longbutnonumeric').length > 0);
    assert.deepEqual(validateBackupPassphrase(strongPassphrase), []);
  });

  it('exports encrypted state, previews metadata, and restores with the passphrase', async () => {
    const state = createInitialState();
    const raw = await createEncryptedBackup(state, strongPassphrase, {
      iterations: 1000,
      createdAt: '2026-07-09T00:00:00.000Z'
    });
    const preview = previewEncryptedBackup(raw);
    const restored = await decryptEncryptedBackup(raw, strongPassphrase);

    assert.equal(preview.app, 'RelateAI');
    assert.equal(preview.encrypted, true);
    assert.equal(preview.recordCounts.contacts, state.contacts.length);
    assert.equal(restored.contacts[0].id, state.contacts[0].id);
    assert.equal(restored.messages.length, state.messages.length);
    assert.doesNotMatch(raw, /Asha Mehra/);
    assert.doesNotMatch(raw, /mango lassi/);
  });

  it('rejects wrong passphrases and tampered files', async () => {
    const state = createInitialState();
    const raw = await createEncryptedBackup(state, strongPassphrase, { iterations: 1000 });
    const tampered = JSON.parse(raw) as {
      cipher: {
        ciphertext: string;
      };
    };
    tampered.cipher.ciphertext = `A${tampered.cipher.ciphertext.slice(1)}`;

    await assert.rejects(() => decryptEncryptedBackup(raw, 'WrongPassphrase123'), /incorrect|damaged/i);
    await assert.rejects(() => decryptEncryptedBackup(JSON.stringify(tampered), strongPassphrase), /integrity/i);
  });
});
