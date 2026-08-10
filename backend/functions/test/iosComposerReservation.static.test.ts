import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const sourceRoot = fileURLToPath(new URL('../src/', import.meta.url));

const readSource = (relative: string): string =>
  readFileSync(join(sourceRoot, relative), 'utf8').replace(/\r\n/gu, '\n');

function between(source: string, start: string, end: string): string {
  const startIndex = source.indexOf(start);
  const endIndex = source.indexOf(end, startIndex + start.length);
  expect(startIndex).toBeGreaterThanOrEqual(0);
  expect(endIndex).toBeGreaterThan(startIndex);
  return source.slice(startIndex, endIndex);
}

describe('iOS composer reservation architecture', () => {
  it('makes every Android sender mutation contend on the same top-level document', () => {
    const source = readSource('services/controlPlane.ts');
    const ranges = [
      [
        'public async requestContactDerivedReset',
        'public async requestSenderRelease',
      ],
      [
        'public async requestSenderRelease',
        'private async advanceContactResetDrain',
      ],
      ['public async registerAndroidInstallation', 'public async renewLease'],
      ['public async renewLease', 'public async changeAccountMode'],
      [
        'public async changeAccountMode',
        'public async claimBirthdayOccurrence',
      ],
      ['private async claim(', 'public async armAttempt'],
      ['private async resolveArm(', 'private applyArmDecision'],
      ['public async authorizeSafeRetry', 'public async reportTestOutcome'],
      ['public async reportTestOutcome', 'public async beginTransfer'],
      ['public async beginTransfer', 'public async completeTransfer'],
      ['public async completeTransfer', 'public async beginDeletion'],
    ] as const;
    for (const [start, end] of ranges) {
      const body = between(source, start, end);
      expect(body).toContain('paths.iosComposerReservation');
      expect(body).toContain('iosComposerBlocksSenderMutation');
    }
  });

  it('replays immutable destructive completion before refusing new Android mutations', () => {
    const source = readSource('services/controlPlane.ts');
    for (const [start, end, decision] of [
      [
        'public async requestContactDerivedReset',
        'public async requestSenderRelease',
        'decideBeginContactDerivedReset',
      ],
      [
        'public async requestSenderRelease',
        'private async advanceContactResetDrain',
        'decideBeginSenderRelease',
      ],
    ] as const) {
      const body = between(source, start, end);
      const replayIndex = body.indexOf('if (receipt !== null)');
      const reservationIndex = body.indexOf(
        'iosComposerBlocksSenderMutation(iosComposerReservationSnapshot, nowMs)',
      );
      expect(replayIndex).toBeGreaterThanOrEqual(0);
      expect(reservationIndex).toBeGreaterThan(replayIndex);
      expect(body.slice(replayIndex, reservationIndex)).toContain(decision);
    }
  });

  it('lets deletion dominate in the same transaction and verifies absence', () => {
    const source = readSource('services/controlPlane.ts');
    const deletion = between(
      source,
      'public async beginDeletion',
      'public async accountDeletionReceipt',
    );
    expect(deletion).toContain('transaction.get(paths.iosComposerReservation)');
    expect(deletion).toContain(
      'transaction.delete(paths.iosComposerReservation)',
    );
    const finalization = between(
      source,
      'public async finalizeDeletionTombstone',
      '\n  }\n}',
    );
    expect(finalization).toContain('iosComposerReservationSnapshot.exists');
  });

  it('uses authenticated replay-protected callables and content-free schemas', () => {
    const functionsSource = readSource('functions/index.ts');
    expect(functionsSource).toContain('enforceAppCheck: true');
    expect(functionsSource).toContain('consumeAppCheckToken: true');
    for (const [start, end] of [
      [
        'export const acquireIOSComposerReservation',
        'export const commitIOSComposerReservation',
      ],
      [
        'export const commitIOSComposerReservation',
        'export const releaseIOSComposerReservation',
      ],
      [
        'export const releaseIOSComposerReservation',
        'export const sweepDeletionDrains',
      ],
    ] as const) {
      const callable = between(functionsSource, start, end);
      expect(callable).toContain('onCall(');
      expect(callable).toContain('commonOptions');
      expect(callable).toContain('requireAuthenticated');
      expect(callable).not.toContain('requireRecentGoogleAuthentication');
    }

    const model = readSource('domain/model.ts');
    const reservation = between(
      model,
      'export interface IOSComposerReservation',
      '/**\n * A short-lived, account-global mutation fence.',
    );
    expect(reservation).not.toMatch(
      /(?:contact|civilDate|destination|phone|body|message|recipient)/u,
    );
  });
});
