import { readFileSync } from 'node:fs';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc } from 'firebase/firestore';
import { afterAll, beforeAll, describe, it } from 'vitest';

let environment: RulesTestEnvironment;

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

    await setDoc(doc(ownerDb, 'users/uid-one/people/person-1'), {
      name: 'Test',
    });

    await assertFails(getDoc(doc(otherDb, 'users/uid-one/people/person-1')));
    await assertFails(
      setDoc(doc(otherDb, 'users/uid-one/people/person-1'), { name: 'Other' }),
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

  it('fences owner backup writes while the account is being deleted (F03)', async () => {
    const ownerDb = environment
      .authenticatedContext('uid-fence', { email_verified: true })
      .firestore();

    // Before deletion starts, the owner can write.
    await assertSucceeds(
      setDoc(doc(ownerDb, 'users/uid-fence/people/before'), { name: 'Before' }),
    );

    // The server marks the account DELETING; the owner's backup writes must stop.
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'coordinationPresence/uid-fence'), {
        state: 'DELETING',
      });
    });
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-fence/people/during'), { name: 'During' }),
    );
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-fence/backup/during'), { name: 'During' }),
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
      setDoc(doc(ownerDb, 'users/uid-fence/people/after'), { name: 'After' }),
    );
  });
});
