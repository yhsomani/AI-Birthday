import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  allocateCommandMetadata,
  commandId,
  createCollisionResistantIdGenerator,
  createFixedClock,
  localCalendarYear,
  localDate,
  type IdGenerator
} from './commandMetadata';

const sequentialIds = (): IdGenerator => {
  let sequence = 0;
  return {
    nextId: kind => `${kind}-fixture-${++sequence}`
  };
};

describe('command metadata boundary', () => {
  it('captures one injected clock reading and final IDs before transition', () => {
    const metadata = allocateCommandMetadata(
      {
        clock: createFixedClock('2026-12-31T18:45:00.000Z', '2027-01-01'),
        idGenerator: sequentialIds()
      },
      { activity: 1, contact: 2, event: 2 }
    );

    assert.equal(metadata.occurredAt, '2026-12-31T18:45:00.000Z');
    assert.equal(metadata.localDate, '2027-01-01');
    assert.equal(localCalendarYear(metadata.localDate), 2027);
    assert.equal(commandId(metadata, 'activity'), 'activity-fixture-1');
    assert.deepEqual(metadata.ids.contact, ['contact-fixture-2', 'contact-fixture-3']);
    assert.deepEqual(metadata.ids.event, ['event-fixture-4', 'event-fixture-5']);
  });

  it('does not use timestamp-only IDs when many records are created in one instant', () => {
    const generator = createCollisionResistantIdGenerator();
    const ids = Array.from({ length: 10_000 }, () => generator.nextId('message'));

    assert.equal(new Set(ids).size, ids.length);
    assert.ok(ids.every(id => id.startsWith('message-')));
  });

  it('validates calendar dates independently from instants', () => {
    assert.equal(localDate('2028-02-29'), '2028-02-29');
    assert.throws(() => localDate('2027-02-29'), /Invalid LocalDate/);
  });
});
