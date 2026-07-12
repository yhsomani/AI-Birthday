module.exports = {
  root: true,
  extends: '@react-native',
  overrides: [
    {
      files: ['tools/**/*.mjs'],
      env: { es2022: true, node: true },
      parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
    },
  ],
};
