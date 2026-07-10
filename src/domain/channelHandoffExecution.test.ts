import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { HandoffTarget } from './channelHandoff';
import {
  buildHandoffSharePayload,
  runHandoffTarget,
  type HandoffExecutionDependencies
} from './channelHandoffExecution';

const target = (overrides: Partial<HandoffTarget> = {}): HandoffTarget => ({
  url: 'sms:+919999999999?body=Hello',
  shareFallback: true,
  label: 'Open SMS',
  fallbackLabel: 'Copy/share message',
  privacyNote: 'RelateAI opens the approved text only.',
  completionTitle: 'Mark sent?',
  completionMessage: 'Mark sent only after sending.',
  markSentLabel: 'I sent it',
  dismissLabel: 'Not yet',
  ...overrides
});

const dependencies = (overrides: Partial<HandoffExecutionDependencies> = {}): HandoffExecutionDependencies => ({
  canOpenUrl: async () => true,
  openUrl: async () => undefined,
  share: async () => 'shared',
  ...overrides
});

describe('channel handoff execution', () => {
  it('opens an available destination and asks for sent confirmation', async () => {
    const opened: string[] = [];
    const result = await runHandoffTarget(
      { target: target(), body: 'Approved body', contactName: 'Asha' },
      dependencies({
        openUrl: async url => {
          opened.push(url);
        },
        share: async () => {
          throw new Error('Share should not be used when the destination opens.');
        }
      })
    );

    assert.deepEqual(opened, ['sms:+919999999999?body=Hello']);
    assert.equal(result.outcome, 'opened-destination');
    assert.equal(result.needsSentConfirmation, true);
    assert.equal(result.usedFallback, false);
  });

  it('falls back to sharing only the approved text when the destination app is unavailable', async () => {
    const sharedMessages: string[] = [];
    const result = await runHandoffTarget(
      {
        target: target({ reason: 'Phone number is missing.' }),
        body: 'Approved body',
        contactName: 'Mira'
      },
      dependencies({
        canOpenUrl: async () => false,
        share: async payload => {
          sharedMessages.push(payload.message);
          return 'shared';
        }
      })
    );

    assert.deepEqual(sharedMessages, ['Approved body']);
    assert.equal(result.outcome, 'shared-fallback');
    assert.equal(result.needsSentConfirmation, true);
    assert.equal(result.usedFallback, true);
  });

  it('does not ask for sent confirmation when the share sheet is dismissed', async () => {
    const result = await runHandoffTarget(
      { target: target({ url: undefined }), body: 'Approved body', contactName: 'Mira' },
      dependencies({
        share: async () => 'dismissed'
      })
    );

    assert.equal(result.outcome, 'dismissed-fallback');
    assert.equal(result.needsSentConfirmation, false);
    assert.equal(result.usedFallback, true);
  });

  it('uses fallback sharing when opening a reported destination fails', async () => {
    let shared = false;
    const result = await runHandoffTarget(
      { target: target(), body: 'Approved body', contactName: 'Asha' },
      dependencies({
        openUrl: async () => {
          throw new Error('Destination failed.');
        },
        share: async () => {
          shared = true;
          return 'shared';
        }
      })
    );

    assert.equal(shared, true);
    assert.equal(result.outcome, 'shared-fallback');
    assert.equal(result.needsSentConfirmation, true);
  });

  it('builds share payloads with only approved text and a recipient-free generic title', () => {
    const payload = buildHandoffSharePayload('Approved body', ' Asha ');

    assert.equal(payload.title, 'Approved message');
    assert.equal(payload.message, 'Approved body');
    assert.doesNotMatch(JSON.stringify(payload), /Asha|missing|diagnostic|reason/i);
  });
});
