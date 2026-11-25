import { readFileSync } from 'node:fs';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

let environment: RulesTestEnvironment;

const personRecord = (id: string, name: string) => ({
  id,
  name,
  relationship: 'friend',
  relationshipCloseness: 'close',
  preferredLanguage: 'en',
  preferredTone: 'warm',
  importantFacts: '',
  preferredDeliveryChannel: 'sms',
  autoPrepare: false,
  autoSendPolicy: 'manual',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
  version: 1,
});

const generationManifest = (generationId: string) => ({
  schemaVersion: 1,
  generationId,
  createdAt: '2026-01-01T00:00:00.000Z',
  people: 1,
  birthdays: 0,
  drafts: 0,
  reminderSettings: 0,
});

const generationManifestV2 = (generationId: string) => ({
  ...generationManifest(generationId),
  schemaVersion: 2,
  birthdayDateEncoding: 'calendar-date-v1',
});

const backupPointer = {
  schemaVersion: 1,
  generationId: 'g1',
  publishedAt: '2026-01-01T00:00:00.000Z',
};

const backupPointerV2 = {
  schemaVersion: 2,
  generationId: 'g1780000000000000-a1b2c3d4',
  publishedAt: '2026-01-01T00:00:00.000Z',
};

beforeAll(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-birthday-autopilot',
    firestore: {
      rules: readFileSync(
        new URL('../../firestore.rules', import.meta.url),
        'utf8',
      ),
    },
  });
});

afterAll(async () => {
  await environment.cleanup();
});

