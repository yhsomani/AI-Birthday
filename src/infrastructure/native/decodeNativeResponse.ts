import { z } from 'zod';

import type { SafeSupportCode } from '../../domain/shared/brand';
import type {
  NativeResult,
  ProjectionEnvelope,
} from '../../domain/shared/result';
import {
  nativeProblemSchema,
  nativeRevisionSchema,
  strictObject,
  utcInstantSchema,
} from './schemaPrimitives';

const MAX_NATIVE_PAYLOAD_CHARS = 1_048_576;

const rawResponseSchema = strictObject({
  contractVersion: z.literal(1),
  revision: nativeRevisionSchema,
  generatedAt: utcInstantSchema,
  kind: z.enum(['ok', 'error']),
  payloadJson: z.string().max(MAX_NATIVE_PAYLOAD_CHARS),
});

const CONTRACT_SUPPORT_CODE = 'NATIVE_CONTRACT_INVALID' as SafeSupportCode;

const contractFailure = <Value>(): NativeResult<Value> => ({
  kind: 'error',
  problem: { kind: 'internal', supportCode: CONTRACT_SUPPORT_CODE },
});

const parseJson = (value: string): unknown => {
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return undefined;
  }
};

export const decodeNativeResponse = <Value>(
  raw: unknown,
  valueSchema: z.ZodType<Value>,
): NativeResult<Value> => {
  const response = rawResponseSchema.safeParse(raw);
  if (!response.success) {
    return contractFailure();
  }

  const parsedPayload = parseJson(response.data.payloadJson);
  if (parsedPayload === undefined) {
    return contractFailure();
  }

  if (response.data.kind === 'error') {
    const problem = nativeProblemSchema.safeParse(parsedPayload);
    return problem.success
      ? { kind: 'error', problem: problem.data }
      : contractFailure();
  }

  const value = valueSchema.safeParse(parsedPayload);
  if (!value.success) {
    return contractFailure();
  }

  const envelope: ProjectionEnvelope<Value> = {
    contractVersion: 1,
    revision: response.data.revision,
    generatedAt: response.data.generatedAt,
    value: value.data,
  };

  return { kind: 'ok', envelope };
};
