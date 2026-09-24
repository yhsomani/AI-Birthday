import { readFileSync } from 'node:fs';

import {
  assertFails,
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

describe('server-only Firestore rules', () => {
  it('denies unauthenticated direct reads and writes', async () => {
    const db = environment.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'globalControl/current')));
    await assertFails(
      setDoc(doc(db, 'accounts/uid-one'), { mode: 'TEST_ONLY' }),
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

  it('denies authenticated access to every AI gateway collection', async () => {
    const db = environment
      .authenticatedContext('uid-one', { email_verified: true })
      .firestore();
    // Entitlement projection is materialised server-side only.
    await assertFails(getDoc(doc(db, 'users/uid-one/meta/aiEntitlement')));
    await assertFails(
      setDoc(doc(db, 'users/uid-one/meta/aiEntitlement'), { tier: 'PRO' }),
    );
    // Usage ledger and per-period summaries are append-only via Functions.
    await assertFails(getDoc(doc(db, 'users/uid-one/aiUsage/entry-1')));
    await assertFails(
      setDoc(doc(db, 'users/uid-one/aiUsage/summary-2026-09'), { requests: 99 }),
    );
    // Global budget guard is admin-only.
    await assertFails(getDoc(doc(db, 'aiGlobalBudget/current')));
    await assertFails(setDoc(doc(db, 'aiGlobalBudget/current'), { requests: 1 }));
    // Billing event stream is written exclusively by the RTDN ingester.
    await assertFails(getDoc(doc(db, 'users/uid-one/events/playBilling/e1')));
    await assertFails(
      setDoc(doc(db, 'users/uid-one/events/playBilling/e1'), { sku: 'x' }),
    );
  });

  it('denies even admin-privileged client SDK paths outside service accounts', async () => {
    // A forged request that targets another user's entitlement must fail too.
    const db = environment
      .authenticatedContext('uid-two', { email_verified: true })
      .firestore();
    await assertFails(getDoc(doc(db, 'users/uid-one/meta/aiEntitlement')));
    await assertFails(getDoc(doc(db, 'users/uid-one/aiUsage/entry-1')));
  });
});
