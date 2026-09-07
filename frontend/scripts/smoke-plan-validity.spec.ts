// Kleiner Beleglauf für zwei Befunde vom 05.09. — läuft neben `abnahme.spec.ts` und braucht KEINE leere
// Datenbank, sondern genau den Bestand, den der Nutzer sieht (angenommener Plan, gültig ab dem Annahmetag).
//
//   cd frontend && npx playwright test -c scripts/playwright.config.ts --grep @gilt-ab
//
// 1. F-8: `/plan` startet in der ersten Woche, in der der Plan gilt — das Raster ist voll, nicht leer.
//    In der Woche davor steht das Hinweisbanner mit dem Sprungknopf.
// 2. F-5a: nach `page.reload()` bleibt die Sitzung stehen (`/plan`, nicht `/login`).
import { expect, test, type Page } from 'playwright/test'

const ADMIN_PW = process.env.ABNAHME_ADMIN_PW ?? ''
// Kein Standardkennwort mehr im Quelltext (Befund SICHERHEIT-1, 05.09.).
if (!ADMIN_PW) throw new Error('ABNAHME_ADMIN_PW fehlt: Wert aus deployment/.env (ADMIN_INITIAL_PASSWORD) setzen.')
const SHOTS = '../docs/smoke'

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Benutzername').fill('admin')
  await page.getByLabel('Passwort').fill(ADMIN_PW)
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

test.describe('Geltungsbeginn und Sitzung @gilt-ab', () => {
  test('Plan zeigt die erste gültige Woche, Banner springt dorthin, Reload hält die Sitzung', async ({ page }) => {
    const fehler: string[] = []
    page.on('pageerror', (e) => fehler.push(String(e)))

    await login(page)

    // 1. Standardwoche: /plan ohne ?woche zeigt die Woche, in der der Plan gilt — mit Stunden.
    await page.goto('/plan')
    await expect(page.getByRole('heading', { name: 'Aktiver Plan' })).toBeVisible()
    await expect(page.getByText(/gilt ab /)).toBeVisible()
    const kacheln = page.locator('[data-lesson-id]')
    await expect.poll(() => kacheln.count(), { timeout: 15_000 }).toBeGreaterThan(0)
    await page.screenshot({ path: `${SHOTS}/plan-gilt-ab.png`, fullPage: true })

    // 2. Die Woche davor: leeres Raster, dafür der Hinweis mit Sprungknopf.
    const davor = await page.evaluate(() => {
      const url = new URL(window.location.href)
      const woche = url.searchParams.get('woche')
      const mo = woche ? new Date(`${woche}T00:00:00Z`) : new Date()
      mo.setUTCDate(mo.getUTCDate() - 7)
      return mo.toISOString().slice(0, 10)
    })
    await page.goto(`/plan?woche=${davor}`)
    const banner = page.getByText(/Dieser Plan gilt ab .*\. Diese Woche zeigt den vorherigen Stand\./)
    await expect(banner).toBeVisible({ timeout: 15_000 })
    await page.screenshot({ path: `${SHOTS}/plan-gilt-ab-hinweis.png`, fullPage: true })

    await page.getByRole('button', { name: 'Zur ersten gültigen Woche' }).click()
    await expect(banner).toBeHidden()
    await expect.poll(() => kacheln.count(), { timeout: 15_000 }).toBeGreaterThan(0)

    // 3. F-5a: Neuladen meldet nicht ab.
    await page.reload()
    await expect(page).toHaveURL(/\/plan/)
    await expect(page).not.toHaveURL(/\/login/)
    await expect(page.getByRole('heading', { name: 'Aktiver Plan' })).toBeVisible()
    await page.screenshot({ path: `${SHOTS}/plan-nach-reload.png`, fullPage: true })

    expect(fehler, `Seitenfehler: ${fehler.join(' | ')}`).toHaveLength(0)
  })
})
