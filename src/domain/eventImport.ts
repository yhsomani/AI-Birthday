import type { CalendarImportCandidate } from './types';

export type EventImportFormat = 'auto' | 'csv' | 'vcard';

export type EventImportParseResult = {
  candidates: CalendarImportCandidate[];
  skipped: number;
  errors: string[];
};

export const MAX_EVENT_IMPORT_BYTES = 2 * 1024 * 1024;
export const MAX_EVENT_IMPORT_ROWS = 5_000;
export const MAX_EVENT_IMPORT_COLUMNS = 32;
export const MAX_EVENT_IMPORT_FIELD_LENGTH = 4_096;
export const MAX_EVENT_IMPORT_ERRORS = 100;

const inputByteLength = (value: string) => new TextEncoder().encode(value).byteLength;

const normalizeHeader = (value: string) => value.trim().toLowerCase().replace(/[^a-z0-9]/g, '');

const slug = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48) || 'event';

const isoAtReviewHour = (year: number, month: number, day: number) => {
  const date = new Date(Date.UTC(year, month - 1, day, 9, 0, 0, 0));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    return undefined;
  }
  return date.toISOString();
};

const parsePartialDate = (month: number, day: number, now: Date) => {
  const thisYear = isoAtReviewHour(now.getFullYear(), month, day);
  if (!thisYear) {
    return undefined;
  }
  return new Date(thisYear).getTime() < now.getTime()
    ? isoAtReviewHour(now.getFullYear() + 1, month, day)
    : thisYear;
};

export const normalizeImportDate = (value: string | undefined, now = new Date()): string | undefined => {
  const trimmed = value?.trim();
  if (!trimmed) {
    return undefined;
  }

  const isoDate = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (isoDate) {
    return isoAtReviewHour(Number(isoDate[1]), Number(isoDate[2]), Number(isoDate[3]));
  }

  const compactDate = trimmed.match(/^(\d{4})(\d{2})(\d{2})$/);
  if (compactDate) {
    return isoAtReviewHour(Number(compactDate[1]), Number(compactDate[2]), Number(compactDate[3]));
  }

  const slashDate = trimmed.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/);
  if (slashDate) {
    const first = Number(slashDate[1]);
    const second = Number(slashDate[2]);
    const year = Number(slashDate[3]);
    const month = first > 12 ? second : first;
    const day = first > 12 ? first : second;
    return isoAtReviewHour(year, month, day);
  }

  const vcardPartial = trimmed.match(/^--?(\d{2})(\d{2})$/);
  if (vcardPartial) {
    return parsePartialDate(Number(vcardPartial[1]), Number(vcardPartial[2]), now);
  }

  const monthDay = trimmed.match(/^(\d{1,2})-(\d{1,2})$/);
  if (monthDay) {
    return parsePartialDate(Number(monthDay[1]), Number(monthDay[2]), now);
  }

  const parsed = new Date(trimmed);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
};

