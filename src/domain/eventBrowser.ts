import type { EventType, RelationshipEvent } from './types';
import {
  eventOccurrenceInYear,
  materializeEventOccurrence
} from './occasionDates';

export type EventTypeFilter = 'All' | EventType;
export type EventTimeFilter = 'All' | 'Upcoming' | 'Past' | 'This month';

export type EventBrowserFilters = {
  type: EventTypeFilter;
  time: EventTimeFilter;
  nowIso: string;
  monthIso?: string;
};

export type EventMonthDay = {
  dateKey: string;
  dayOfMonth: number;
  inMonth: boolean;
  events: RelationshipEvent[];
};

export type EventMonthView = {
  year: number;
  monthIndex: number;
  monthKey: string;
  label: string;
  days: EventMonthDay[];
};

const dateKey = (iso: string) => iso.slice(0, 10);

const monthKey = (date: Date) =>
  `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;

const parseIso = (iso: string) => {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? undefined : date;
};

export const sortEventsByDate = (events: RelationshipEvent[], reference: Date = new Date()) =>
  events.map(event => materializeEventOccurrence(event, reference)).sort((a, b) => {
    const byDate = a.date.localeCompare(b.date);
    return byDate === 0 ? a.label.localeCompare(b.label) : byDate;
  });

export const filterRelationshipEvents = (
  events: RelationshipEvent[],
  filters: EventBrowserFilters
) => {
  const nowDate = parseIso(filters.nowIso) ?? new Date();
  const nowKey = dateKey(nowDate.toISOString());
  const activeMonth = filters.monthIso ? parseIso(filters.monthIso) : nowDate;
  const activeMonthKey = activeMonth ? monthKey(activeMonth) : monthKey(nowDate);

  return sortEventsByDate(
    events.map(event => materializeEventOccurrence(event, nowDate)).filter(event => {
      const eventKey = dateKey(event.date);
      const typeMatches = filters.type === 'All' || event.type === filters.type;
      const timeMatches =
        filters.time === 'All' ||
        (filters.time === 'Upcoming' && eventKey >= nowKey) ||
        (filters.time === 'Past' && eventKey < nowKey) ||
        (filters.time === 'This month' && eventKey.startsWith(activeMonthKey));

      return typeMatches && timeMatches;
    }),
    nowDate
  );
};

export const buildEventMonthView = (
  events: RelationshipEvent[],
  anchorIso: string
): EventMonthView => {
  const anchor = parseIso(anchorIso) ?? new Date();
  const year = anchor.getUTCFullYear();
  const monthIndex = anchor.getUTCMonth();
  const firstOfMonth = new Date(Date.UTC(year, monthIndex, 1, 12, 0, 0));
  const firstWeekday = firstOfMonth.getUTCDay();
  const gridStart = new Date(firstOfMonth);
  gridStart.setUTCDate(firstOfMonth.getUTCDate() - firstWeekday);
  const gridEnd = new Date(gridStart);
  gridEnd.setUTCDate(gridStart.getUTCDate() + 41);
  const years = [...new Set([gridStart.getUTCFullYear(), gridEnd.getUTCFullYear()])];
  const occurrences = events.flatMap(event =>
    years.flatMap(year => {
      const occurrence = eventOccurrenceInYear(event, year);
      return occurrence ? [occurrence] : [];
    })
  );
  const eventsByDate = occurrences.reduce<Record<string, RelationshipEvent[]>>((acc, event) => {
    const key = dateKey(event.date);
    acc[key] = [...(acc[key] ?? []), event];
    return acc;
  }, {});

  const days = Array.from({ length: 42 }, (_, index) => {
    const current = new Date(gridStart);
    current.setUTCDate(gridStart.getUTCDate() + index);
    const key = dateKey(current.toISOString());
    return {
      dateKey: key,
      dayOfMonth: current.getUTCDate(),
      inMonth: current.getUTCMonth() === monthIndex,
      events: sortEventsByDate(eventsByDate[key] ?? [], current)
    };
  });

  return {
    year,
    monthIndex,
    monthKey: monthKey(firstOfMonth),
    label: firstOfMonth.toLocaleDateString('en-IN', {
      month: 'long',
      year: 'numeric',
      timeZone: 'UTC'
    }),
    days
  };
};

export const shiftMonth = (anchorIso: string, offset: number) => {
  const anchor = parseIso(anchorIso) ?? new Date();
  const shifted = new Date(Date.UTC(anchor.getUTCFullYear(), anchor.getUTCMonth() + offset, 1, 12, 0, 0));
  return shifted.toISOString();
};
