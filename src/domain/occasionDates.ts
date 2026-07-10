import type {
  EventType,
  RelationshipEvent,
  YearlyOccasionRecurrence
} from './types';

const yearlyEventTypes = new Set<EventType>(['Birthday', 'Anniversary', 'Work anniversary']);

const validDate = (value: string | Date) => {
  const parsed = value instanceof Date ? new Date(value) : new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed;
};

export const utcDateKey = (value: string | Date): string | undefined => {
  const parsed = validDate(value);
  return parsed?.toISOString().slice(0, 10);
};

export const localDateKey = (value: Date): string | undefined => {
  const parsed = validDate(value);
  if (!parsed) {
    return undefined;
  }
  return `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(
    parsed.getDate()
  ).padStart(2, '0')}`;
};

export const recurrenceFromDate = (
  type: EventType,
  dateIso: string
): YearlyOccasionRecurrence | undefined => {
  if (!yearlyEventTypes.has(type)) {
    return undefined;
  }
  const date = validDate(dateIso);
  if (!date) {
    return undefined;
  }
  return {
    frequency: 'Yearly',
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
    originalYear: date.getUTCFullYear(),
    leapDayPolicy: 'February 28'
  };
};

export const recurrenceForEvent = (
  event: RelationshipEvent
): YearlyOccasionRecurrence | undefined => event.recurrence ?? recurrenceFromDate(event.type, event.date);

const isLeapYear = (year: number) => year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);

const occurrenceParts = (recurrence: YearlyOccasionRecurrence, year: number) => {
  if (recurrence.month === 2 && recurrence.day === 29 && !isLeapYear(year)) {
    return recurrence.leapDayPolicy === 'March 1'
      ? { month: 3, day: 1 }
      : { month: 2, day: 28 };
  }
  return { month: recurrence.month, day: recurrence.day };
};

export const yearlyOccurrenceIso = (
  recurrence: YearlyOccasionRecurrence,
  year: number
): string | undefined => {
  if (
    !Number.isInteger(year) ||
    !Number.isInteger(recurrence.month) ||
    !Number.isInteger(recurrence.day) ||
    recurrence.month < 1 ||
    recurrence.month > 12 ||
    recurrence.day < 1 ||
    recurrence.day > 31
  ) {
    return undefined;
  }
  const parts = occurrenceParts(recurrence, year);
  const date = new Date(Date.UTC(year, parts.month - 1, parts.day, 12, 0, 0));
  if (date.getUTCMonth() !== parts.month - 1 || date.getUTCDate() !== parts.day) {
    return undefined;
  }
  return date.toISOString();
};

/** Resolves the occurrence on or after the reference local-calendar day. */
export const eventOccurrenceIso = (
  event: RelationshipEvent,
  reference: Date = new Date()
): string | undefined => {
  const recurrence = recurrenceForEvent(event);
  if (!recurrence) {
    return validDate(event.date)?.toISOString();
  }
  const referenceKey = localDateKey(reference);
  if (!referenceKey) {
    return undefined;
  }
  const year = reference.getFullYear();
  const currentYear = yearlyOccurrenceIso(recurrence, year);
  if (currentYear && currentYear.slice(0, 10) >= referenceKey) {
    return currentYear;
  }
  return yearlyOccurrenceIso(recurrence, year + 1);
};

export const materializeEventOccurrence = (
  event: RelationshipEvent,
  reference: Date = new Date()
): RelationshipEvent => ({
  ...event,
  date: eventOccurrenceIso(event, reference) ?? event.date,
  recurrence: recurrenceForEvent(event)
});

export const eventOccurrenceInYear = (
  event: RelationshipEvent,
  year: number
): RelationshipEvent | undefined => {
  const recurrence = recurrenceForEvent(event);
  if (!recurrence) {
    const date = validDate(event.date);
    return date?.getUTCFullYear() === year ? event : undefined;
  }
  const date = yearlyOccurrenceIso(recurrence, year);
  return date
    ? {
        ...event,
        date,
        recurrence
      }
    : undefined;
};

export const withCanonicalRecurrence = (event: RelationshipEvent): RelationshipEvent => ({
  ...event,
  recurrence: recurrenceForEvent(event)
});
