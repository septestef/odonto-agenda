import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import { configSeguridadHtml } from './src/shared/seguridad-html.ts'

export default tseslint.config(
  { ignores: ['dist/', 'coverage/', 'node_modules/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  configSeguridadHtml,
  {
    files: ['**/*.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
)
