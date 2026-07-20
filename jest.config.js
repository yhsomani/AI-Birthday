module.exports = {
  preset: '@react-native/jest-preset',
  // Firebase Functions is an intentionally isolated Node/Vitest package.
  // Keeping it outside the mobile Jest graph prevents incompatible runners
  // from collecting each other's suites while preserving explicit backend CI.
  testPathIgnorePatterns: ['/node_modules/', '<rootDir>/backend/'],
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?|@react-navigation|react-native-svg)/)',
  ],
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.test.{ts,tsx}',
    '!src/**/*.spec.{ts,tsx}',
    '!src/**/*.d.ts',
  ],
  coverageThreshold: {
    global: {
      branches: 67,
      functions: 62,
      lines: 69,
      statements: 69,
    },
    './src/infrastructure/native/coreSchemas.ts': {
      branches: 100,
      functions: 100,
      lines: 100,
      statements: 100,
    },
    './src/infrastructure/native/featureSchemas.ts': {
      branches: 100,
      functions: 100,
      lines: 100,
      statements: 100,
    },
    './src/infrastructure/native/decodeNativeResponse.ts': {
      branches: 90,
      functions: 100,
      lines: 100,
      statements: 100,
    },
    './src/design-system/tokens/theme.ts': {
      branches: 100,
      functions: 100,
      lines: 100,
      statements: 100,
    },
  },
};
