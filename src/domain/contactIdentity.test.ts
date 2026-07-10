import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import {
  allContactRoutes,
  contactMatchesImportedRoute,
  importedContactRoutes,
  mergeImportedIdentity
} from './contactIdentity';

describe('contact identity and route model', () => {
  it('normalizes and deduplicates every imported phone and email route', () => {
    const routes = importedContactRoutes({
      sourceId: 'source-1',
      name: 'Contact',
      phones: ['+91 90000 11111', '0091 90000 11111', '+91 90000 22222'],
      emails: ['One@Example.com', 'one@example.com', 'two@example.com']
    });
    assert.deepEqual(
      routes.map(route => route.value),
      ['+919000011111', '+919000022222', 'one@example.com', 'two@example.com']
    );
    assert.equal(routes.filter(route => route.primary).length, 2);
  });

  it('accepts only normalized imported phone identities containing 7 to 15 digits', () => {
    const routes = importedContactRoutes({
      sourceId: 'phone-bounds',
      name: 'Phone Bounds',
      phones: ['123456', '1234567', '+12 345 678 901 234 5', '+12 345 678 901 234 56', '00 91 90000 11111']
    });

    assert.deepEqual(
      routes.filter(route => route.type === 'Phone').map(route => route.value),
      ['1234567', '+123456789012345', '+919000011111']
    );
  });

  it('matches exact routes or source identities without treating a name as identity', () => {
    const contact = createTestState().contacts[0];
    assert.equal(
      contactMatchesImportedRoute(contact, {
        sourceId: 'different-source',
        name: 'Different Name',
        phone: contact.phone
      }),
      true
    );
    assert.equal(
      contactMatchesImportedRoute(contact, {
        sourceId: 'different-source',
        name: contact.name,
        phone: '+91 90000 00000'
      }),
      false
    );
  });

  it('merges routes and source IDs without discarding legacy primary routes', () => {
    const contact = createTestState().contacts[0];
    const merged = mergeImportedIdentity(contact, {
      sourceId: 'device-asha',
      name: contact.name,
      phones: ['+91 90000 22222'],
      emails: ['new@example.com']
    });
    assert.ok(allContactRoutes(merged).some(route => route.value === '+919876543210'));
    assert.ok(allContactRoutes(merged).some(route => route.value === '+919000022222'));
    assert.ok(merged.sourceIdentities?.some(identity => identity.sourceId === 'device-asha'));
  });
});
