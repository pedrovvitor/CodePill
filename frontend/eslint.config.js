import js from '@eslint/js'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import boundaries from 'eslint-plugin-boundaries'
import prettier from 'eslint-config-prettier'

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'src/domain/catalog/api-types.gen.ts'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      reactHooks.configs.flat['recommended-latest'],
      reactRefresh.configs.vite,
      prettier,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    rules: {
      // ARCHITECTURE.md §4.1 — `any` is banned
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
  {
    // ARCHITECTURE.md §4.1 rule 5 — dependency direction:
    // ui → application → domain; infrastructure implements application ports;
    // app (bootstrap) may wire everything together.
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      'boundaries/elements': [
        { type: 'domain', pattern: 'src/domain/**' },
        { type: 'application', pattern: 'src/application/**' },
        { type: 'infrastructure', pattern: 'src/infrastructure/**' },
        { type: 'ui', pattern: 'src/ui/**' },
        { type: 'app', pattern: 'src/app/**' },
        { type: 'test-support', pattern: 'src/test/**' },
        { type: 'entry', pattern: 'src/main.tsx', mode: 'full' },
      ],
    },
    rules: {
      'boundaries/element-types': [
        'error',
        {
          default: 'disallow',
          rules: [
            { from: 'domain', allow: ['domain'] },
            { from: 'application', allow: ['application', 'domain'] },
            { from: 'infrastructure', allow: ['infrastructure', 'application', 'domain'] },
            { from: 'ui', allow: ['ui', 'application', 'domain'] },
            { from: 'app', allow: ['app', 'ui', 'application', 'infrastructure', 'domain'] },
            { from: 'entry', allow: ['app'] },
            {
              from: 'test-support',
              allow: ['test-support', 'domain', 'application', 'infrastructure', 'ui'],
            },
          ],
        },
      ],
    },
  },
)
