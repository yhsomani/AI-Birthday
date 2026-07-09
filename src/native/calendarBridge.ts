import * as Calendar from 'expo-calendar';
import { buildCalendarExportEntries } from '../domain/calendarSync';
import type { AppState, CalendarImportCandidate } from '../domain/types';

const RELATEAI_CALENDAR_TITLE = 'RelateAI Relationship Events';

const getDefaultCalendarSource = async (): Promise<Calendar.Source> => {
  const calendars = await Calendar.getCalendarsAsync(Calendar.EntityTypes.EVENT);
  const defaultCalendar = calendars.find(calendar => calendar.allowsModifications);
  if (defaultCalendar?.source) {
    return defaultCalendar.source;
  }
  return {
    id: 'relateai-local',
    name: RELATEAI_CALENDAR_TITLE,
    type: Calendar.SourceType.LOCAL,
    isLocalAccount: true
  };
};

const getOrCreateRelateCalendar = async () => {
  const calendars = await Calendar.getCalendarsAsync(Calendar.EntityTypes.EVENT);
  const existing = calendars.find(calendar => calendar.title === RELATEAI_CALENDAR_TITLE);
  if (existing) {
    return existing.id;
  }
  const source = await getDefaultCalendarSource();
  return Calendar.createCalendarAsync({
    title: RELATEAI_CALENDAR_TITLE,
    color: '#176b5b',
    entityType: Calendar.EntityTypes.EVENT,
    source,
    name: RELATEAI_CALENDAR_TITLE,
    ownerAccount: 'personal',
    accessLevel: Calendar.CalendarAccessLevel.OWNER
  });
};

export const exportEventsToDeviceCalendar = async (state: AppState): Promise<number> => {
  const permission = await Calendar.requestCalendarPermissionsAsync();
  if (permission.status !== 'granted') {
    throw new Error('Calendar permission was not granted.');
  }

  const calendarId = await getOrCreateRelateCalendar();
  const entries = buildCalendarExportEntries(state);
  for (const entry of entries) {
    await Calendar.createEventAsync(calendarId, {
      title: entry.title,
      startDate: new Date(entry.startDate),
      endDate: new Date(entry.endDate),
      notes: entry.notes,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
    });
  }
  return entries.length;
};

export const importEventsFromDeviceCalendar = async (): Promise<CalendarImportCandidate[]> => {
  const permission = await Calendar.requestCalendarPermissionsAsync();
  if (permission.status !== 'granted') {
    throw new Error('Calendar permission was not granted.');
  }

  const calendars = await Calendar.getCalendarsAsync(Calendar.EntityTypes.EVENT);
  const calendarIds = calendars.map(calendar => calendar.id);
  const startDate = new Date();
  const endDate = new Date();
  endDate.setFullYear(startDate.getFullYear() + 1);
  const events = await Calendar.getEventsAsync(calendarIds, startDate, endDate);

  return events
    .filter(event => /birthday|anniversary|graduation|follow[- ]?up/i.test(event.title ?? ''))
    .map(event => ({
      sourceId: event.id,
      title: event.title ?? 'Calendar event',
      startDate: new Date(event.startDate).toISOString(),
      notes: event.notes
    }));
};
