import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import {
  decideClaim,
  decideAdvanceDeletionDrain,
  decideBeginDeletion,
  decideRegistration,
} from '../src/domain/decisions.js';
import {
  decideAdvanceSenderRelease,
  decideBeginContactDerivedReset,
  decideBeginSenderRelease,
} from '../src/domain/coordinationOperations.js';
import { deriveOperationIdentity } from '../src/domain/operationIdentity.js';
import { SCHEMA_VERSION, type AccountFence } from '../src/domain/model.js';
import {
  INSTALLATION_ID,
  NOW_MS,
  fence,
  globalControl,
  installation,
  binding,
} from './fixtures.js';

const registration = {
  ledgerGeneration: 'ledger-generation-1',
  installationId: INSTALLATION_ID,
  appBuildNumber: 100,
  policyVersion: 7,
  distributionChannel: 'PLAY',
} as const;

describe('first-registration versus account deletion race', () => {

  it('has no serial ordering that leaves writable Android state after deletion wins', () => {
    fc.assert(
      fc.property(fc.boolean(), deletionFirst => {
        if (deletionFirst) {
          const deletion = decideBeginDeletion(
            null,
            null,
            '1'.repeat(64),
            NOW_MS,
          );
          expect(deletion.kind).toBe('STARTED');
          if (deletion.kind === 'STARTED') {
            expect(
              decideRegistration(
                globalControl(),
                deletion.tombstone,
                null,
                null,
                registration,
                NOW_MS + 1,
              ),
            ).toEqual({ kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' });
          }
          return;
        }

        const registered = decideRegistration(
          globalControl(),
          null,
          null,
          null,
          registration,
          NOW_MS,
        );
        expect(registered.kind).toBe('REGISTERED_ACTIVE');
        if (registered.kind === 'REGISTERED_ACTIVE') {
          const deletion = decideBeginDeletion(
            null,
            registered.fence,
            '1'.repeat(64),
            NOW_MS + 1,
          );
          expect(deletion.kind).toBe('STARTED');
          if (deletion.kind === 'STARTED') {
            expect(deletion.fence?.mode).toBe('DELETING');
            expect(deletion.tombstone.stage).toBe('DRAINING');
          }
        }
      }),
    );
  });

  it('never completes a deletion drain at or before its frozen deadline', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: 60_000 }), offset => {
        const drainUntilMs = NOW_MS + 60_000;
        const fence: AccountFence = {
          schemaVersion: SCHEMA_VERSION,
          mode: 'DELETING',
          activeInstallationId: INSTALLATION_ID,
          senderEpoch: 1,
          ownerLeaseUntilMs: NOW_MS,
          nextArmNotBeforeMs: NOW_MS,
          latestIssuedSubmitNotAfterMs: drainUntilMs,
          resetGeneration: 1,
          birthdayAutomationNotBeforeMs: NOW_MS,
          deletionDrainUntilMs: drainUntilMs,
          createdAtMs: NOW_MS,
          updatedAtMs: NOW_MS,
        };
        const decision = decideAdvanceDeletionDrain(
          {
            schemaVersion: SCHEMA_VERSION,
            requestKey: '1'.repeat(64),
            stage: 'DRAINING',
            drainUntilMs,
            nextSweepAtMs: drainUntilMs + 1,
            sweepAttemptCount: 0,
            createdAtMs: NOW_MS,
            updatedAtMs: NOW_MS,
          },
          fence,
          installation({ epoch: 1 }),
          NOW_MS + offset,
        );
        expect(decision.kind).toBe('WAIT');
      }),
    );
  });
});

describe('contact reset and sender release serialization', () => {

  it('either deletes a pre-reset claim or refuses an old-generation claim', () => {
    fc.assert(
      fc.property(fc.boolean(), claimCommitsFirst => {
        const oldFence = fence();
        const oldInstallation = installation();
        const claimInput = {
          ...binding(),
          purpose: 'BIRTHDAY' as const,
          claimRequestId: '00000000-0000-4000-8000-000000000951',
          claimId: '00000000-0000-4000-8000-000000000951',
          requestKey: '00000000-0000-4000-8000-000000000951',
          occurrenceAliasKeys: ['v1.reset-race-occurrence'],
          destinationAliasKeys: ['v1.reset-race-destination'],
          testMaterialAliasKeys: [],
        };
        const reset = decideBeginContactDerivedReset(
          null,
          null,
          null,
          oldFence,
          oldInstallation,
          deriveOperationIdentity(
            'uid-reset-race',
            'CONTACT_DERIVED_RESET',
            '00000000-0000-4000-8000-000000000952',
          ),
          NOW_MS + 1,
        );
        expect(reset.kind).toBe('STARTED');
        if (reset.kind !== 'STARTED') {
          return;
        }
        if (claimCommitsFirst) {
          expect(
            decideClaim(
              globalControl(),
              null,
              oldFence,
              oldInstallation,
              {
                requestRecord: null,
                requestClaim: null,
                occurrenceKeys: [],
                destinationGuards: [],
              },
              claimInput,
              NOW_MS,
            ).kind,
          ).toBe('CLAIMED');
          expect(reset.operation.stage).toBe('RESET_DRAINING');
          return;
        }
        expect(
          decideClaim(
            globalControl(),
            null,
            reset.fence,
            reset.activeInstallation,
            {
              requestRecord: null,
              requestClaim: null,
              occurrenceKeys: [],
              destinationGuards: [],
            },
            claimInput,
            NOW_MS + 2,
          ),
        ).toEqual({ kind: 'REFUSED', reason: 'RESET_SUPPRESSED' });
      }),
    );
  });

  it('never releases at or before the frozen sender drain deadline', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: 60_000 }), offset => {
        const drainUntilMs = NOW_MS + 60_000;
        const started = decideBeginSenderRelease(
          null,
          null,
          null,
          fence({ latestIssuedSubmitNotAfterMs: drainUntilMs }),
          installation(),
          {
            installationId: INSTALLATION_ID,
            senderEpoch: 4,
            resetGeneration: 3,
          },
          deriveOperationIdentity(
            'uid-release-race',
            'SENDER_RELEASE',
            '00000000-0000-4000-8000-000000000953',
            [INSTALLATION_ID, '4', '3'],
          ),
          NOW_MS,
        );
        expect(started.kind).toBe('STARTED');
        if (started.kind !== 'STARTED') {
          return;
        }
        expect(
          decideAdvanceSenderRelease(
            started.operation,
            started.fence,
            started.activeInstallation,
            NOW_MS + offset,
          ),
        ).toEqual({ kind: 'WAIT', drainUntilMs });
      }),
    );
  });
});
