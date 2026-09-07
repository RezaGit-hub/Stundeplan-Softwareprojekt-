import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { globalIgnores } from 'eslint/config'

// Team-Datei (web-app/frontend/eslint.config.js) + typescript-eslint. Generierte shadcn-Dateien werden nicht "repariert".
export default tseslint.config([
  // src/example/** ist das JSX-Gerüst des Teams (nicht geroutet, nur zum Nachlesen) — es wird nicht gelintet.
  globalIgnores(['dist', '.demo-dist', '.agent-out', 'src/example/**']),
  {
    files: ['**/*.{js,jsx,ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // Provider-Dateien exportieren Komponente + Hook (auth.tsx, theme-provider.tsx): Warnung, kein Fehler
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  {
    // shadcn-Generat: unverändert lassen (Ausnahme sonner.tsx-Import), deshalb hier keine React-Compiler-Regeln
    files: ['src/components/ui/**', 'src/hooks/use-mobile.ts'],
    rules: {
      'react-refresh/only-export-components': 'off',
      'react-hooks/set-state-in-effect': 'off',
    },
  },
])
