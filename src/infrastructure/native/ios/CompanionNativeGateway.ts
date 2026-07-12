import { NativeModules, Platform } from 'react-native';
import { z } from 'zod';

const safeCodeSchema = z.string().regex(/^[A-Z][A-Z0-9_]{2,63}$/u);
const opaqueIdSchema = z.string().regex(/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/u);
const localDateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/u);
const maskedDestinationSchema = z.string().regex(/^•••• \d{4}$/u);
const forbiddenBidiPattern =
  /[\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]/u;
const urlPattern = /(?:https?:\/\/|www\.)\S+/iu;

const hasUnsafeControl = (value: string): boolean =>
  Array.from(value).some(character => {
    const codePoint = character.codePointAt(0);
    return (
      codePoint !== undefined &&
      ((codePoint <= 0x1f &&
        codePoint !== 0x09 &&
        codePoint !== 0x0a &&
        codePoint !== 0x0d) ||
        codePoint === 0x7f)
    );
  });

const safePrivateBodySchema = z
  .string()
  .min(1)
  .max(1_000)
  .refine(value => value.trim().length > 0)
  .refine(value => !hasUnsafeControl(value))
  .refine(value => !forbiddenBidiPattern.test(value))
  .refine(value => !urlPattern.test(value));

const composerReviewRequestSchema = z
  .object({
    expectedRevision: z.string().regex(/^(0|[1-9]\d{0,18})$/u),
    proposalId: opaqueIdSchema,
  })
  .strict();

const composerReviewProjectionSchema = z
  .object({
    actionNonce: z.string().regex(/^[A-Za-z0-9_-]{43}$/u),
    body: safePrivateBodySchema,
    expiresAtEpochMilliseconds: z.number().finite().nonnegative(),
    maskedDestination: maskedDestinationSchema,
    proposalId: opaqueIdSchema,
    revision: z.string().regex(/^(0|[1-9]\d{0,18})$/u),
  })
  .strict();

const composerOpenRequestSchema = composerReviewRequestSchema.extend({
  actionNonce: z.string().regex(/^[A-Za-z0-9_-]{43}$/u),
});

const reminderPlanSchema = z
  .object({
    civilDate: localDateSchema,
    hour: z.number().int().min(0).max(23),
    minute: z.number().int().min(0).max(59),
    occurrenceId: opaqueIdSchema,
  })
  .strict();

const reminderPlansSchema = z
  .array(reminderPlanSchema)
  .max(500)
  .superRefine((plans, context) => {
    const seen = new Set<string>();
    plans.forEach((plan, index) => {
      if (seen.has(plan.occurrenceId)) {
        context.addIssue({
          code: 'custom',
          message: 'duplicate occurrence',
          path: [index, 'occurrenceId'],
        });
      }
      seen.add(plan.occurrenceId);
    });
  });

const composerOutcomeSchema = z.enum([
  'cancelled',
  'failed',
  'reported-sent',
  'unknown',
]);

const reminderStateSchema = z
  .object({
    authorization: z.enum([
      'authorized',
      'denied',
      'ephemeral',
      'not-determined',
      'provisional',
      'unknown',
    ]),
    code: safeCodeSchema.optional(),
    earliestUnscheduledCivilDate: localDateSchema.optional(),
    failedCount: z.number().int().min(0).max(60),
    kind: z.enum(['error', 'ok']),
    plannedDateCount: z.number().int().min(0).max(500),
    scheduledCount: z.number().int().min(0).max(60),
    truncated: z.boolean(),
  })
  .strict();

const simpleResultSchema = z
  .object({
    code: safeCodeSchema.optional(),
    kind: z.enum(['error', 'ok']),
  })
  .strict();

export type CompanionComposerReviewRequest = z.input<
  typeof composerReviewRequestSchema
>;
export type CompanionComposerReviewProjection = z.output<
  typeof composerReviewProjectionSchema
>;
export type CompanionComposerOpenRequest = z.input<
  typeof composerOpenRequestSchema
>;
export type CompanionComposerOutcome = z.output<typeof composerOutcomeSchema>;
export type CompanionReminderPlan = z.input<typeof reminderPlanSchema>;
export type CompanionReminderState = z.output<typeof reminderStateSchema>;

