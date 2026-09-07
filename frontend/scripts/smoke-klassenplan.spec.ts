// Beleglauf der Schülersicht (E-50) gegen den laufenden Stack — braucht den vorhandenen Bestand
// (angenommener Plan, Klassenkonten), nicht die leere Datenbank des Abnahmelaufs.
//
//   cd frontend && ABNAHME_ADMIN_PW=… npx playwright test -c scripts/playwright.klassenplan.config.ts
//
// Der Lauf setzt sich das Klassenpasswort selbst über PUT /api/users/{id}/password (die Planung darf das)
// und behält es in einer Variablen. **Es wird nirgends ausgegeben** — weder in der Konsole noch im
// Screenshot: die Anmeldung tippt es in ein Feld vom Typ `password`.
import { expect, test } from 'playwright/test'

const ADMIN_PW = process.env.ABNAHME_ADMIN_PW ?? ''
// Kein Standardkennwort mehr im Quelltext (Befund SICHERHEIT-1, 05.09.).
if (!ADMIN_PW) throw new Error('ABNAHME_ADMIN_PW fehlt: Wert aus deployment/.env (ADMIN_INITIAL_PASSWORD) setzen.')
/** Woche, in der der angenommene Plan gilt (F-8: ein Plan gilt ab dem Tag seiner Annahme). */
const WOCHE = process.env.KLASSENPLAN_WOCHE ?? '2026-09-07'
const SHOTS = '../docs/smoke'

const ALPHABET = 'abcdefghijkmnpqrstuvwxyz23456789'
function neuesPasswort(): string {
  let out = ''
  for (let i = 0; i < 12; i += 1) out += ALPHABET[Math.floor(Math.random() * ALPHABET.length)]
  return out
}

test.describe('Klassenplan @klassenplan', () => {
  test('Klassenkonto sieht nur seinen Klassenplan — Screenshot', async ({ page, request }) => {
    const fehler: string[] = []
    page.on('pageerror', (e) => fehler.push(String(e)))

    // 1. Als Planung ein Klassenkonto suchen und ihm ein frisches Passwort geben.
    const anmeldung = await request.post('/api/auth/login', { data: { username: 'admin', password: ADMIN_PW } })
    expect(anmeldung.ok(), 'Anmeldung der Planung').toBeTruthy()
    const adminToken = (await anmeldung.json()).token as string

    const konten = await (await request.get('/api/users', { headers: { Authorization: `Bearer ${adminToken}` } })).json()
    const klassenkonten = (konten as { id: number; username: string; role: string; classShort: string | null }[])
      .filter((k) => k.role === 'STUDENT')
    // Bevorzugt die 7a (Testdatenbestand des Pflichtenhefts), sonst das erste Klassenkonto.
    const gewuenscht = process.env.KLASSENPLAN_KLASSE ?? '7a'
    const klassenkonto = klassenkonten.find((k) => k.classShort === gewuenscht) ?? klassenkonten[0]
    expect(klassenkonto, 'es gibt mindestens ein Klassenkonto (POST /api/users/class-accounts)').toBeTruthy()

    const passwort = neuesPasswort()
    const gesetzt = await request.put(`/api/users/${klassenkonto!.id}/password`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { password: passwort },
    })
    expect(gesetzt.status(), 'Passwort gesetzt').toBe(204)

    // 2. Über die Oberfläche anmelden — nicht über die API: geprüft wird der Weg, den die Klasse geht.
    await page.goto('/login')
    await page.getByLabel('Benutzername').fill(klassenkonto!.username)
    await page.getByLabel('Passwort').fill(passwort)
    await page.getByRole('button', { name: 'Anmelden' }).click()

    // 3. Die Anmeldung landet ohne Umweg auf /klassenplan.
    await expect(page).toHaveURL(/\/klassenplan/)
    await expect(page.getByRole('heading', { name: 'Klassenplan' })).toBeVisible()

    // 4. Die Woche, in der der Plan gilt — sonst ist das Raster leer (F-8).
    await page.goto(`/klassenplan?woche=${WOCHE}`)
    const kacheln = page.locator('[data-lesson-id]')
    await expect.poll(() => kacheln.count(), { timeout: 15_000 }).toBeGreaterThan(0)

    // 5. Die Seitenleiste zeigt genau eine Seite plus Abmelden — keine Planungseinträge.
    //    Über den Verweis (href) gesucht, nicht über den Text: „Klassenplan" steht auch in der Brotkrume.
    await expect(page.locator('a[href="/klassenplan"]')).toHaveCount(2) // Kopfzeile der Leiste + Menüeintrag
    await expect(page.getByRole('button', { name: 'Abmelden' })).toBeVisible()
    for (const fremd of ['/', '/plan', '/entwurf', '/vertretung', '/verlauf', '/einstellungen',
      '/grunddaten/lehrkraefte', '/grunddaten/klassen', '/mein-plan']) {
      await expect(page.locator(`a[href="${fremd}"]`), `kein Verweis auf ${fremd}`).toHaveCount(0)
    }

    // 6. Eine fremde Route führt zurück auf den Klassenplan (RequireRole).
    await page.goto('/grunddaten/lehrkraefte')
    await expect(page).toHaveURL(/\/klassenplan/)

    await page.goto(`/klassenplan?woche=${WOCHE}`)
    await expect.poll(() => kacheln.count(), { timeout: 15_000 }).toBeGreaterThan(0)
    await page.screenshot({ path: `${SHOTS}/klassenplan.png`, fullPage: true })

    expect(fehler, 'keine Seitenfehler').toEqual([])
  })
})
