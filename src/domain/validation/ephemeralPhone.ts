import type { EphemeralPhoneInput } from '../shared/brand';
import type { UiDraftValidation } from '../shared/result';

const PHONE_INPUT_PATTERN = /^[+0-9 ()-]{7,32}$/u;

export const validateEphemeralPhoneInput = (
  raw: string,
): UiDraftValidation<EphemeralPhoneInput> => {
  const value = raw.trim();
  const digitCount = value.match(/[0-9]/gu)?.length ?? 0;
  if (!PHONE_INPUT_PATTERN.test(value) || digitCount < 7 || digitCount > 15) {
    return {
      kind: 'invalid',
      authority: 'ui-only',
      issues: [{ field: 'phone', code: 'phone-invalid' }],
    };
  }
  return {
    kind: 'valid',
    authority: 'ui-only',
    value: value as EphemeralPhoneInput,
  };
};
