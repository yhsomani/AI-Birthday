import {
  buildCalendarExportEntries,
  buildCalendarExportPlan,
  type MirroredCalendarEvent
} from '../domain/calendarSync';
import type { AppState, CalendarExportEntry, CalendarImportCandidate } from '../domain/types';

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

const getOrCreateRelateCalendar = async (
  Calendar: CalendarBridgeApi
): Promise<DeviceCalendar> => {
  const calendars = await Calendar.getCalendars(Calendar.EntityTypes.EVENT);
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

const toMirroredCalendarEvent = (
  event: DeviceCalendarEvent,
  Calendar: CalendarBridgeApi
): MirroredCalendarEvent => ({
  id: event.id,
  title: event.title,
  startDate: event.startDate,
  endDate: event.endDate,
  notes: event.notes,
  allDay: event.allDay,
  recurrenceRule:
    event.recurrenceRule?.frequency === Calendar.Frequency.YEARLY
      ? { frequency: 'yearly' }
      : undefined
});

const exportTokenFromNotes = (notes: string | null | undefined) =>
  notes
    ?.split('\n')
    .map(line => line.trim())
    .find(line => line.startsWith('RelateAI export:'))
    ?.replace('RelateAI export:', '')
    .trim();

const collapseCalendarOccurrences = (
  events: DeviceCalendarEvent[],
  entries: CalendarEntryWithNativeSemantics[]
) => {
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
    const currentScore = Number.isNaN(targetTime)
      ? currentTime
      : Math.abs(currentTime - targetTime);

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
  Calendar: CalendarBridgeApi
): Promise<number> => {
  const permission = await Calendar.requestCalendarPermissions();
  if (permission.status !== 'granted') {
    throw new Error('Calendar permission was not granted.');
  }

  const calendar = await getOrCreateRelateCalendar(Calendar);
  const entries = buildCalendarExportEntries(state);
  const { startDate, endDate } = exportRangeFor(entries);
  const listedEvents = await calendar.listEvents(startDate, endDate);
  const existingEvents = collapseCalendarOccurrences(listedEvents, entries);
  const mirroredEvents = existingEvents.map(event => toMirroredCalendarEvent(event, Calendar));
  const exportPlan = buildCalendarExportPlan(entries, mirroredEvents);
  const eventsById = new Map(existingEvents.map(event => [event.id, event]));

  for (const staleDeviceEventId of exportPlan.staleDeviceEventIds) {
    const staleEvent = eventsById.get(staleDeviceEventId);
    if (!staleEvent) {
      throw new Error(`Calendar event ${staleDeviceEventId} disappeared during reconciliation.`);
    }
    await lifecycleTargetFor(staleEvent).delete();
  }
  for (const update of exportPlan.toUpdate) {
    const deviceEvent = eventsById.get(update.deviceEventId);
    if (!deviceEvent) {
      throw new Error(`Calendar event ${update.deviceEventId} disappeared during reconciliation.`);
    }
    await lifecycleTargetFor(deviceEvent).update(
      mapCalendarEntryToNativeDetails(update.entry, Calendar)
    );
  }
  for (const entry of exportPlan.toCreate) {
    await calendar.createEvent(mapCalendarEntryToNativeDetails(entry, Calendar));
  }

  return exportPlan.toCreate.length + exportPlan.toUpdate.length;
};

export const exportEventsToDeviceCalendar = async (state: AppState): Promise<number> => {
  const Calendar = await import('expo-calendar');
  return exportEventsToDeviceCalendarWithApi(state, Calendar);
};

export const importEventsFromDeviceCalendarWithApi = async (
  Calendar: CalendarBridgeApi
): Promise<CalendarImportCandidate[]> => {
  const permission = await Calendar.requestCalendarPermissions();
  if (permission.status !== 'granted') {
    throw new Error('Calendar permission was not granted.');
  }

  const calendars = await Calendar.getCalendars(Calendar.EntityTypes.EVENT);
  const startDate = new Date();
  const endDate = new Date();
  endDate.setFullYear(startDate.getFullYear() + 1);
  const events = calendars.length
    ? await Calendar.listEvents(calendars, startDate, endDate)
    : [];

  return events
    .filter(event => /birthday|anniversary|graduation|follow[- ]?up/i.test(event.title ?? ''))
    .map(event => ({
      sourceId: event.id,
      title: event.title ?? 'Calendar event',
      startDate: new Date(event.startDate).toISOString(),
      notes: event.notes ?? undefined
    }));
};

export const importEventsFromDeviceCalendar = async (): Promise<CalendarImportCandidate[]> => {
  const Calendar = await import('expo-calendar');
  return importEventsFromDeviceCalendarWithApi(Calendar);
};
