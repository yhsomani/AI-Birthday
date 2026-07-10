import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  shareAnalyticsCsvWithApi,
  shareAnalyticsSummaryWithApi,
  type AnalyticsReportShareDependencies
} from './analyticsSharing';

describe('analytics native sharing boundary', () => {
  it('shares only a validated redacted summary and distinguishes dismissal', async () => {
    const payloads: { title: string; message: string }[] = [];
    const shared = await shareAnalyticsSummaryWithApi(
      { title: 'Relationship summary', body: 'Metrics:\n- Healthy: 2', lineCount: 2, redacted: true },
      {
        dismissedAction: 'dismissed',
        share: async payload => {
          payloads.push(payload);
          return { action: 'shared' };
        }
      }
    );
    const dismissed = await shareAnalyticsSummaryWithApi(
      { title: 'Relationship summary', body: 'No private rows', lineCount: 1, redacted: true },
      { dismissedAction: 'dismissed', share: async () => ({ action: 'dismissed' }) }
    );

    assert.equal(shared, 'shared');
    assert.equal(dismissed, 'dismissed');
    assert.deepEqual(payloads, [{ title: 'Relationship summary', message: 'Metrics:\n- Healthy: 2' }]);
  });

  it('deletes its bounded temporary CSV after share success or failure', async () => {
    const writes: string[] = [];
    const removals: string[] = [];
    const dependencies: AnalyticsReportShareDependencies = {
      now: () => new Date('2026-07-10T09:30:00.000Z'),
      fileSystem: {
        cacheDirectory: 'file:///cache/',
        utf8Encoding: 'utf8',
        writeAsStringAsync: async uri => {
          writes.push(uri);
        },
        deleteAsync: async uri => {
          removals.push(uri);
        }
      },
      sharing: {
        isAvailableAsync: async () => true,
        shareAsync: async () => undefined
      }
    };

    const shared = await shareAnalyticsCsvWithApi('Section,Name\nMetric,Health', dependencies);
    assert.deepEqual(shared, { opened: true, temporaryFileRemoved: true });
    assert.equal(writes.length, 1);
    assert.deepEqual(removals, writes);

    dependencies.sharing.shareAsync = async () => {
      throw new Error('share failed');
    };
    await assert.rejects(() => shareAnalyticsCsvWithApi('Section,Name\nMetric,Health', dependencies), /share failed/);
    assert.equal(removals.length, 2);

    dependencies.fileSystem.writeAsStringAsync = async uri => {
      writes.push(uri);
      throw new Error('partial write failed');
    };
    await assert.rejects(
      () => shareAnalyticsCsvWithApi('Section,Name\nMetric,Health', dependencies),
      /partial write failed/
    );
    assert.equal(removals.length, 3);
  });

  it('fails before writing when sharing is unavailable or the operation is cancelled', async () => {
    let writes = 0;
    const dependencies: AnalyticsReportShareDependencies = {
      fileSystem: {
        cacheDirectory: 'file:///cache/',
        utf8Encoding: 'utf8',
        writeAsStringAsync: async () => {
          writes += 1;
        },
        deleteAsync: async () => undefined
      },
      sharing: {
        isAvailableAsync: async () => false,
        shareAsync: async () => undefined
      }
    };
    await assert.rejects(() => shareAnalyticsCsvWithApi('a,b', dependencies), /unavailable/);

    const controller = new AbortController();
    controller.abort(new Error('cancelled'));
    await assert.rejects(() => shareAnalyticsCsvWithApi('a,b', dependencies, controller.signal), /cancelled|aborted/i);
    assert.equal(writes, 0);
  });
});
