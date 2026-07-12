module.exports = {
  preset: '@react-native/jest-preset',
  // Firebase Functions is an intentionally isolated Node/Vitest package.
  // Keeping it outside the mobile Jest graph prevents incompatible runners
  // from collecting each other's suites while preserving explicit backend CI.
  testPathIgnorePatterns: ['/node_modules/', '<rootDir>/backend/'],
};
