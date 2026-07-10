import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildCalendarExportEntries } from '../domain/calendarSync';
import type { AppState, CalendarExportEntry } from '../domain/types';
import { createProductionInitialState } from '../data/productionState';
import {
  exportEventsToDeviceCalendarWithApi,
  importEventsFromDeviceCalendarWithApi,
  mapCalendarEntryToNativeDetails,
  type CalendarBridgeApi
} from './calendarBridge';

const exportState = (): AppState => ({
  ...createProductionInitialState(),
  contacts: [
    {
      id: 'contact-1',
      name: 'Asha Mehra',
      relationship: 'Sister',
      group: 'Family',
      phone: '+91 98765 43210',
      preferredChannel: 'Manual',
      language: 'English',
      tone: ['Warm'],
      healthScore: 80,
      isVip: true,
      dnd: false,
      checkInCadenceDays: 30,
      notesSummary: '',
      annualGiftBudget: 0
    }
  ],
  events: [
    {
      id: 'event-update',
      contactId: 'contact-1',
      type: 'Birthday',
      label: 'Birthday',
      date: '2030-08-04T06:30:00.000Z',
      recurrence: {
        frequency: 'Yearly',
        month: 8,
        day: 4,
        originalYear: 1992,
        leapDayPolicy: 'February 28'
      },
      verified: true,
      source: 'Manual',
      checklist: []
    },
    {
      id: 'event-create',
      contactId: 'contact-1',
      type: 'Graduation',
      label: 'Graduation',
      date: '2030-11-14T06:30:00.000Z',
      verified: true,
      source: 'Manual',
      checklist: []
    }
  ]
});

const calendarConstants = {
  EntityTypes: { EVENT: 'event' },
  SourceType: { LOCAL: 'local' },
  CalendarAccessLevel: { OWNER: 'owner' },
  Frequency: { YEARLY: 'yearly' }
};