describe('Firestore authorization rules', () => {
  it('denies unauthenticated direct reads and writes', async () => {
    const db = environment.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'globalControl/current')));
    await assertFails(
      setDoc(doc(db, 'accounts/uid-one'), { mode: 'TEST_ONLY' }),
    );
  });

  it('allows authenticated owners to access only their own opt-in backup collections', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-one', { email_verified: true })
      .firestore();
    const otherDb = environment
      .authenticatedContext('uid-two', { email_verified: true })
      .firestore();

    await setDoc(
      doc(ownerDb, 'users/uid-one/people/person-1'),
      personRecord('person-1', 'Test'),
    );

    await assertFails(getDoc(doc(otherDb, 'users/uid-one/people/person-1')));
    await assertFails(
      setDoc(
        doc(otherDb, 'users/uid-one/people/person-1'),
        personRecord('person-1', 'Other'),
      ),
    );
  });

  it('denies authenticated direct reads and writes at every ledger depth', async () => {
    const db = environment
      .authenticatedContext('uid-one', { email_verified: true })
      .firestore();
    await assertFails(getDoc(doc(db, 'accounts/uid-one')));
    await assertFails(
      getDoc(doc(db, 'accounts/uid-one/occurrenceClaims/opaque-claim')),
    );
    await assertFails(
      setDoc(doc(db, 'deletionTombstones/uid-one'), { stage: 'DRAINING' }),
    );
  });

  it('allows existing drafts longer than the old 8192-character limit without truncation', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-long-draft', { email_verified: true })
      .firestore();
    const body = 'a'.repeat(16_384);

    await assertSucceeds(
      setDoc(doc(ownerDb, 'users/uid-long-draft/drafts/long-draft'), {
        id: 'long-draft',
        birthdayId: 'birthday-1',
        personId: 'person-1',
        body,
        tone: 'warm',
        length: 'medium',
        status: 'draft',
        providerType: 'manual',
        variationIndex: 0,
        createdAt: '2026-01-01T00:00:00.000Z',
        updatedAt: '2026-01-01T00:00:00.000Z',
      }),
    );
    const saved = await getDoc(
      doc(ownerDb, 'users/uid-long-draft/drafts/long-draft'),
    );
    expect(saved.data()?.body).toBe(body);
  });

  it('fences owner backup writes while the account is being deleted (F03)', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-fence', { email_verified: true })
      .firestore();

    // Before deletion starts, the owner can write.
    await assertSucceeds(
      setDoc(
        doc(ownerDb, 'users/uid-fence/people/before'),
        personRecord('before', 'Before'),
      ),
    );
    await assertSucceeds(
      setDoc(doc(ownerDb, 'users/uid-fence/backup/current'), backupPointer),
    );

    // The server marks the account DELETING; the owner's backup writes must stop.
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'coordinationPresence/uid-fence'), {
        state: 'DELETING',
      });
    });
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-fence/people/during'),
        personRecord('during', 'During'),
      ),
    );
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-fence/backup/current'), backupPointer),
    );

    // Reads of the owner's own data remain allowed while the fence is up.
    await assertSucceeds(getDoc(doc(ownerDb, 'users/uid-fence/people/before')));

    // Once the fence is lifted, writes resume.
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'coordinationPresence/uid-fence'), {
        state: 'ANDROID_STATE',
      });
    });
    await assertSucceeds(
      setDoc(
        doc(ownerDb, 'users/uid-fence/people/after'),
        personRecord('after', 'After'),
      ),
    );
  });

  it('keeps the deletion fence while only the tombstone remains (A4-005)', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-tombstone', { email_verified: true })
      .firestore();
    await assertSucceeds(
      setDoc(
        doc(ownerDb, 'users/uid-tombstone/people/before'),
        personRecord('before', 'Before'),
      ),
    );
    await assertSucceeds(
      setDoc(doc(ownerDb, 'users/uid-tombstone/backup/current'), backupPointer),
    );

    // The presence document is gone (Auth deletion verified) but the tombstone remains.
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'deletionTombstones/uid-tombstone'), {
        stage: 'VERIFYING',
      });
    });
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-tombstone/people/late'),
        personRecord('late', 'Late'),
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-tombstone/backup/current'),
        backupPointer,
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-tombstone/generations/g1'),
        generationManifest('g1'),
      ),
    );
    await assertSucceeds(
      getDoc(doc(ownerDb, 'users/uid-tombstone/people/before')),
    );

    // Finalization removes the tombstone and lifts the fence.
    await environment.withSecurityRulesDisabled(async (context) => {
      await deleteDoc(doc(context.firestore(), 'deletionTombstones/uid-tombstone'));
    });
    await assertSucceeds(
      setDoc(
        doc(ownerDb, 'users/uid-tombstone/people/after'),
        personRecord('after', 'After'),
      ),
    );
  });

  it('lets owners write immutable backup generations and fences them during deletion (F05)', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-gen', { email_verified: true })
      .firestore();
    const otherDb = environment
      .authenticatedContext('uid-other', { email_verified: true })
      .firestore();

    await assertSucceeds(
      setDoc(
        doc(ownerDb, 'users/uid-gen/generations/g1'),
        generationManifest('g1'),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(ownerDb, 'users/uid-gen/generations/g1/people/p1'),
        personRecord('p1', 'Gen'),
      ),
    );
    await assertFails(
      updateDoc(doc(ownerDb, 'users/uid-gen/generations/g1'), { schemaVersion: 2 }),
    );
    await assertFails(
      updateDoc(doc(ownerDb, 'users/uid-gen/generations/g1/people/p1'), {
        name: 'Gen-Updated',
      }),
    );
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-gen/generations/g1/secrets/x'), { v: 1 }),
    );
    await assertFails(
      setDoc(
        doc(otherDb, 'users/uid-gen/generations/g1/people/p2'),
        personRecord('p2', 'X'),
      ),
    );

    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'coordinationPresence/uid-gen'), {
        state: 'DELETING',
      });
    });
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-gen/generations/g2/people/p3'),
        personRecord('p3', 'Late'),
      ),
    );
  });

  it('allows v2 generation publication with its randomized ID and manifest encoding', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-generation-v2')
      .firestore();
    const generationId = backupPointerV2.generationId;

    await assertSucceeds(
      setDoc(
        doc(ownerDb, `users/uid-generation-v2/generations/${generationId}`),
        generationManifestV2(generationId),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(
          ownerDb,
          `users/uid-generation-v2/generations/${generationId}/people/p1`,
        ),
        personRecord('p1', 'V2 person'),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(ownerDb, 'users/uid-generation-v2/backup/current'),
        backupPointerV2,
      ),
    );
    await assertSucceeds(
      getDoc(doc(ownerDb, 'users/uid-generation-v2/backup/current')),
    );
    await assertSucceeds(
      getDoc(
        doc(ownerDb, `users/uid-generation-v2/generations/${generationId}`),
      ),
    );
  });

  it('continues to permit owner restore reads for a published v1 snapshot', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-generation-v1-restore')
      .firestore();
    const generationId = 'g1780000000000000';

    await environment.withSecurityRulesDisabled(async context => {
      const adminDb = context.firestore();
      await setDoc(
        doc(
          adminDb,
          `users/uid-generation-v1-restore/generations/${generationId}`,
        ),
        generationManifest(generationId),
      );
      await setDoc(
        doc(
          adminDb,
          `users/uid-generation-v1-restore/generations/${generationId}/people/p1`,
        ),
        personRecord('p1', 'Legacy person'),
      );
      await setDoc(
        doc(adminDb, 'users/uid-generation-v1-restore/backup/current'),
        {
          ...backupPointer,
          generationId,
        },
      );
    });

    await assertSucceeds(
      getDoc(doc(ownerDb, 'users/uid-generation-v1-restore/backup/current')),
    );
    await assertSucceeds(
      getDoc(
        doc(
          ownerDb,
          `users/uid-generation-v1-restore/generations/${generationId}`,
        ),
      ),
    );
    await assertSucceeds(
      getDoc(
        doc(
          ownerDb,
          `users/uid-generation-v1-restore/generations/${generationId}/people/p1`,
        ),
      ),
    );
  });

  it('rejects malformed v2 manifests, pointers, and randomized IDs', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-generation-v2-invalid')
      .firestore();
    const validId = 'g1780000000000000-a1b2c3d4';
    const badEncoding = {
      ...generationManifestV2(validId),
      birthdayDateEncoding: 'unknown-encoding',
    };
    const extraManifestField = {
      ...generationManifestV2(validId),
      extra: true,
    };
    const missingEncoding = { ...generationManifest(validId), schemaVersion: 2 };

    await assertFails(
      setDoc(
        doc(ownerDb, `users/uid-generation-v2-invalid/generations/${validId}`),
        badEncoding,
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, `users/uid-generation-v2-invalid/generations/${validId}`),
        extraManifestField,
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, `users/uid-generation-v2-invalid/generations/${validId}`),
        missingEncoding,
      ),
    );
    const numericLegacyId = 'g1780000000000000';
    await assertFails(
      setDoc(
        doc(ownerDb, `users/uid-generation-v2-invalid/generations/${numericLegacyId}`),
        generationManifestV2(numericLegacyId),
      ),
    );
    // Generation documents are uploaded before their manifest is published.
    // Numeric IDs remain valid for v1 child writes; the manifest enforces that
    // a published v2 generation uses the randomized suffix.
    await assertSucceeds(
      setDoc(
        doc(
          ownerDb,
          `users/uid-generation-v2-invalid/generations/${numericLegacyId}/people/p1`,
        ),
        personRecord('p1', 'Unpublished legacy generation child'),
      ),
    );
    await assertFails(
      setDoc(
        doc(
          ownerDb,
          'users/uid-generation-v2-invalid/generations/g1780000000000000-ABCDEF/people/p2',
        ),
        personRecord('p2', 'Uppercase suffix'),
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-generation-v2-invalid/backup/current'),
        { ...backupPointerV2, generationId: 'g1780000000000000' },
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-generation-v2-invalid/backup/current'),
        { ...backupPointer, generationId: validId },
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-generation-v2-invalid/backup/current'),
        {
          ...backupPointerV2,
          generationId: 'g1780000000000000-a1b2c3d4f',
        },
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-generation-v2-invalid/backup/current'),
        { ...backupPointerV2, extra: true },
      ),
    );
  });

  it('rejects malformed, oversized, and nested backup writes', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-validation')
      .firestore();
    const malformed = {
      ...personRecord('person-1', 'Valid'),
      extraField: 'not in the backup schema',
    };
    const oversized = personRecord('person-2', 'x'.repeat(201));

    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-validation/people/person-1'), malformed),
    );
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-validation/people/person-2'), oversized),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-validation/people/person-3/nested/doc'),
        personRecord('person-3', 'Nested'),
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-validation/backup/other'),
        backupPointer,
      ),
    );
    await assertFails(
      setDoc(
        doc(ownerDb, 'users/uid-validation/generations/not-a-generation/people/person-4'),
        personRecord('person-4', 'Invalid generation'),
      ),
    );
  });
});
