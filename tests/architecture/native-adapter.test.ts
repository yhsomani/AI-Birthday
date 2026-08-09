import type { ProjectionInvalidation } from '../../src/application/ports/AppProjectionPort';
import { BirthdayNativeAdapter } from '../../src/infrastructure/native/BirthdayNativeAdapter';
import type { NativeInvalidationSource } from '../../src/infrastructure/native/NativeInvalidationSource';
import type { NativeRouteSource } from '../../src/infrastructure/native/NativeRouteSource';

describe('BirthdayNativeAdapter fail-closed behavior', () => {
  it('returns a content-free failure when the native module is unavailable', async () => {
    const adapter = new BirthdayNativeAdapter(null);

    await expect(adapter.getEligibility()).resolves.toEqual({
      kind: 'error',
      problem: {
        kind: 'internal',
        supportCode: 'NATIVE_BRIDGE_UNAVAILABLE',
      },
    });
  });

  it('drops malformed invalidations and forwards only strict known areas', () => {
    let emit: ((event: unknown) => void) | undefined;
    const source: NativeInvalidationSource = {
      subscribe: listener => {
        emit = listener;
        return () => undefined;
      },
    };
    const adapter = new BirthdayNativeAdapter(null, source);
    const received: ProjectionInvalidation[] = [];
    adapter.subscribeInvalidations(event => received.push(event));

    emit?.({ revision: '1', areas: ['future-area'] });
    emit?.({ revision: '-1', areas: ['home'] });
    emit?.({ revision: '2', areas: ['home'], unexpected: true });
    emit?.({ revision: '3', areas: ['home', 'activity'] });
    emit?.({ revision: '4', areas: ['route'] });

    expect(received).toEqual([
      { revision: '3', areas: ['home', 'activity'] },
      { revision: '4', areas: ['route'] },
    ]);
  });

  it('requests only opaque metadata for the next iOS composer proposal', async () => {
    const getProjection = jest.fn().mockResolvedValue({
      contractVersion: 1,
      revision: '9',
      generatedAt: '2026-07-12T08:30:00Z',
      kind: 'ok',
      payloadJson: JSON.stringify({
        kind: 'ready',
        proposalId: 'proposal-1',
        occurrenceId: 'occurrence-1',
        occurrenceDate: '2026-07-18',
        recipient: 'Private name',
      }),
    });
    const nativeModule = {
      addListener: jest.fn(),
      executeUserIntent: jest.fn(),
      getProjection,
      removeListeners: jest.fn(),
    } as ConstructorParameters<typeof BirthdayNativeAdapter>[0];
    const adapter = new BirthdayNativeAdapter(nativeModule);

    const result = await adapter.getNextComposerProposal();

    expect(result.kind).toBe('ok');
    expect(getProjection).toHaveBeenCalledWith(
      'messages',
      JSON.stringify({ kind: 'next-composer-proposal' }),
    );
  });

  it('strictly consumes cold and warm native routes without exposing request ids', async () => {
    let emitRoute: ((event: unknown) => void) | undefined;
    const routeSource: NativeRouteSource = {
      subscribe: listener => {
        emitRoute = listener;
        return () => undefined;
      },
    };
    const getProjection = jest
      .fn()
      .mockResolvedValueOnce({
        contractVersion: 1,
        revision: '10',
        generatedAt: '2026-07-12T08:30:00Z',
        kind: 'ok',
        payloadJson: JSON.stringify({
          kind: 'automation-review',
          routeId: '9c65f8be-f37d-4e57-a1c0-b93ddc51658b',
          source: 'birthday-reminder',
        }),
      })
      .mockResolvedValueOnce({
        contractVersion: 1,
        revision: '11',
        generatedAt: '2026-07-12T08:31:00Z',
        kind: 'ok',
        payloadJson: JSON.stringify({
          kind: 'attention',
          routeId: 'a4f2a2c0-8df3-4b2e-b9e4-661a2050d4a1',
          source: 'attention',
        }),
      });
    const nativeModule = {
      addListener: jest.fn(),
      executeUserIntent: jest.fn(),
      getProjection,
      removeListeners: jest.fn(),
    } as ConstructorParameters<typeof BirthdayNativeAdapter>[0];
    const adapter = new BirthdayNativeAdapter(
      nativeModule,
      { subscribe: () => () => undefined },
      routeSource,
    );
    const listener = jest.fn();
    adapter.subscribeRouteAvailable(listener);

    emitRoute?.({ kind: 'available', requestId: 'private' });
    emitRoute?.({ kind: 'future' });
    emitRoute?.({ kind: 'available' });

    expect(listener).toHaveBeenCalledTimes(1);
    await expect(adapter.getPendingRoute()).resolves.toMatchObject({
      kind: 'ok',
      envelope: {
        value: {
          kind: 'automation-review',
          source: 'birthday-reminder',
        },
      },
    });
    await expect(adapter.getPendingRoute()).resolves.toMatchObject({
      kind: 'ok',
      envelope: {
        value: { kind: 'attention', source: 'attention' },
      },
    });
    expect(getProjection).toHaveBeenCalledWith('route', JSON.stringify({}));
  });

  it('uses strict native policy-editor and public-resources projections', async () => {
    const getProjection = jest.fn(async (area: string, requestJson: string) => {
      const request = JSON.parse(requestJson) as { kind?: string };
      const payload =
        area === 'automation' && request.kind === 'policy-editor'
          ? {
              kind: 'configured',
              draft: {
                primaryStart: '09:00',
                primaryEnd: '11:00',
                latePolicy: { kind: 'none' },
                dailyCap: 10,
              },
            }
          : area === 'privacy' && request.kind === 'public-resources'
          ? {
              kind: 'available',
              buildLabel: 'Birthday Autopilot 0.1.0 (1)',
              baseUrl: 'https://birthday-autopilot-prod.web.app',
            }
          : {
              kind: 'none',
            };
      return {
        contractVersion: 1,
        revision: '11',
        generatedAt: '2026-07-12T08:30:00Z',
        kind: 'ok',
        payloadJson: JSON.stringify(payload),
      };
    });
    const nativeModule = {
      addListener: jest.fn(),
      executeUserIntent: jest.fn(),
      getProjection,
      removeListeners: jest.fn(),
    } as ConstructorParameters<typeof BirthdayNativeAdapter>[0];
    const adapter = new BirthdayNativeAdapter(nativeModule);

    await expect(adapter.getPolicyEditor()).resolves.toMatchObject({
      kind: 'ok',
      envelope: { value: { kind: 'configured' } },
    });
    await expect(adapter.getPublicResources()).resolves.toMatchObject({
      kind: 'ok',
      envelope: {
        value: {
          kind: 'available',
          baseUrl: 'https://birthday-autopilot-prod.web.app',
        },
      },
    });
    expect(getProjection).toHaveBeenCalledWith(
      'automation',
      JSON.stringify({ kind: 'policy-editor' }),
    );
    expect(getProjection).toHaveBeenCalledWith(
      'privacy',
      JSON.stringify({ kind: 'public-resources' }),
    );
  });

  it('uses closed lifecycle projection and intent names without identifiers in JavaScript logs', async () => {
    const getProjection = jest.fn(async (area: string) => ({
      contractVersion: 1,
      revision: '12',
      generatedAt: '2026-07-12T08:30:00Z',
      kind: 'ok',
      payloadJson: JSON.stringify(
        area === 'notifications' ? { kind: 'not-requested' } : { kind: 'none' },
      ),
    }));
    const executeUserIntent = jest.fn(async (intent: string) => ({
      contractVersion: 1,
      revision: '13',
      generatedAt: '2026-07-12T08:30:01Z',
      kind: 'ok',
      payloadJson: JSON.stringify(
        intent === 'request-notification-permission'
          ? { kind: 'denied' }
          : intent === 'repair-lifecycle-state'
          ? {
              kind: 'local-wiping',
              id: 'privacy-operation-repair',
              action: 'disconnect-contacts',
              updatedAt: '2026-07-12T08:30:01Z',
            }
          : { kind: 'opened' },
      ),
    }));
    const nativeModule = {
      addListener: jest.fn(),
      executeUserIntent,
      getProjection,
      removeListeners: jest.fn(),
    } as ConstructorParameters<typeof BirthdayNativeAdapter>[0];
    const adapter = new BirthdayNativeAdapter(nativeModule);

    await expect(adapter.getNotificationPermission()).resolves.toMatchObject({
      kind: 'ok',
      envelope: { value: { kind: 'not-requested' } },
    });
    await expect(adapter.getSenderTransferOperation()).resolves.toMatchObject({
      kind: 'ok',
      envelope: { value: { kind: 'none' } },
    });
    await expect(adapter.getCurrentOperation()).resolves.toMatchObject({
      kind: 'ok',
      envelope: { value: { kind: 'none' } },
    });
    await expect(
      adapter.requestNotificationPermission(),
    ).resolves.toMatchObject({
      kind: 'ok',
      envelope: { value: { kind: 'denied' } },
    });
    await expect(adapter.openNotificationSettings()).resolves.toMatchObject({
      kind: 'ok',
      envelope: { value: { kind: 'opened' } },
    });
    await expect(
      adapter.repairLifecycleState({ kind: 'disconnect-contacts' }),
    ).resolves.toMatchObject({
      kind: 'ok',
      envelope: {
        value: {
          kind: 'local-wiping',
          action: 'disconnect-contacts',
        },
      },
    });

    expect(getProjection).toHaveBeenCalledWith('notifications', '{}');
    expect(getProjection).toHaveBeenCalledWith(
      'automation',
      JSON.stringify({ kind: 'sender-transfer-operation' }),
    );
    expect(getProjection).toHaveBeenCalledWith(
      'privacy',
      JSON.stringify({ kind: 'current-operation' }),
    );
    expect(executeUserIntent).toHaveBeenCalledWith(
      'request-notification-permission',
      null,
      '{}',
    );
    expect(executeUserIntent).toHaveBeenCalledWith(
      'open-notification-settings',
      null,
      '{}',
    );
    expect(executeUserIntent).toHaveBeenCalledWith(
      'repair-lifecycle-state',
      null,
      JSON.stringify({ kind: 'disconnect-contacts' }),
    );
  });
});
