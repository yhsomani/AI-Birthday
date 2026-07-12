import { CompanionNativeGateway } from './CompanionNativeGateway';

const validReviewRequest = () => ({
  expectedRevision: '7',
  proposalId: 'proposal-1',
});

const validOpenRequest = () => ({
  ...validReviewRequest(),
  actionNonce: 'a'.repeat(43),
});

const validReviewProjection = () => ({
  actionNonce: 'a'.repeat(43),
  body: 'Happy birthday!',
  expiresAtEpochMilliseconds: 1_800_000_000_000,
  maskedDestination: '•••• 3210',
  proposalId: 'proposal-1',
  revision: '7',
});

const validReminderState = {
  authorization: 'authorized',
  failedCount: 0,
  kind: 'ok',
  plannedDateCount: 2,
  scheduledCount: 2,
  truncated: false,
} as const;

describe('CompanionNativeGateway', () => {
  it('fails closed when native iOS modules are unavailable', async () => {
    const gateway = new CompanionNativeGateway(null, null);

    await expect(gateway.canOpenComposer()).resolves.toBe(false);
    await expect(
      gateway.openUserConfirmedComposer(validOpenRequest()),
    ).resolves.toEqual({
      code: 'IOS_NATIVE_BRIDGE_UNAVAILABLE',
      kind: 'error',
    });
    await expect(gateway.getReminderStatus()).resolves.toEqual({
      code: 'IOS_NATIVE_BRIDGE_UNAVAILABLE',
      kind: 'error',
    });
  });

  it('accepts only truthful closed composer outcomes', async () => {
    const present = jest.fn().mockResolvedValue('reported-sent');
    const gateway = new CompanionNativeGateway(
      {
        canPresent: jest.fn().mockResolvedValue(true),
        prepareComposerReview: jest.fn().mockResolvedValue(validReviewProjection()),
        presentUserConfirmedComposer: present,
      },
      null,
    );

    await expect(
      gateway.openUserConfirmedComposer(validOpenRequest()),
    ).resolves.toEqual({ kind: 'ok', value: 'reported-sent' });
    expect(present).toHaveBeenCalledTimes(1);

    present.mockResolvedValueOnce('delivered');
    await expect(
      gateway.openUserConfirmedComposer(validOpenRequest()),
    ).resolves.toEqual({
      code: 'COMPOSER_RESULT_INVALID',
      kind: 'error',
    });
  });

  it('loads the reviewed payload from native protected state', async () => {
    const prepare = jest.fn().mockResolvedValue(validReviewProjection());
    const gateway = new CompanionNativeGateway(
      {
        canPresent: jest.fn().mockResolvedValue(true),
        prepareComposerReview: prepare,
        presentUserConfirmedComposer: jest.fn(),
      },
      null,
    );

    await expect(
      gateway.prepareComposerReview(validReviewRequest()),
    ).resolves.toEqual({ kind: 'ok', value: validReviewProjection() });
    expect(prepare).toHaveBeenCalledWith(validReviewRequest());
  });

  it('rejects a native review response that exposes a raw recipient', async () => {
    const gateway = new CompanionNativeGateway(
      {
        canPresent: jest.fn().mockResolvedValue(true),
        prepareComposerReview: jest.fn().mockResolvedValue({
          ...validReviewProjection(),
          recipients: ['+919876543210'],
        }),
        presentUserConfirmedComposer: jest.fn(),
      },
      null,
    );

    await expect(
      gateway.prepareComposerReview(validReviewRequest()),
    ).resolves.toEqual({
      code: 'COMPOSER_REVIEW_RESULT_INVALID',
      kind: 'error',
    });
  });

  it('does not leak native rejection messages', async () => {
    const gateway = new CompanionNativeGateway(
      {
        canPresent: jest.fn().mockResolvedValue(true),
        prepareComposerReview: jest.fn().mockResolvedValue(validReviewProjection()),
        presentUserConfirmedComposer: jest.fn().mockRejectedValue({
          code: 'unsafe phone +919876543210',
          message: 'private message body',
        }),
      },
      null,
    );

    await expect(
      gateway.openUserConfirmedComposer(validOpenRequest()),
    ).resolves.toEqual({
      code: 'COMPOSER_NATIVE_FAILURE',
      kind: 'error',
    });
  });

  it('never accepts a free-form recipient or body at presentation', async () => {
    const present = jest.fn();
    const gateway = new CompanionNativeGateway(
      {
        canPresent: jest.fn().mockResolvedValue(true),
        prepareComposerReview: jest.fn().mockResolvedValue(validReviewProjection()),
        presentUserConfirmedComposer: present,
      },
      null,
    );
    const request = {
      ...validOpenRequest(),
      body: 'Visit https://example.com',
      recipients: ['+919876543210'],
    };

    await expect(gateway.openUserConfirmedComposer(request)).resolves.toEqual({
      code: 'COMPOSER_INPUT_INVALID',
      kind: 'error',
    });
    expect(present).not.toHaveBeenCalled();
  });

  it('validates reminder results and bounds duplicate plans', async () => {
    const replacePlans = jest.fn().mockResolvedValue(validReminderState);
    const gateway = new CompanionNativeGateway(null, {
      cancelAppOwned: jest.fn().mockResolvedValue({ kind: 'ok' }),
      getStatus: jest.fn().mockResolvedValue(validReminderState),
      replacePlans,
      requestAuthorization: jest.fn().mockResolvedValue(validReminderState),
      wipeCompanionData: jest.fn().mockResolvedValue({ kind: 'ok' }),
    });

    await expect(
      gateway.replaceReminderPlans([
        {
          civilDate: '2026-08-01',
          hour: 9,
          minute: 0,
          occurrenceId: 'same-occurrence',
        },
        {
          civilDate: '2026-08-02',
          hour: 9,
          minute: 0,
          occurrenceId: 'same-occurrence',
        },
      ]),
    ).resolves.toEqual({ code: 'REMINDER_INPUT_INVALID', kind: 'error' });
    expect(replacePlans).not.toHaveBeenCalled();

    await expect(gateway.getReminderStatus()).resolves.toEqual({
      kind: 'ok',
      value: validReminderState,
    });
  });

  it('opens notification settings only through a strict native result', async () => {
    const openNotificationSettings = jest
      .fn()
      .mockResolvedValueOnce({ kind: 'ok' })
      .mockResolvedValueOnce({ kind: 'ok', privateValue: 'leak' });
    const gateway = new CompanionNativeGateway(null, {
      cancelAppOwned: jest.fn().mockResolvedValue({ kind: 'ok' }),
      getStatus: jest.fn().mockResolvedValue(validReminderState),
      openNotificationSettings,
      replacePlans: jest.fn().mockResolvedValue(validReminderState),
      requestAuthorization: jest.fn().mockResolvedValue(validReminderState),
      wipeCompanionData: jest.fn().mockResolvedValue({ kind: 'ok' }),
    });

    await expect(gateway.openNotificationSettings()).resolves.toEqual({
      kind: 'ok',
      value: null,
    });
    await expect(gateway.openNotificationSettings()).resolves.toEqual({
      code: 'REMINDER_RESULT_INVALID',
      kind: 'error',
    });
  });
});
