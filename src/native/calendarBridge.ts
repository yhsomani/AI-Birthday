import {
  buildCalendarExportEntries,
  buildCalendarExportPlan,
  resolveCalendarExportSelection,
  type MirroredCalendarEvent
} from '../domain/calendarSync';
import type { AppState, CalendarExportEntry, CalendarImportCandidate } from '../domain/types';
import { throwIfAborted } from './abort';

const RELATEAI_CALENDAR_TITLE = 'RelateAI Relationship Events';

export type CalendarBridgeApi = Pick<
  typeof import('expo-calendar'),
  | 'requestCalendarPermissions'
  | 'getCalendars'
  | 'createCalendar'
  | 'listEvents'
  | 'EntityTypes'
  | 'SourceType'
  | 'CalendarAccessLevel'
  | 'Frequency'
>;

type DeviceCalendar = Awaited<ReturnType<CalendarBridgeApi['getCalendars']>>[number];
type DeviceCalendarEvent = Awaited<ReturnType<DeviceCalendar['listEvents']>>[number];
type CalendarEntryWithNativeSemantics = CalendarExportEntry & {
  allDay?: boolean;
  recurrenceRule?: {
    frequency: 'yearly';
  };
};

export interface CalendarBridgeOperationOptions {
  signal?: AbortSignal;
  eventIds?: readonly string[];
}

const cancellationGate = (signal: AbortSignal | undefined) => {
  let nativeCommitStarted = false;
  return {
    checkpoint: () => {
      if (!nativeCommitStarted) throwIfAborted(signal);
    },
    beginNativeCommit: () => {
      if (!nativeCommitStarted) {
        throwIfAborted(signal);
        nativeCommitStarted = true;
      }
    }
  };
};

const getOrCreateRelateCalendar = async (
  Calendar: CalendarBridgeApi,
  gate: ReturnType<typeof cancellationGate>
): Promise<DeviceCalendar> => {
  gate.checkpoint();
  const calendars = await Calendar.getCalendars(Calendar.EntityTypes.EVENT);
  gate.checkpoint();
  const existing = calendars.find(calendar => calendar.title === RELATEAI_CALENDAR_TITLE);
  if (existing) {
    return existing;
  }

  const defaultCalendar = calendars.find(calendar => calendar.allowsModifications);
  const source = defaultCalendar?.source ?? {
    id: 'relateai-local',
    name: RELATEAI_CALENDAR_TITLE,
    type: Calendar.SourceType.LOCAL,
    isLocalAccount: true
  };

  gate.beginNativeCommit();
  return Calendar.createCalendar({
    title: RELATEAI_CALENDAR_TITLE,
    color: '#176b5b',
    entityType: Calendar.EntityTypes.EVENT,
    source,
    name: RELATEAI_CALENDAR_TITLE,
    ownerAccount: 'personal',
    accessLevel: Calendar.CalendarAccessLevel.OWNER
  });
};

const exportRangeFor = (entries: ReturnType<typeof buildCalendarExportEntries>) => {
  const now = new Date();
  const startDate = new Date(now);
  startDate.setFullYear(now.getFullYear() - 1);
  const endDate = new Date(now);
  endDate.setFullYear(now.getFullYear() + 2);

  entries.forEach(entry => {
    const entryStart = new Date(entry.startDate);
    const entryEnd = new Date(entry.endDate);
    if (entryStart.getTime() < startDate.getTime()) {
      startDate.setTime(entryStart.getTime());
      startDate.setDate(startDate.getDate() - 1);
    }
    if (entryEnd.getTime() > endDate.getTime()) {
      endDate.setTime(entryEnd.getTime());
      endDate.setDate(endDate.getDate() + 1);
    }
  });

  return { startDate, endDate };
};