export type CompanionNativeResult<Value> =
  | Readonly<{ kind: 'ok'; value: Value }>
  | Readonly<{ code: string; kind: 'error' }>;

interface RawCompanionMessageModule {
  canPresent(): Promise<unknown>;
  prepareComposerReview(
    request: CompanionComposerReviewRequest,
  ): Promise<unknown>;
  presentUserConfirmedComposer(
    request: CompanionComposerOpenRequest,
  ): Promise<unknown>;
}

interface RawCompanionReminderModule {
  cancelAppOwned(): Promise<unknown>;
  getStatus(): Promise<unknown>;
  openNotificationSettings?(): Promise<unknown>;
  replacePlans(plans: readonly CompanionReminderPlan[]): Promise<unknown>;
  requestAuthorization(): Promise<unknown>;
  wipeCompanionData(): Promise<unknown>;
}

const errorCodeFrom = (error: unknown, fallback: string): string => {
  if (typeof error !== 'object' || error === null || !('code' in error)) {
    return fallback;
  }
  const parsed = safeCodeSchema.safeParse(error.code);
  return parsed.success ? parsed.data : fallback;
};

const moduleWithMethods = <Value>(
  value: unknown,
  methods: readonly string[],
): Value | null => {
  if (typeof value !== 'object' || value === null) {
    return null;
  }
  const record = value as Record<string, unknown>;
  return methods.every(method => typeof record[method] === 'function')
    ? (value as Value)
    : null;
};

export class CompanionNativeGateway {
  public constructor(
    private readonly messageModule: RawCompanionMessageModule | null,
    private readonly reminderModule: RawCompanionReminderModule | null,
  ) {}

  public async canOpenComposer(): Promise<boolean> {
    if (this.messageModule === null) {
      return false;
    }
    try {
      const value = await this.messageModule.canPresent();
      return value === true;
    } catch {
      return false;
    }
  }

  public async openUserConfirmedComposer(
    request: CompanionComposerOpenRequest,
  ): Promise<CompanionNativeResult<CompanionComposerOutcome>> {
    const validated = composerOpenRequestSchema.safeParse(request);
    if (!validated.success) {
      return { code: 'COMPOSER_INPUT_INVALID', kind: 'error' };
    }
    if (this.messageModule === null) {
      return { code: 'IOS_NATIVE_BRIDGE_UNAVAILABLE', kind: 'error' };
    }
    try {
      const raw = await this.messageModule.presentUserConfirmedComposer(
        validated.data,
      );
      const outcome = composerOutcomeSchema.safeParse(raw);
      return outcome.success
        ? { kind: 'ok', value: outcome.data }
        : { code: 'COMPOSER_RESULT_INVALID', kind: 'error' };
    } catch (error) {
      return {
        code: errorCodeFrom(error, 'COMPOSER_NATIVE_FAILURE'),
        kind: 'error',
      };
    }
  }

  public async prepareComposerReview(
    request: CompanionComposerReviewRequest,
  ): Promise<CompanionNativeResult<CompanionComposerReviewProjection>> {
    const validated = composerReviewRequestSchema.safeParse(request);
    if (!validated.success) {
      return { code: 'COMPOSER_INPUT_INVALID', kind: 'error' };
    }
    if (this.messageModule === null) {
      return { code: 'IOS_NATIVE_BRIDGE_UNAVAILABLE', kind: 'error' };
    }
    try {
      const raw = await this.messageModule.prepareComposerReview(validated.data);
      const projection = composerReviewProjectionSchema.safeParse(raw);
      return projection.success
        ? { kind: 'ok', value: projection.data }
        : { code: 'COMPOSER_REVIEW_RESULT_INVALID', kind: 'error' };
    } catch (error) {
      return {
        code: errorCodeFrom(error, 'COMPOSER_NATIVE_FAILURE'),
        kind: 'error',
      };
    }
  }

  public getReminderStatus(): Promise<CompanionNativeResult<CompanionReminderState>> {
    return this.reminderCall('getStatus');
  }

  public requestReminderAuthorization(): Promise<
    CompanionNativeResult<CompanionReminderState>
  > {
    return this.reminderCall('requestAuthorization');
  }

