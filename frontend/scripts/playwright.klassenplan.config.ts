// Konfiguration für den Beleglauf der Schülersicht (`smoke-klassenplan.spec.ts`, E-50).
// Gleiche Einstellungen wie `playwright.config.ts`, nur eine andere Testdatei — dieser Lauf braucht den
// vorhandenen Bestand (angenommener Plan, Klassenkonten) und darf nicht mit dem Abnahmelauf auf leerer
// Datenbank kollidieren.
//   cd frontend && ABNAHME_ADMIN_PW=… npx playwright test -c scripts/playwright.klassenplan.config.ts
import { defineConfig } from 'playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'smoke-klassenplan.spec.ts',
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