export const mapCalendarEntryToNativeDetails = (
  entry: CalendarEntryWithNativeSemantics,
  Calendar: CalendarBridgeApi
) => ({
  title: entry.title,
  startDate: new Date(entry.startDate),
  endDate: new Date(entry.endDate),
  notes: entry.notes,
  allDay: entry.allDay ?? false,
  recurrenceRule:
    entry.recurrenceRule?.frequency === 'yearly'
      ? {
          frequency: Calendar.Frequency.YEARLY,
          interval: 1
        }
      : null,
  ...(!entry.allDay && { timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone })
});

const toMirroredCalendarEvent = (event: DeviceCalendarEvent, Calendar: CalendarBridgeApi): MirroredCalendarEvent => ({
  id: event.id,
  title: event.title,
  startDate: event.startDate,
  endDate: event.endDate,
  notes: event.notes,
  allDay: event.allDay,
  recurrenceRule: event.recurrenceRule?.frequency === Calendar.Frequency.YEARLY ? { frequency: 'yearly' } : undefined
});

const exportTokenFromNotes = (notes: string | null | undefined) =>
  notes
    ?.split('\n')
    .map(line => line.trim())
    .find(line => line.startsWith('RelateAI export:'))
    ?.replace('RelateAI export:', '')
    .trim();

const collapseCalendarOccurrences = (events: DeviceCalendarEvent[], entries: CalendarEntryWithNativeSemantics[]) => {
  const entryById = new Map(entries.map(entry => [entry.id, entry]));
  const selectedBySeriesId = new Map<string, DeviceCalendarEvent>();

  for (const event of events) {
    const current = selectedBySeriesId.get(event.id);
    if (!current) {
      selectedBySeriesId.set(event.id, event);
      continue;
    }

    const entry = entryById.get(exportTokenFromNotes(event.notes) ?? '');
    const targetTime = entry ? new Date(entry.startDate).getTime() : Number.NaN;
    const eventTime = new Date(event.startDate).getTime();
    const currentTime = new Date(current.startDate).getTime();
    const eventScore = Number.isNaN(targetTime) ? eventTime : Math.abs(eventTime - targetTime);
    const currentScore = Number.isNaN(targetTime) ? currentTime : Math.abs(currentTime - targetTime);

    if (eventScore < currentScore) {
      selectedBySeriesId.set(event.id, event);
    }
  }

  return [...selectedBySeriesId.values()];
};

const lifecycleTargetFor = (event: DeviceCalendarEvent) =>
  event.recurrenceRule
    ? event.getOccurrenceSync({
        instanceStartDate: event.startDate,
        futureEvents: true
      })
    : event;

/**
 * Executes calendar reconciliation through Expo 57 shared calendar/event
 * objects. Exported for executable adapter tests; production callers should
 * use `exportEventsToDeviceCalendar`.
 */
export const exportEventsToDeviceCalendarWithApi = async (
  state: AppState,
  Calendar: CalendarBridgeApi,
  options: CalendarBridgeOperationOptions = {}
): Promise<number> => {
  const gate = cancellationGate(options.signal);
  gate.checkpoint();
  const selection = resolveCalendarExportSelection(state, options.eventIds);
  if (!selection.ok) {
    throw new Error('Calendar export selection is invalid or no longer exportable.');
  }
  const permission = await Calendar.requestCalendarPermissions();
  gate.checkpoint();
  if (permission.status !== 'granted') {
    throw new Error('Calendar permission was not granted.');
  }

  const calendar = await getOrCreateRelateCalendar(Calendar, gate);
  const entries = selection.entries;
  const { startDate, endDate } = exportRangeFor(entries);
  gate.checkpoint();
  const listedEvents = await calendar.listEvents(startDate, endDate);
  gate.checkpoint();
  const existingEvents = collapseCalendarOccurrences(listedEvents, entries);
  const mirroredEvents = existingEvents.map(event => toMirroredCalendarEvent(event, Calendar));
  const exportPlan = buildCalendarExportPlan(entries, mirroredEvents, { mode: selection.mode });
  const eventsById = new Map(existingEvents.map(event => [event.id, event]));

  for (const staleDeviceEventId of exportPlan.staleDeviceEventIds) {
    const staleEvent = eventsById.get(staleDeviceEventId);
    if (!staleEvent) {
      throw new Error(`Calendar event ${staleDeviceEventId} disappeared during reconciliation.`);
    }
    gate.beginNativeCommit();
    await lifecycleTargetFor(staleEvent).delete();
  }
  for (const update of exportPlan.toUpdate) {
    const deviceEvent = eventsById.get(update.deviceEventId);
    if (!deviceEvent) {
      throw new Error(`Calendar event ${update.deviceEventId} disappeared during reconciliation.`);
    }
    gate.beginNativeCommit();
    await lifecycleTargetFor(deviceEvent).update(mapCalendarEntryToNativeDetails(update.entry, Calendar));
  }
  for (const entry of exportPlan.toCreate) {
    gate.beginNativeCommit();
    await calendar.createEvent(mapCalendarEntryToNativeDetails(entry, Calendar));
  }

  return exportPlan.toCreate.length + exportPlan.toUpdate.length;
};