describe('Expo 57 calendar adapter', () => {
  it('reconciles through calendar/event class methods without calling throwing legacy shims', async () => {
    const state = exportState();
    const entries = buildCalendarExportEntries(state);
    const updated: unknown[] = [];
    const created: unknown[] = [];
    const deleted: string[] = [];
    const existingEvents = [
      {
        id: 'device-update',
        title: 'Outdated title',
        startDate: entries[0].startDate,
        endDate: entries[0].endDate,
        notes: entries[0].notes,
        update: async (details: unknown) => {
          updated.push(details);
        },
        delete: async () => {
          deleted.push('device-update');
        }
      },
      {
        id: 'device-stale',
        title: 'Birthday: Deleted contact',
        startDate: entries[0].startDate,
        endDate: entries[0].endDate,
        notes: 'RelateAI reminder.\nRelateAI export: calendar-export-deleted',
        update: async () => undefined,
        delete: async () => {
          deleted.push('device-stale');
        }
      }
    ];
    const relateCalendar = {
      id: 'relate-calendar',
      title: 'RelateAI Relationship Events',
      allowsModifications: true,
      source: { id: 'local', name: 'Local', type: 'local', isLocalAccount: true },
      listEvents: async () => existingEvents,
      createEvent: async (details: unknown) => {
        created.push(details);
        return {};
      }
    };
    const api = {
      ...calendarConstants,
      requestCalendarPermissions: async () => ({ status: 'granted' }),
      getCalendars: async () => [relateCalendar],
      createCalendar: async () => {
        throw new Error('Existing RelateAI calendar should be reused.');
      },
      listEvents: async () => [],
      requestCalendarPermissionsAsync: () => {
        throw new Error('Expo 57 root legacy shim must never be called.');
      },
      getCalendarsAsync: () => {
        throw new Error('Expo 57 root legacy shim must never be called.');
      },
      getEventsAsync: () => {
        throw new Error('Expo 57 root legacy shim must never be called.');
      },
      createEventAsync: () => {
        throw new Error('Expo 57 root legacy shim must never be called.');
      },
      updateEventAsync: () => {
        throw new Error('Expo 57 root legacy shim must never be called.');
      },
      deleteEventAsync: () => {
        throw new Error('Expo 57 root legacy shim must never be called.');
      }
    } as unknown as CalendarBridgeApi;

    const changed = await exportEventsToDeviceCalendarWithApi(state, api);

    assert.equal(changed, 2);
    assert.equal(updated.length, 1);
    assert.equal(created.length, 1);
    assert.deepEqual(deleted, ['device-stale']);
  });

  it('maps all-day annual recurrence to Expo recurrence fields', () => {
    const entry: CalendarExportEntry = {
      id: 'calendar-export-birthday',
      eventId: 'birthday',
      title: 'Birthday: Asha Mehra',
      startDate: '2030-08-04T00:00:00.000Z',
      endDate: '2030-08-05T00:00:00.000Z',
      notes: 'RelateAI export: calendar-export-birthday',
      allDay: true,
      recurrenceRule: { frequency: 'yearly' }
    };

    const details = mapCalendarEntryToNativeDetails(
      entry,
      calendarConstants as unknown as CalendarBridgeApi
    );

    assert.equal(details.allDay, true);
    assert.deepEqual(details.recurrenceRule, { frequency: 'yearly', interval: 1 });
    assert.equal('timeZone' in details, false);

    const timedDetails = mapCalendarEntryToNativeDetails(
      { ...entry, allDay: undefined, recurrenceRule: undefined },
      calendarConstants as unknown as CalendarBridgeApi
    );
    assert.equal(timedDetails.allDay, false);
    assert.equal(timedDetails.recurrenceRule, null);
    assert.equal('timeZone' in timedDetails, true);
  });

  it('normalizes class-event recurrence before deciding an export is unchanged', async () => {
    const completeState = exportState();
    const state = { ...completeState, events: [completeState.events[0]] };
    const [entry] = buildCalendarExportEntries(state);
    let updateCalls = 0;
    let createCalls = 0;
    const calendar = {
      id: 'relate-calendar',
      title: 'RelateAI Relationship Events',
      allowsModifications: true,
      source: { id: 'local', name: 'Local', type: 'local', isLocalAccount: true },
      listEvents: async () => [
        {
          id: 'device-current',
          title: entry.title,
          startDate: entry.startDate,
          endDate: entry.endDate,
          notes: entry.notes,
          allDay: true,
          recurrenceRule: { frequency: 'yearly', interval: 1 },
          update: async () => {
            updateCalls += 1;
          },
          delete: async () => undefined
        }
      ],
      createEvent: async () => {
        createCalls += 1;
        return {};
      }
    };
    const api = {
      ...calendarConstants,
      requestCalendarPermissions: async () => ({ status: 'granted' }),
      getCalendars: async () => [calendar],
      createCalendar: async () => calendar,
      listEvents: async () => []
    } as unknown as CalendarBridgeApi;

    const changed = await exportEventsToDeviceCalendarWithApi(state, api);

    assert.equal(changed, 0);
    assert.equal(updateCalls, 0);
    assert.equal(createCalls, 0);
  });

  it('collapses recurring occurrences and deletes the future series only once', async () => {
    const state = { ...exportState(), events: [] };
    let seriesDeleteCalls = 0;
    let occurrenceTargetCalls = 0;
    const seriesTarget = {
      delete: async () => {
        seriesDeleteCalls += 1;
      }
    };
    const recurringOccurrence = (startDate: string) => ({
      id: 'stale-series',
      title: 'Birthday: Deleted contact',
      startDate,
      endDate: new Date(new Date(startDate).getTime() + 86_400_000).toISOString(),
      notes: 'RelateAI reminder.\nRelateAI export: calendar-export-deleted',
      allDay: true,
      recurrenceRule: { frequency: 'yearly', interval: 1 },
      getOccurrenceSync: (options: { instanceStartDate?: string | Date; futureEvents?: boolean }) => {
        occurrenceTargetCalls += 1;
        assert.equal(options.futureEvents, true);
        assert.ok(options.instanceStartDate);
        return seriesTarget;
      },
      delete: async () => {
        throw new Error('A recurring occurrence must use a future-series lifecycle target.');
      },
      update: async () => undefined
    });
    const calendar = {
      id: 'relate-calendar',
      title: 'RelateAI Relationship Events',
      allowsModifications: true,
      source: { id: 'local', name: 'Local', type: 'local', isLocalAccount: true },
      listEvents: async () => [
        recurringOccurrence('2025-08-04T00:00:00.000Z'),
        recurringOccurrence('2026-08-04T00:00:00.000Z'),
        recurringOccurrence('2027-08-04T00:00:00.000Z')
      ],
      createEvent: async () => ({})
    };
    const api = {
      ...calendarConstants,
      requestCalendarPermissions: async () => ({ status: 'granted' }),
      getCalendars: async () => [calendar],
      createCalendar: async () => calendar,
      listEvents: async () => []
    } as unknown as CalendarBridgeApi;

    const changed = await exportEventsToDeviceCalendarWithApi(state, api);

    assert.equal(changed, 0);
    assert.equal(occurrenceTargetCalls, 1);
    assert.equal(seriesDeleteCalls, 1);
  });

  it('creates the owned calendar with the class API when it is missing', async () => {
    const state = exportState();
    const createdEvents: unknown[] = [];
    const ownedCalendar = {
      id: 'new-relate-calendar',
      title: 'RelateAI Relationship Events',
      allowsModifications: true,
      source: { id: 'local', name: 'Local', type: 'local', isLocalAccount: true },
      listEvents: async () => [],
      createEvent: async (details: unknown) => {
        createdEvents.push(details);
        return {};
      }
    };
    let createCalendarCalls = 0;
    const api = {
      ...calendarConstants,
      requestCalendarPermissions: async () => ({ status: 'granted' }),
      getCalendars: async () => [],
      createCalendar: async () => {
        createCalendarCalls += 1;
        return ownedCalendar;
      },
      listEvents: async () => []
    } as unknown as CalendarBridgeApi;

    const changed = await exportEventsToDeviceCalendarWithApi(state, api);

    assert.equal(createCalendarCalls, 1);
    assert.equal(changed, 2);
    assert.equal(createdEvents.length, 2);
  });

  it('imports matching events through the supported listEvents API and preserves DTO shape', async () => {
    const listCalls: Array<{ calendars: unknown[]; startDate: Date; endDate: Date }> = [];
    const calendars = [{ id: 'personal-calendar', title: 'Personal' }];
    const api = {
      ...calendarConstants,
      requestCalendarPermissions: async () => ({ status: 'granted' }),
      getCalendars: async () => calendars,
      createCalendar: async () => {
        throw new Error('Not used by import.');
      },
      listEvents: async (selected: unknown[], startDate: Date, endDate: Date) => {
        listCalls.push({ calendars: selected, startDate, endDate });
        return [
          {
            id: 'event-birthday',
            title: 'Birthday: Dev Kapoor',
            startDate: '2030-11-14T00:00:00.000Z',
            notes: null
          },
          {
            id: 'event-meeting',
            title: 'Project meeting',
            startDate: '2030-11-15T10:00:00.000Z',
            notes: 'Unrelated'
          }
        ];
      },
      getEventsAsync: () => {
        throw new Error('Expo 57 root legacy shim must never be called.');
      }
    } as unknown as CalendarBridgeApi;

    const candidates = await importEventsFromDeviceCalendarWithApi(api);

    assert.equal(listCalls.length, 1);
    assert.equal(listCalls[0].calendars, calendars);
    assert.deepEqual(candidates, [
      {
        sourceId: 'event-birthday',
        title: 'Birthday: Dev Kapoor',
        startDate: '2030-11-14T00:00:00.000Z',
        notes: undefined
      }
    ]);
  });

  it('does not read calendars after permission denial', async () => {
    let calendarReads = 0;
    const api = {
      ...calendarConstants,
      requestCalendarPermissions: async () => ({ status: 'denied' }),
      getCalendars: async () => {
        calendarReads += 1;
        return [];
      },
      createCalendar: async () => {
        throw new Error('Not used.');
      },
      listEvents: async () => []
    } as unknown as CalendarBridgeApi;

    await assert.rejects(
      importEventsFromDeviceCalendarWithApi(api),
      /Calendar permission was not granted/
    );
    assert.equal(calendarReads, 0);
  });
});
