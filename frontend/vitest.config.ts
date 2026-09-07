import { defineConfig, mergeConfig } from 'vitest/config'
import { baseConfig } from './vite.config.ts'

// Tests laufen ohne Kunstlatenz gegen die Attrappe (VITE_MOCK_LATENCY=0).
//
// Zwei Sorten Test, eine Konfiguration (Befund FRONTEND-6, 05.09.):
//   *.test.ts  — Logik ohne Oberfläche (Attrappe, Wettlauf, Sitzung, Datumsrechnung); Umgebung `node`.
//   *.test.tsx — gerenderte Komponenten mit @testing-library/react; jede dieser Dateien beginnt mit
//                `// @vitest-environment jsdom` und bekommt damit ein DOM, ohne dass die node-Tests
//                eines brauchen (jsdom kostet je Datei Startzeit).
export default mergeConfig(baseConfig('test'), defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    setupFiles: ['./src/test-setup.ts'],
    env: { VITE_MOCK: 'true', VITE_MOCK_LATENCY: '0' },
  },
}))
