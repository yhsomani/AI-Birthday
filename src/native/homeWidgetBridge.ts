import { NativeModules, Platform } from 'react-native';
import { serializeHomeWidgetSummaryForNative, type HomeWidgetSummary } from '../domain/homeWidget';

type RelateAiHomeWidgetModule = {
  updateHomeWidget?: (summaryJson: string) => Promise<void> | void;
  clearHomeWidget?: () => Promise<void> | void;
};

export type HomeWidgetSyncResult =
  | {
      status: 'synced';
    }
  | {
      status: 'skipped';
      reason: 'unsupported-platform' | 'native-module-missing';
    };

const getHomeWidgetModule = (): RelateAiHomeWidgetModule | undefined =>
  NativeModules.RelateAiHomeWidget as RelateAiHomeWidgetModule | undefined;

export const homeWidgetSummaryJson = (summary: HomeWidgetSummary) =>
  JSON.stringify(serializeHomeWidgetSummaryForNative(summary));

export const syncHomeWidgetSummary = async (summary: HomeWidgetSummary): Promise<HomeWidgetSyncResult> => {
  if (Platform.OS !== 'android') {
    return {
      status: 'skipped',
      reason: 'unsupported-platform'
    };
  }

  const module = getHomeWidgetModule();
  if (!module?.updateHomeWidget) {
    return {
      status: 'skipped',
      reason: 'native-module-missing'
    };
  }

  await module.updateHomeWidget(homeWidgetSummaryJson(summary));
  return {
    status: 'synced'
  };
};

export const clearHomeWidgetSummary = async (): Promise<HomeWidgetSyncResult> => {
  if (Platform.OS !== 'android') {
    return { status: 'skipped', reason: 'unsupported-platform' };
  }
  const module = getHomeWidgetModule();
  if (!module?.clearHomeWidget) {
    return { status: 'skipped', reason: 'native-module-missing' };
  }
  await module.clearHomeWidget();
  return { status: 'synced' };
};
