import type { HomeWidgetSummary, HomeWidgetTile } from '../domain/homeWidget';
import type { SupportedLocale } from '../domain/types';
import { t, tc, type TranslationKey } from '../i18n/i18n';

const homeWidgetTileText = (locale: SupportedLocale, tile: HomeWidgetTile): HomeWidgetTile => {
  switch (tile.id) {
    case 'today-events':
      return {
        ...tile,
        title: tc(locale, tile.count, {
          one: 'feature.home.widget.tile.todayEvents.title.one',
          other: 'feature.home.widget.tile.todayEvents.title.other'
        }),
        detail: t(locale, 'feature.home.widget.tile.todayEvents.detail'),
        accessibilityLabel: tc(locale, tile.count, {
          one: 'feature.home.widget.tile.todayEvents.accessibility.one',
          other: 'feature.home.widget.tile.todayEvents.accessibility.other'
        })
      };
    case 'pending-approvals':
      return {
        ...tile,
        title: tc(locale, tile.count, {
          one: 'feature.home.widget.tile.pendingApprovals.title.one',
          other: 'feature.home.widget.tile.pendingApprovals.title.other'
        }),
        detail: t(locale, 'feature.home.widget.tile.pendingApprovals.detail'),
        accessibilityLabel: tc(locale, tile.count, {
          one: 'feature.home.widget.tile.pendingApprovals.accessibility.one',
          other: 'feature.home.widget.tile.pendingApprovals.accessibility.other'
        })
      };
  }
};

export const localizeHomeWidgetSummary = (summary: HomeWidgetSummary, locale: SupportedLocale): HomeWidgetSummary => ({
  ...summary,
  title: t(locale, 'feature.home.widget.summaryTitle'),
  subtitle:
    summary.tiles.length > 0
      ? tc(locale, summary.tiles.length, {
          one: 'feature.home.widget.summaryReady.one',
          other: 'feature.home.widget.summaryReady.other'
        })
      : t(locale, 'feature.home.widget.summaryEmpty'),
  tiles: summary.tiles.map(tile => homeWidgetTileText(locale, tile)),
  emptyState: summary.emptyState ? t(locale, 'feature.home.widget.emptyState') : undefined,
  privacyNote: t(locale, 'feature.home.widget.privacyNote')
});

export const requiredHomeWidgetPresentationKeys: TranslationKey[] = [
  'feature.home.widget.summaryTitle',
  'feature.home.widget.summaryReady.one',
  'feature.home.widget.summaryReady.other',
  'feature.home.widget.summaryEmpty',
  'feature.home.widget.privacyNote',
  'feature.home.widget.emptyState',
  'feature.home.widget.tile.todayEvents.title.one',
  'feature.home.widget.tile.todayEvents.title.other',
  'feature.home.widget.tile.todayEvents.detail',
  'feature.home.widget.tile.todayEvents.accessibility.one',
  'feature.home.widget.tile.todayEvents.accessibility.other',
  'feature.home.widget.tile.pendingApprovals.title.one',
  'feature.home.widget.tile.pendingApprovals.title.other',
  'feature.home.widget.tile.pendingApprovals.detail',
  'feature.home.widget.tile.pendingApprovals.accessibility.one',
  'feature.home.widget.tile.pendingApprovals.accessibility.other'
];
