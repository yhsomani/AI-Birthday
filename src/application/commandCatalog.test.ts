import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildCommandCatalog, commandCatalogCoversEveryCommand, supportedHarnessCommandTypes } from './commandCatalog';

describe('functional command catalog', () => {
  it('exhaustively exposes every strict command type without duplicates', () => {
    assert.equal(commandCatalogCoversEveryCommand, true);
    assert.equal(new Set(supportedHarnessCommandTypes).size, supportedHarnessCommandTypes.length);
    assert.equal(supportedHarnessCommandTypes.includes('system.catalog'), true);
    assert.equal(supportedHarnessCommandTypes.includes('messages.set-channel'), true);
    assert.equal(supportedHarnessCommandTypes.includes('events.import-file'), true);
  });

  it('teaches core id, confirmation, secure-secret, and review-first workflows', () => {
    const catalog = buildCommandCatalog();
    assert.equal(catalog.commandCount, supportedHarnessCommandTypes.length);
    assert.ok(catalog.workflows.some(workflow => workflow.id === 'message-review'));
    assert.ok(catalog.workflows.some(workflow => workflow.id === 'encrypted-backup'));
    assert.ok(catalog.workflows.some(workflow => workflow.id === 'analytics-reflection'));
    assert.match(JSON.stringify(catalog), /\$SECURE_INPUT/);
    assert.doesNotMatch(JSON.stringify(catalog), /phone|email address|private note body/i);
  });
});
