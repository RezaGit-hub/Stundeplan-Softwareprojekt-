// Playwright-Konfiguration für den Browser-Durchlauf der Abnahme (scripts/abnahme.spec.ts).
//
// Voraussetzung (nicht in package.json, damit das Repo schlank bleibt): `npm i --no-save playwright@1.60.0`
// und Google Chrome unter /Applications (channel 'chrome' — es wird kein Browser heruntergeladen).
// Aufruf gegen den Compose-Stack (nginx, Port 1500):
//   cd frontend && npx playwright test -c scripts/playwright.config.ts
// Gegen den Vite-Entwicklungsserver (Proxy auf das Backend):
//   ABNAHME_BASE=http://localhost:5193 npx playwright test -c scripts/playwright.config.ts
// Passwort des Kontos `admin`: ABNAHME_ADMIN_PW ist Pflicht, der Wert steht in deployment/.env
// (ADMIN_INITIAL_PASSWORD) und niemals im Quelltext.
import { defineConfig } from 'playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'abnahme.spec.ts',
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