export const exportEventsToDeviceCalendar = async (
  state: AppState,
  options: CalendarBridgeOperationOptions = {}
): Promise<number> => {
  const Calendar = await import('expo-calendar');
  return exportEventsToDeviceCalendarWithApi(state, Calendar, options);
};

const allDayCalendarDate = (value: string | Date): string | undefined => {
  if (typeof value === 'string') {
    const calendarPrefix = /^(\d{4})-(\d{2})-(\d{2})(?:$|[T ])/.exec(value.trim());
    if (calendarPrefix) {
      const year = Number(calendarPrefix[1]);
      const month = Number(calendarPrefix[2]);
      const day = Number(calendarPrefix[3]);
      const date = new Date(Date.UTC(year, month - 1, day, 12, 0, 0, 0));
      if (date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day) {
        return date.toISOString();
      }
      return undefined;
    }
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return undefined;
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate(), 12, 0, 0, 0)).toISOString();
};

export const normalizeDeviceCalendarImportStartDate = (
  value: string | Date,
  allDay: boolean | undefined
): string | undefined => {
  if (allDay) return allDayCalendarDate(value);
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
};

export const importEventsFromDeviceCalendarWithApi = async (
  Calendar: CalendarBridgeApi,
  options: CalendarBridgeOperationOptions = {}
): Promise<CalendarImportCandidate[]> => {
  throwIfAborted(options.signal);
  const permission = await Calendar.requestCalendarPermissions();
  throwIfAborted(options.signal);
  if (permission.status !== 'granted') {
    throw new Error('Calendar permission was not granted.');
  }

  throwIfAborted(options.signal);
  const calendars = await Calendar.getCalendars(Calendar.EntityTypes.EVENT);
  throwIfAborted(options.signal);
  const startDate = new Date();
  const endDate = new Date();
  endDate.setFullYear(startDate.getFullYear() + 1);
  throwIfAborted(options.signal);
  const events = calendars.length ? await Calendar.listEvents(calendars, startDate, endDate) : [];
  throwIfAborted(options.signal);

  const candidates: CalendarImportCandidate[] = [];
  for (const event of events) {
    throwIfAborted(options.signal);
    if (!/birthday|anniversary|graduation|follow[- ]?up/i.test(event.title ?? '')) continue;
    const normalizedStartDate = normalizeDeviceCalendarImportStartDate(event.startDate, event.allDay);
    if (!normalizedStartDate) continue;
    candidates.push({
      sourceId: event.id,
      title: event.title ?? 'Calendar event',
      startDate: normalizedStartDate,
      notes: event.notes ?? undefined
    });
  }
  return candidates;
};

export const importEventsFromDeviceCalendar = async (
  options: CalendarBridgeOperationOptions = {}
): Promise<CalendarImportCandidate[]> => {
  const Calendar = await import('expo-calendar');
  return importEventsFromDeviceCalendarWithApi(Calendar, options);
};
