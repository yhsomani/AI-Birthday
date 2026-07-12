import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const sourceRoot = fileURLToPath(new URL('../src/', import.meta.url));

function between(source: string, start: string, end: string): string {
  const startIndex = source.indexOf(start);
  const endIndex = source.indexOf(end, startIndex + start.length);
  expect(startIndex).toBeGreaterThanOrEqual(0);
  expect(endIndex).toBeGreaterThan(startIndex);
  return source.slice(startIndex, endIndex);
}

describe('coordination-operation security architecture', () => {
  it('requires the active operation fence in every stateful callable transaction', () => {
    const source = readFileSync(
      join(sourceRoot, 'services/controlPlane.ts'),
      'utf8',
    );
    const statefulRanges = [
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
      ['public async beginDeletion', 'public async companionStatus'],
      ['public async companionStatus', 'public async advanceDeletion'],
    ] as const;
    for (const [start, end] of statefulRanges) {
      expect(between(source, start, end)).toContain('paths.operation');
    }
  });

  it('requires recent Google reauthentication and consumed App Check', () => {
    const source = readFileSync(join(sourceRoot, 'functions/index.ts'), 'utf8');
    expect(source).toContain('enforceAppCheck: true');
    expect(source).toContain('consumeAppCheckToken: true');
    for (const [start, end] of [
      [
        'export const resetContactDerivedState',
        'export const releaseAndroidSender',
      ],
      ['export const releaseAndroidSender', 'export const companionStatus'],
    ] as const) {
      const callable = between(source, start, end);
      expect(callable).toContain('onCall(commonOptions');
      expect(callable).toContain('requireRecentGoogleAuthentication');
    }
  });

  it('keeps lost-journal status authenticated and read-only', () => {
    const source = readFileSync(join(sourceRoot, 'functions/index.ts'), 'utf8');
    const callable = between(
      source,
      'export const coordinationLifecycleStatus',
      'export const companionStatus',
    );
    expect(callable).toContain('onCall(');
    expect(callable).toContain('commonOptions');
    expect(callable).toContain('coordinationLifecycleStatusSchema');
    expect(callable).not.toContain('requireRecentGoogleAuthentication');
  });

  it('keeps deletion receipt lookup signed-out, App-Checked, consumed, and content-free', () => {
    const source = readFileSync(join(sourceRoot, 'functions/index.ts'), 'utf8');
    const callable = between(
      source,
      'export const accountDeletionReceipt',
      'export const resetContactDerivedState',
    );
    expect(callable).toContain('onCall(commonOptions');
    expect(callable).toContain('requireSignedOutAppChecked');
    expect(callable).not.toContain('requireAuthenticated');
    expect(callable).not.toContain('requireRecentGoogleAuthentication');
    expect(source).toContain("throw new HttpsError('failed-precondition', 'SIGNED_OUT_REQUIRED')");
    expect(source).toContain('consumeAppCheckToken: true');
  });

  it('keeps operation records content-free and outside the deletable account tree', () => {
    const model = readFileSync(join(sourceRoot, 'domain/model.ts'), 'utf8');
    const paths = readFileSync(
      join(sourceRoot, 'persistence/paths.ts'),
      'utf8',
    );
    const operationModel = between(
      model,
      'export interface CoordinationOperation',
      'export interface BindingInput',
    );
    expect(operationModel).not.toMatch(
      /readonly\s+(?:email|phoneNumber|messageText|recipientName|accessToken|providerSubject)\??\s*:/u,
    );
    expect(paths).toContain(
      "db.collection('coordinationOperationFences').doc(uid)",
    );
    expect(paths).toContain("db.collection('coordinationOperationReceipts')");
    expect(paths).toContain("db.collection('deletionReceipts')");
  });
});
