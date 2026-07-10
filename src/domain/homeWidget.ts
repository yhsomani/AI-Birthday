import type { AppState, Screen } from './types';
import { eventOccurrenceIso } from './occasionDates';

export type HomeWidgetTileId = 'today-events' | 'pending-approvals';

export interface HomeWidgetRoute {
  screen: Screen;
  contactId?: string;
  messageId?: string;
}

export interface HomeWidgetTile {
  id: HomeWidgetTileId;
  title: string;
  detail: string;
  count: number;
  daysUntil?: number;
  route: HomeWidgetRoute;
  accessibilityLabel: string;
  priority: number;
}

export interface HomeWidgetSummary {
  generatedAt: string;
  expiresAt: string;
  title: string;
  subtitle: string;
  tiles: HomeWidgetTile[];
  emptyState?: string;
  privacyNote: string;
}

export interface NativeHomeWidgetTile {
  id: HomeWidgetTileId;
  title: string;
  detail: string;
  count: number;
  route: {
    screen: 'home' | 'events' | 'messages' | 'more';
  };
  accessibilityLabel: string;
}

export interface NativeHomeWidgetSummary {
  generatedAt: string;
  expiresAt: string;
  title: string;
  subtitle: string;
  tiles: NativeHomeWidgetTile[];
  emptyState?: string;
  privacyNote: string;
}

const widgetTtlMinutes = 30;
const safeWidgetScreens = new Set<Screen>(['home', 'events', 'messages', 'more']);

const dateKey = (date: Date) => {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
};

const addMinutes = (date: Date, minutes: number) => {
  const next = new Date(date);
  next.setMinutes(next.getMinutes() + minutes);
  return next;
};

const plural = (count: number, singular: string, pluralLabel = `${singular}s`) =>
  `${count} ${count === 1 ? singular : pluralLabel}`;

const isReviewStatus = (status: string) => status === 'Needs review' || status === 'Draft';

export const buildHomeWidgetSummary = (state: AppState, now = new Date()): HomeWidgetSummary => {
  const today = dateKey(now);
  const generatedAt = now.toISOString();
  const activeContactIds = new Set(state.contacts.filter(contact => !contact.archivedAt).map(contact => contact.id));
  const todayEvents = state.events.filter(
    event => activeContactIds.has(event.contactId) && eventOccurrenceIso(event, now)?.slice(0, 10) === today
  );
  const pendingApprovals = state.messages.filter(
    message => activeContactIds.has(message.contactId) && isReviewStatus(message.status)
  );

  const tiles: HomeWidgetTile[] = [];

  if (todayEvents.length > 0) {
    tiles.push({
      id: 'today-events',
      title: plural(todayEvents.length, 'event') + ' today',
      detail: 'Open Events to prepare and review reminders.',
      count: todayEvents.length,
      route: { screen: 'events' },
      accessibilityLabel: `${todayEvents.length} relationship event${todayEvents.length === 1 ? '' : 's'} today. Open Events.`,
      priority: 10
    });
  }

  if (pendingApprovals.length > 0) {
    tiles.push({
      id: 'pending-approvals',
      title: plural(pendingApprovals.length, 'message') + ' to review',
      detail: 'Open Messages to approve, edit, reject, or retry.',
      count: pendingApprovals.length,
      route: { screen: 'messages' },
      accessibilityLabel: `${pendingApprovals.length} message${pendingApprovals.length === 1 ? '' : 's'} waiting for review. Open Messages.`,
      priority: 9
    });
  }

  const sortedTiles = tiles.sort((a, b) => b.priority - a.priority).slice(0, 2);

  return {
    generatedAt,
    expiresAt: addMinutes(now, widgetTtlMinutes).toISOString(),
    title: 'RelateAI today',
    subtitle:
      sortedTiles.length > 0
        ? `${sortedTiles.length} safe shortcut${sortedTiles.length === 1 ? '' : 's'} ready.`
        : 'No relationship actions need attention.',
    tiles: sortedTiles,
    emptyState: sortedTiles.length === 0 ? 'No events or approvals need attention.' : undefined,
    privacyNote:
      'Widget summaries avoid message bodies, phone numbers, email addresses, private notes, and send actions.'
  };
};

export const nativeHomeWidgetRouteScreen = (screen: Screen): NativeHomeWidgetTile['route']['screen'] =>
  safeWidgetScreens.has(screen) ? (screen as NativeHomeWidgetTile['route']['screen']) : 'home';

export const serializeHomeWidgetSummaryForNative = (summary: HomeWidgetSummary): NativeHomeWidgetSummary => ({
  generatedAt: summary.generatedAt,
  expiresAt: summary.expiresAt,
  title: summary.title,
  subtitle: summary.subtitle,
  tiles: summary.tiles.slice(0, 4).map(tile => ({
    id: tile.id,
    title: tile.title,
    detail: tile.detail,
    count: Math.max(0, Math.floor(tile.count)),
    route: {
      screen: nativeHomeWidgetRouteScreen(tile.route.screen)
    },
    accessibilityLabel: tile.accessibilityLabel
  })),
  emptyState: summary.emptyState,
  privacyNote: summary.privacyNote
});
