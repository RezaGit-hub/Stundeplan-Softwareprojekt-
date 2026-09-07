// Konfiguration für den kleinen Beleglauf `smoke-plan-validity.spec.ts` (Befunde F-8 und F-5a).
// Gleiche Einstellungen wie `playwright.config.ts`, nur eine andere Testdatei — dieser Lauf braucht den
// vorhandenen Bestand (angenommener Plan) und darf nicht mit dem Abnahmelauf auf leerer Datenbank kollidieren.
//   cd frontend && npx playwright test -c scripts/playwright.smoke.config.ts
import { defineConfig } from 'playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'smoke-plan-validity.spec.ts',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 8_000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.ABNAHME_BASE ?? 'http://localhost:1500',
    channel: 'chrome',
    headless: true,
    viewport: { width: 1440, height: 900 },
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
  },
})
