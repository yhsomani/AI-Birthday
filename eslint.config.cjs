const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  {
    ignores: [
      'node_modules/**',
      'reports/**',
      'dist/**',
      'android/**',
      'ios/**',
      'plugins/**'
    ]
  },
  expoConfig,
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'eqeqeq': ['error', 'always'],
      'no-console': 'error',
      'no-eval': 'error',
      'no-implied-eval': 'error',
      'no-new-func': 'error',
      'no-throw-literal': 'error',
      'prefer-const': 'error',
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ]
    }
  },
  {
    files: ['src/domain/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['../native/**', '../state/**', '../ui/**', '../application/**'],
              message: 'The domain layer must remain platform and application independent.'
            }
          ]
        }
      ]
    }
  },
  {
    files: ['src/native/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['../ui/**'],
              message: 'Native adapters must not depend on presentation modules.'
            }
          ]
        }
      ]
    }
  }
]);