  public openNotificationSettings(): Promise<CompanionNativeResult<null>> {
    return this.simpleReminderCall('openNotificationSettings');
  }

  public async replaceReminderPlans(
    plans: readonly CompanionReminderPlan[],
  ): Promise<CompanionNativeResult<CompanionReminderState>> {
    const validated = reminderPlansSchema.safeParse(plans);
    if (!validated.success) {
      return { code: 'REMINDER_INPUT_INVALID', kind: 'error' };
    }
    if (this.reminderModule === null) {
      return { code: 'IOS_NATIVE_BRIDGE_UNAVAILABLE', kind: 'error' };
    }
    try {
      const raw = await this.reminderModule.replacePlans(validated.data);
      return this.decodeReminderState(raw);
    } catch (error) {
      return {
        code: errorCodeFrom(error, 'REMINDER_NATIVE_FAILURE'),
        kind: 'error',
      };
    }
  }

  public cancelReminders(): Promise<CompanionNativeResult<null>> {
    return this.simpleReminderCall('cancelAppOwned');
  }

  public wipeCompanionData(): Promise<CompanionNativeResult<null>> {
    return this.simpleReminderCall('wipeCompanionData');
  }

  private async reminderCall(
    method: 'getStatus' | 'requestAuthorization',
  ): Promise<CompanionNativeResult<CompanionReminderState>> {
    if (this.reminderModule === null) {
      return { code: 'IOS_NATIVE_BRIDGE_UNAVAILABLE', kind: 'error' };
    }
    try {
      const raw = await this.reminderModule[method]();
      return this.decodeReminderState(raw);
    } catch (error) {
      return {
        code: errorCodeFrom(error, 'REMINDER_NATIVE_FAILURE'),
        kind: 'error',
      };
    }
  }

  private decodeReminderState(
    raw: unknown,
  ): CompanionNativeResult<CompanionReminderState> {
    const state = reminderStateSchema.safeParse(raw);
    if (!state.success) {
      return { code: 'REMINDER_RESULT_INVALID', kind: 'error' };
    }
    if (state.data.kind === 'error') {
      return {
        code: state.data.code ?? 'REMINDER_NATIVE_FAILURE',
        kind: 'error',
      };
    }
    return { kind: 'ok', value: state.data };
  }

  private async simpleReminderCall(
    method:
      | 'cancelAppOwned'
      | 'openNotificationSettings'
      | 'wipeCompanionData',
  ): Promise<CompanionNativeResult<null>> {
    if (
      this.reminderModule === null ||
      typeof this.reminderModule[method] !== 'function'
    ) {
      return { code: 'IOS_NATIVE_BRIDGE_UNAVAILABLE', kind: 'error' };
    }
    try {
      const raw = await this.reminderModule[method]!();
      const decoded = simpleResultSchema.safeParse(raw);
      if (!decoded.success) {
        return { code: 'REMINDER_RESULT_INVALID', kind: 'error' };
      }
      return decoded.data.kind === 'ok'
        ? { kind: 'ok', value: null }
        : {
            code: decoded.data.code ?? 'REMINDER_NATIVE_FAILURE',
            kind: 'error',
          };
    } catch (error) {
      return {
        code: errorCodeFrom(error, 'REMINDER_NATIVE_FAILURE'),
        kind: 'error',
      };
    }
  }
}

export const createCompanionNativeGateway = (): CompanionNativeGateway => {
  if (Platform.OS !== 'ios') {
    return new CompanionNativeGateway(null, null);
  }
  const modules = NativeModules as unknown as Record<string, unknown>;
  const messageModule = moduleWithMethods<RawCompanionMessageModule>(
    modules.CompanionMessageModule,
    ['canPresent', 'prepareComposerReview', 'presentUserConfirmedComposer'],
  );
  const reminderModule = moduleWithMethods<RawCompanionReminderModule>(
    modules.CompanionReminderModule,
    [
      'cancelAppOwned',
      'getStatus',
      'replacePlans',
      'requestAuthorization',
      'wipeCompanionData',
    ],
  );
  return new CompanionNativeGateway(messageModule, reminderModule);
};
