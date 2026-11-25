import { describe, expect, it } from 'vitest';

import { runBounded } from '../src/services/runBounded.js';

describe('runBounded', () => {
  it('processes every item while respecting the concurrency ceiling', async () => {
    let active = 0;
    let peak = 0;
    const completed: number[] = [];

    await runBounded([0, 1, 2, 3, 4, 5, 6], 3, async value => {
      active += 1;
      peak = Math.max(peak, active);
      await new Promise(resolve => setTimeout(resolve, 1));
      completed.push(value);
      active -= 1;
    });

    expect(peak).toBe(3);
    expect(completed.sort((left, right) => left - right)).toEqual([
      0, 1, 2, 3, 4, 5, 6,
    ]);
  });

  it('handles empty work and clamps invalid concurrency to one worker', async () => {
    const completed: number[] = [];
    await runBounded<number>([], 3, () => Promise.resolve());
    await runBounded([1, 2], 0, value => {
      completed.push(value);
      return Promise.resolve();
    });

    expect(completed).toEqual([1, 2]);
  });
});
