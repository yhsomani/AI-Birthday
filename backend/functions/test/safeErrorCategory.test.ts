import { describe, expect, it } from 'vitest';

import { safeErrorCategory } from '../src/services/safeErrorCategory.js';

describe('safeErrorCategory', () => {
  it('returns an allowlisted category without exposing error messages or names', () => {
    const unsafeError = new Error('request body and account details') as Error & {
      name: string;
    };
    unsafeError.name = 'SensitiveErrorName';

    expect(safeErrorCategory(unsafeError)).toBe('Error');
    expect(safeErrorCategory(new TypeError('secret'))).toBe('TypeError');
    expect(safeErrorCategory({ message: 'secret' })).toBe('Unknown');
  });
});
