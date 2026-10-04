import {defineConfig} from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    // Firebase documents that emulator transaction locks can take up to 30s.
    testTimeout: 35_000,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary'],
      thresholds: {
        branches: 75,
        functions: 100,
        lines: 80,
        statements: 80,
      },
    },
  },
});
