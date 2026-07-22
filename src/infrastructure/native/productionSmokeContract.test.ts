import type { Spec as BirthdayNativeSpec } from '../../../specs/native/NativeBirthday';
import fixtureDocument from '../../../e2e/production-smoke/production-smoke-projections.json';
import type { ContactId, NativeRevision } from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import { BirthdayNativeAdapter } from './BirthdayNativeAdapter';

type SmokePlatform = 'android' | 'ios';
type Fixture = Readonly<{
  generatedAt: string;
  intentProblem: Readonly<{ code: string; kind: 'unsupported' }>;
  platforms: Readonly<Record<SmokePlatform, Readonly<Record<string, unknown>>>>;
  revision: string;
  schemaVersion: 1;
}>;

const fixture = fixtureDocument as unknown as Fixture;

const response = (kind: 'error' | 'ok', payload: unknown) => ({
  contractVersion: fixture.schemaVersion,
  generatedAt: fixture.generatedAt,
  kind,
  payloadJson: JSON.stringify(payload),
  revision: fixture.revision,
});

const projectionKey = (area: string, request: Record<string, unknown>) => {
  if (
    [
      'account',
      'bootstrap',
      'eligibility',
      'home',
      'notifications',
      'readiness',
      'route',
      'setup',
    ].includes(area)
  ) {
    return area;
  }
  return typeof request.kind === 'string' ? `${area}:${request.kind}` : '';
};

const nativeModule = (platform: SmokePlatform): BirthdayNativeSpec =>
  ({
    addListener: jest.fn(),
    executeUserIntent: jest.fn(async () =>
      response('error', fixture.intentProblem),
    ),
    getProjection: jest.fn(async (area: string, requestJson: string) => {
      const request = JSON.parse(requestJson) as Record<string, unknown>;
      const payload = fixture.platforms[platform][projectionKey(area, request)];
      return payload === undefined
        ? response('error', {
            code: 'native-bridge-unavailable',
            kind: 'unsupported',
          })
        : response('ok', payload);
    }),
    removeListeners: jest.fn(),
  }) as unknown as BirthdayNativeSpec;

const valueOf = <Value>(result: NativeResult<Value>): Value => {
  expect(result.kind).toBe('ok');
  if (result.kind !== 'ok') {
    throw new Error('Expected a schema-valid production-smoke projection');
  }
  return result.envelope.value;
};

describe.each<SmokePlatform>(['android', 'ios'])(
  '%s production-path native smoke projections',
  platform => {
    it('decodes every allowlisted projection through the production adapter', async () => {
      const adapter = new BirthdayNativeAdapter(nativeModule(platform));
      const results = await Promise.all([
        adapter.getAccount(),
        adapter.getBootstrap(),
        adapter.getEligibility(),
        adapter.getHome(),
        adapter.getNotificationPermission(),
        adapter.getPendingRoute(),
        adapter.getReadiness(),
        adapter.getSetup(),
        adapter.listPeople({ filter: 'all', pageSize: 20 }),
        adapter.getMessageEditor(),
        adapter.getNextComposerProposal(),
        adapter.getApproval('smoke-contact' as ContactId),
        adapter.getLatestTest(),
        adapter.getPolicyEditor(),
        adapter.getSenderTransferOperation(),
        adapter.listActivity({ pageSize: 20 }),
        adapter.listIssues(),
        adapter.getCurrentOperation(),
        adapter.getInventory(),
        adapter.getLatestDeletionReceipt(),
        adapter.getPublicResources(),
      ]);

      results.forEach(result => expect(result.kind).toBe('ok'));
      const people = valueOf(
        await adapter.listPeople({ filter: 'all', pageSize: 20 }),
      );
      const activity = valueOf(await adapter.listActivity({ pageSize: 20 }));
      const inventory = valueOf(await adapter.getInventory());
      expect(people).toMatchObject({ items: [], totalCount: 0 });
      expect(activity).toMatchObject({
        items: [
          {
            id: 'smoke.activity.1',
            kind: 'settings-changed',
            occurredAt: '2026-07-12T00:00:00.000Z',
          },
        ],
      });
      expect(inventory).toMatchObject({
        activityCount: 1,
        approvalCount: 0,
        enabledRecipientCount: 0,
        localContactCount: 0,
        localStorageBytes: 0,
        templateCount: 0,
      });
    });

    it('fails every adapter intent closed with the same synthetic problem', async () => {
      const module = nativeModule(platform);
      const adapter = new BirthdayNativeAdapter(module);
      const expected = { kind: 'error', problem: fixture.intentProblem };

      await expect(adapter.continueWithGoogle()).resolves.toEqual(expected);
      await expect(
        adapter.pauseAll({ expectedRevision: '1' as NativeRevision }),
      ).resolves.toEqual(expected);
      expect(module.executeUserIntent).toHaveBeenCalledTimes(2);
    });
  },
);
