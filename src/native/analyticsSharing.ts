import type { AnalyticsShareSummary } from '../domain/analytics';
import { throwIfAborted } from './abort';

const MAX_SUMMARY_BODY_LENGTH = 64_000;
const MAX_CSV_CHARACTER_LENGTH = 8_000_000;

export type AnalyticsSummaryShareOutcome = 'shared' | 'dismissed';

export interface AnalyticsShareApi {
  dismissedAction: string;
  share(payload: { title: string; message: string }): Promise<{ action: string }>;
}

export interface AnalyticsReportFileSystem {
  cacheDirectory: string | null;
  utf8Encoding: 'utf8';
  writeAsStringAsync(uri: string, value: string, options: { encoding: 'utf8' }): Promise<void>;
  deleteAsync(uri: string, options: { idempotent: true }): Promise<void>;
}

export interface AnalyticsReportSharing {
  isAvailableAsync(): Promise<boolean>;
  shareAsync(uri: string, options: { dialogTitle: string; mimeType: string; UTI: string }): Promise<void>;
}

export interface AnalyticsReportShareDependencies {
  fileSystem: AnalyticsReportFileSystem;
  sharing: AnalyticsReportSharing;
  now?: () => Date;
}

const validSummary = (summary: AnalyticsShareSummary) =>
  summary.redacted === true &&
  summary.title.trim().length > 0 &&
  summary.title.length <= 200 &&
  summary.body.trim().length > 0 &&
  summary.body.length <= MAX_SUMMARY_BODY_LENGTH &&
  summary.lineCount === summary.body.split('\n').length;

export const shareAnalyticsSummaryWithApi = async (
  summary: AnalyticsShareSummary,
  shareApi: AnalyticsShareApi,
  signal?: AbortSignal
): Promise<AnalyticsSummaryShareOutcome> => {
  if (!validSummary(summary)) {
    throw new Error('The redacted analytics summary exceeded safe sharing bounds.');
  }
  throwIfAborted(signal);
  const result = await shareApi.share({ title: summary.title, message: summary.body });
  return result.action === shareApi.dismissedAction ? 'dismissed' : 'shared';
};

const safeTimestamp = (date: Date) => date.toISOString().replace(/[:.]/g, '-');

export const shareAnalyticsCsvWithApi = async (
  csv: string,
  dependencies: AnalyticsReportShareDependencies,
  signal?: AbortSignal
): Promise<{ opened: true; temporaryFileRemoved: true }> => {
  if (csv.trim().length === 0 || csv.length > MAX_CSV_CHARACTER_LENGTH) {
    throw new Error('The analytics report exceeded safe export bounds.');
  }
  throwIfAborted(signal);
  if (!(await dependencies.sharing.isAvailableAsync())) {
    throw new Error('Report sharing is unavailable on this device.');
  }
  throwIfAborted(signal);
  const cacheDirectory = dependencies.fileSystem.cacheDirectory;
  if (!cacheDirectory) {
    throw new Error('Temporary report storage is unavailable on this device.');
  }

  const uri = `${cacheDirectory}relateai-analytics-${safeTimestamp((dependencies.now ?? (() => new Date()))())}.csv`;
  let writeAttempted = false;
  try {
    writeAttempted = true;
    await dependencies.fileSystem.writeAsStringAsync(uri, csv, {
      encoding: dependencies.fileSystem.utf8Encoding
    });
    throwIfAborted(signal);
    await dependencies.sharing.shareAsync(uri, {
      dialogTitle: 'Share RelateAI analytics report',
      mimeType: 'text/csv',
      UTI: 'public.comma-separated-values-text'
    });
  } finally {
    if (writeAttempted) {
      await dependencies.fileSystem.deleteAsync(uri, { idempotent: true });
    }
  }

  return { opened: true, temporaryFileRemoved: true };
};

export const shareAnalyticsSummary = async (
  summary: AnalyticsShareSummary,
  signal?: AbortSignal
): Promise<AnalyticsSummaryShareOutcome> => {
  const { Share } = await import('react-native');
  return shareAnalyticsSummaryWithApi(
    summary,
    {
      dismissedAction: Share.dismissedAction,
      share: payload => Share.share(payload)
    },
    signal
  );
};

export const shareAnalyticsCsv = async (
  csv: string,
  signal?: AbortSignal
): Promise<{ opened: true; temporaryFileRemoved: true }> => {
  const [fileSystem, sharing] = await Promise.all([import('expo-file-system/legacy'), import('expo-sharing')]);
  return shareAnalyticsCsvWithApi(
    csv,
    {
      fileSystem: {
        cacheDirectory: fileSystem.cacheDirectory,
        utf8Encoding: fileSystem.EncodingType.UTF8,
        writeAsStringAsync: fileSystem.writeAsStringAsync,
        deleteAsync: fileSystem.deleteAsync
      },
      sharing
    },
    signal
  );
};
