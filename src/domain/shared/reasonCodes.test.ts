import { SAFE_REASON_CODES } from './reasonCodes';
import { safeReasonMessageKeys } from '../../localization/reasonCopy';

describe('SAFE_REASON_CODES', () => {
  it('contains every code the AI entitlement gate can emit', () => {
    expect(SAFE_REASON_CODES).toEqual(
      expect.arrayContaining([
        'ai-subscription-required',
        'ai-quota-exhausted',
        'ai-usage-period-mismatch',
      ]),
    );
  });

  it('has user copy for every reason code (no raw labels leak into the UI)', () => {
    for (const code of SAFE_REASON_CODES) {
      expect(safeReasonMessageKeys[code]).toMatch(/^live\./u);
    }
  });

  it('maps exhaustive coverage: copy keys never exceed the code set', () => {
    expect(Object.keys(safeReasonMessageKeys).sort()).toEqual(
      [...SAFE_REASON_CODES].sort(),
    );
  });
});