const parseCsvRows = (raw: string): { rows: string[][]; error?: string } => {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;

  for (let index = 0; index < raw.length; index += 1) {
    const char = raw[index];
    const next = raw[index + 1];
    if (quoted) {
      if (char === '"' && next === '"') {
        cell += '"';
        index += 1;
      } else if (char === '"') {
        quoted = false;
      } else {
        cell += char;
      }
      continue;
    }
    if (char === '"') {
      quoted = true;
    } else if (char === ',') {
      row.push(cell.trim());
      cell = '';
    } else if (char === '\n') {
      row.push(cell.trim());
      if (row.length > MAX_EVENT_IMPORT_COLUMNS) {
        return { rows: [], error: `CSV rows may contain at most ${MAX_EVENT_IMPORT_COLUMNS} columns.` };
      }
      rows.push(row);
      if (rows.length > MAX_EVENT_IMPORT_ROWS + 1) {
        return { rows: [], error: `CSV imports may contain at most ${MAX_EVENT_IMPORT_ROWS} event rows.` };
      }
      row = [];
      cell = '';
    } else if (char !== '\r') {
      cell += char;
    }
    if (cell.length > MAX_EVENT_IMPORT_FIELD_LENGTH) {
      return { rows: [], error: `CSV fields may contain at most ${MAX_EVENT_IMPORT_FIELD_LENGTH} characters.` };
    }
  }

  if (cell.length > 0 || row.length > 0) {
    row.push(cell.trim());
    if (row.length > MAX_EVENT_IMPORT_COLUMNS) {
      return { rows: [], error: `CSV rows may contain at most ${MAX_EVENT_IMPORT_COLUMNS} columns.` };
    }
    rows.push(row);
  }
  if (rows.length > MAX_EVENT_IMPORT_ROWS + 1) {
    return { rows: [], error: `CSV imports may contain at most ${MAX_EVENT_IMPORT_ROWS} event rows.` };
  }

  const filteredRows = rows.filter(item => item.some(cellValue => cellValue.length > 0));
  if (filteredRows.some(item => item.some(cellValue => cellValue.length > MAX_EVENT_IMPORT_FIELD_LENGTH))) {
    return { rows: [], error: `CSV fields may contain at most ${MAX_EVENT_IMPORT_FIELD_LENGTH} characters.` };
  }
  return { rows: filteredRows };
};

const valueFor = (row: string[], headers: Map<string, number>, names: string[]) => {
  for (const name of names) {
    const index = headers.get(name);
    if (index !== undefined) {
      const value = row[index]?.trim();
      if (value) {
        return value;
      }
    }
  }
  return undefined;
};

export const parseCsvEventImport = (raw: string, now = new Date()): EventImportParseResult => {
  if (inputByteLength(raw) > MAX_EVENT_IMPORT_BYTES) {
    return { candidates: [], skipped: 0, errors: [`Event import must be no larger than ${MAX_EVENT_IMPORT_BYTES} bytes.`] };
  }
  const parsedRows = parseCsvRows(raw.replace(/^\uFEFF/, ''));
  if (parsedRows.error) {
    return { candidates: [], skipped: 0, errors: [parsedRows.error] };
  }
  const rows = parsedRows.rows;
  if (rows.length < 2) {
    return {
      candidates: [],
      skipped: rows.length,
      errors: ['CSV import needs a header row and at least one event row.']
    };
  }

  const headers = new Map(rows[0].map((header, index) => [normalizeHeader(header), index]));
  const candidates: CalendarImportCandidate[] = [];
  const errors: string[] = [];
  let skipped = 0;

  rows.slice(1).forEach((row, rowIndex) => {
    const rowNumber = rowIndex + 2;
    const sourceId = valueFor(row, headers, ['id', 'sourceid', 'source']) ?? `csv-row-${rowNumber}`;
    const name = valueFor(row, headers, ['name', 'contact', 'person', 'fn']);
    const type = valueFor(row, headers, ['type', 'occasion', 'eventtype']) ?? 'Birthday';
    const title = valueFor(row, headers, ['title', 'event', 'summary']) ?? (name ? `${name} ${type}` : undefined);
    const rawDate = valueFor(row, headers, [
      'date',
      'startdate',
      'start',
      'birthday',
      'bday',
      'anniversary',
      'eventdate'
    ]);
    const startDate = normalizeImportDate(rawDate, now);
    const notes = valueFor(row, headers, ['notes', 'note', 'description']);

    if (!title || !startDate) {
      skipped += 1;
      if (errors.length < MAX_EVENT_IMPORT_ERRORS) {
        errors.push(`Row ${rowNumber} was skipped because it needs a title/name and a valid date.`);
      }
      return;
    }

    candidates.push({
      sourceId: `${sourceId}-${slug(title)}`,
      title,
      startDate,
      notes
    });
  });

  return {
    candidates,
    skipped,
    errors
  };
};

