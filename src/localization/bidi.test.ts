import { bidiIsolate } from './bidi';

describe('bidiIsolate', () => {
  it('isolates raw display text and removes injected direction controls', () => {
    expect(bidiIsolate('ليلى')).toBe('\u2068ليلى\u2069');
    expect(bidiIsolate('A\u202Esha\u2069')).toBe('\u2068Asha\u2069');
  });
});
