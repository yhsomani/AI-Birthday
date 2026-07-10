import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import {
  MAX_EVENT_IMPORT_BYTES,
  MAX_EVENT_IMPORT_COLUMNS,
  MAX_EVENT_IMPORT_FIELD_LENGTH,
  normalizeImportDate,
  parseEventImportText
} from './eventImport';

const now = new Date('2026-07-09T10:00:00.000Z');

describe('event file import contract', () => {
  it('parses CSV rows into calendar import candidates without creating events directly', () => {
    const result = parseEventImportText(
      [
        'name,type,date,notes',
        'Nisha Rao,Birthday,2026-12-05,"Loves handwritten notes"',
        'Aman Shah,Anniversary,2026-11-20,College friend'
      ].join('\n'),
      'csv',
      now
    );

    assert.equal(result.candidates.length, 2);
    assert.equal(result.skipped, 0);
    assert.equal(result.candidates[0].title, 'Nisha Rao Birthday');
    assert.match(result.candidates[0].startDate, /^2026-12-05T09:00:00\.000Z$/);
    assert.equal(result.candidates[0].notes, 'Loves handwritten notes');
  });

  it('parses vCard birthdays and anniversaries as separate review candidates', () => {
    const result = parseEventImportText(
      [
        'BEGIN:VCARD',
        'VERSION:4.0',
        'FN:Dev Kapoor',
        'BDAY:20261114',
        'ANNIVERSARY:2026-12-01',
        'NOTE:Confirm before messaging',
        'END:VCARD'
      ].join('\n'),
      'vcard',
      now
    );

    assert.deepEqual(
      result.candidates.map(candidate => candidate.title),
      ['Dev Kapoor Birthday', 'Dev Kapoor Anniversary']
    );
    assert.ok(result.candidates.every(candidate => candidate.notes === 'Confirm before messaging'));
  });

  it('reports skipped rows without importing invalid or incomplete content', () => {
    const result = parseEventImportText(
      'name,type,date\nMissing Date,Birthday,\nBad Date,Birthday,not-a-date',
      'csv',
      now
    );

    assert.equal(result.candidates.length, 0);
    assert.equal(result.skipped, 2);
    assert.equal(result.errors.length, 2);
  });

  it('normalizes partial yearly dates to the next occurrence', () => {
    assert.equal(normalizeImportDate('--0708', now), '2027-07-08T09:00:00.000Z');
    assert.equal(normalizeImportDate('12-05', now), '2026-12-05T09:00:00.000Z');
  });

  it('bounds bytes, columns, and field lengths before producing review candidates', () => {
    const oversized = parseEventImportText('x'.repeat(MAX_EVENT_IMPORT_BYTES + 1), 'csv');
    assert.equal(oversized.candidates.length, 0);
    assert.match(oversized.errors[0], /no larger/i);

    const cells = Array.from({ length: MAX_EVENT_IMPORT_COLUMNS + 1 }, (_, index) => `h${index}`);
    const tooManyColumns = parseEventImportText(`${cells.join(',')}\n${cells.join(',')}`, 'csv');
    assert.match(tooManyColumns.errors[0], /columns/i);

    const longField = parseEventImportText(
      `name,date\n${'x'.repeat(MAX_EVENT_IMPORT_FIELD_LENGTH + 1)},2026-08-01`,
      'csv'
    );
    assert.match(longField.errors[0], /fields/i);
  });

  it('feeds parsed files into the existing review-first calendar import reducer path', () => {
    const state = createTestState();
    const parsed = parseEventImportText('name,type,date\nNisha Rao,Birthday,2026-12-05', 'csv', now);
    const next = relateReducer(state, { type: 'calendarImported', candidates: parsed.candidates });
    const importedEvent = next.events.find(event => event.label === 'Nisha Rao Birthday');

    assert.equal(next.calendarSync.importedCount, state.calendarSync.importedCount + 1);
    assert.equal(importedEvent?.verified, false);
    assert.equal(importedEvent?.source, 'Imported');
    assert.ok(importedEvent?.checklist.some(item => /Confirm imported date/.test(item.label)));
  });
});