const unfoldVcardLines = (raw: string) => {
  const lines: string[] = [];
  for (const line of raw.replace(/\r/g, '').split('\n')) {
    if (/^[ \t]/.test(line) && lines.length > 0) {
      lines[lines.length - 1] += line.slice(1);
    } else {
      lines.push(line.trim());
    }
  }
  return lines;
};

const parseVcardValue = (line: string) => {
  const separator = line.indexOf(':');
  if (separator < 0) {
    return undefined;
  }
  return {
    key: line.slice(0, separator).split(';')[0].toUpperCase(),
    value: line.slice(separator + 1).trim()
  };
};

export const parseVcardEventImport = (raw: string, now = new Date()): EventImportParseResult => {
  if (inputByteLength(raw) > MAX_EVENT_IMPORT_BYTES) {
    return { candidates: [], skipped: 0, errors: [`Event import must be no larger than ${MAX_EVENT_IMPORT_BYTES} bytes.`] };
  }
  const lines = unfoldVcardLines(raw);
  const blocks: string[][] = [];
  let current: string[] = [];

  for (const line of lines) {
    if (line.toUpperCase() === 'BEGIN:VCARD') {
      current = [];
    } else if (line.toUpperCase() === 'END:VCARD') {
      blocks.push(current);
      if (blocks.length > MAX_EVENT_IMPORT_ROWS) {
        return {
          candidates: [],
          skipped: 0,
          errors: [`vCard imports may contain at most ${MAX_EVENT_IMPORT_ROWS} cards.`]
        };
      }
      current = [];
    } else if (current) {
      current.push(line);
    }
  }

  const candidates: CalendarImportCandidate[] = [];
  const errors: string[] = [];
  let skipped = 0;

  blocks.forEach((block, index) => {
    const values = new Map<string, string>();
    for (const line of block) {
      const parsed = parseVcardValue(line);
      if (parsed?.value && !values.has(parsed.key)) {
        values.set(parsed.key, parsed.value.replace(/\\,/g, ',').replace(/\\n/gi, ' '));
      }
    }

    const name =
      values.get('FN') ??
      values
        .get('N')
        ?.split(';')
        .filter(Boolean)
        .reverse()
        .join(' ')
        .trim();
    const birthday = normalizeImportDate(values.get('BDAY'), now);
    const anniversary = normalizeImportDate(values.get('ANNIVERSARY'), now);
    const note = values.get('NOTE');

    if (!name || (!birthday && !anniversary)) {
      skipped += 1;
      if (errors.length < MAX_EVENT_IMPORT_ERRORS) {
        errors.push(`vCard ${index + 1} was skipped because it needs a name and birthday or anniversary.`);
      }
      return;
    }

    if (birthday) {
      candidates.push({
        sourceId: `vcard-${index + 1}-${slug(name)}-birthday`,
        title: `${name} Birthday`,
        startDate: birthday,
        notes: note
      });
    }
    if (anniversary) {
      candidates.push({
        sourceId: `vcard-${index + 1}-${slug(name)}-anniversary`,
        title: `${name} Anniversary`,
        startDate: anniversary,
        notes: note
      });
    }
  });

  return {
    candidates,
    skipped,
    errors
  };
};

export const parseEventImportText = (
  raw: string,
  format: EventImportFormat = 'auto',
  now = new Date()
): EventImportParseResult => {
  const trimmed = raw.trim();
  if (!trimmed) {
    return {
      candidates: [],
      skipped: 0,
      errors: ['Add CSV or vCard text before importing.']
    };
  }
  if (inputByteLength(trimmed) > MAX_EVENT_IMPORT_BYTES) {
    return {
      candidates: [],
      skipped: 0,
      errors: [`Event import must be no larger than ${MAX_EVENT_IMPORT_BYTES} bytes.`]
    };
  }

  const resolvedFormat =
    format === 'auto' ? (/\bBEGIN:VCARD\b/i.test(trimmed) ? 'vcard' : 'csv') : format;
  return resolvedFormat === 'vcard'
    ? parseVcardEventImport(trimmed, now)
    : parseCsvEventImport(trimmed, now);
};
