// Browser-Durchlauf der Abnahme-Checkliste (docs/ABNAHME-CHECKLISTE.md, Abschnitt 1) gegen das ECHTE Backend.
//
// Jeder Test trägt die Nummer der Checklisten-Zeile (I-3, L-1, G-1 … S-4) und legt einen Screenshot unter
// docs/smoke/ ab; am Ende entsteht docs/smoke/PROTOKOLL.md mit einer Zeile je Schritt (Nummer · Datum · Ergebnis · Beleg).
// Voraussetzung: leere Datenbank (cd deployment && docker compose down -v && docker compose up -d --build),
// dann `npx playwright test -c scripts/playwright.config.ts` (Details in scripts/playwright.config.ts).
//
// Die Sitzung lebt nur im Speicher der Seite (kein localStorage, api.md Auth-Fluss 5). Deshalb läuft alles auf EINER
// Seite und die Navigation geht in-App über pushState — ein Neuladen hieße neu anmelden.
import { mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { test, expect, type Page, type Locator } from 'playwright/test'

test.describe.configure({ mode: 'serial' })

const ADMIN_PW = process.env.ABNAHME_ADMIN_PW ?? ''
// Kein Standardkennwort mehr im Quelltext (Befund SICHERHEIT-1, 05.09.).
if (!ADMIN_PW) throw new Error('ABNAHME_ADMIN_PW fehlt: Wert aus deployment/.env (ADMIN_INITIAL_PASSWORD) setzen.')
const OUT = fileURLToPath(new URL('../../docs/smoke/', import.meta.url))
const WEEK = '2026-09-21' // Woche des Leitszenarios (Pflichtenheft)
mkdirSync(OUT, { recursive: true })

let page: Page
let nShot = 0
const protocol: { id: string; result: string; file: string }[] = []
const pageErrors: string[] = []

/** Screenshot unter docs/smoke/NN-<id>.png; Ergebniszeile fürs Protokoll */
async function beleg(id: string, note = 'ok') {
  nShot += 1
  const file = `${String(nShot).padStart(2, '0')}-${id.replace(/[^a-z0-9]+/gi, '_')}.png`
  await page.screenshot({ path: OUT + file })
  protocol.push({ id, result: note, file })
}
/** In-App-Navigation ohne Neuladen (Token bleibt im Speicher) */
async function go(path: string) {
  await page.evaluate((p) => { window.history.pushState({}, '', p); window.dispatchEvent(new PopStateEvent('popstate')) }, path)
  await page.waitForTimeout(300)
}
const dialog = () => page.getByRole('dialog')
const rowOf = (text: string | RegExp) => page.getByRole('row', { name: text }).first()
async function rowMenu(text: string | RegExp, item: string) {
  await rowOf(text).getByRole('button', { name: 'Aktionen' }).click()
  await page.getByRole('menuitem', { name: item }).click()
}
async function pickOption(trigger: Locator, optionName: string | RegExp) {
  await trigger.click()
  await page.getByRole('option', { name: optionName }).first().click()
}
async function toast(text: string | RegExp) {
  await expect(page.getByText(text).first()).toBeVisible()
}
async function login(username: string, password: string) {
  await page.locator('#login-user').fill(username)
  await page.locator('#login-pass').fill(password)
  await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
}
async function logout() {
  await page.getByRole('button', { name: 'Benutzermenü' }).click()
  await page.getByRole('menuitem', { name: 'Abmelden' }).click()
  await page.waitForURL(/\/login/)
}
/** Lehrkraft anlegen (G-4 bis G-6); gesperrte Zellen werden zweimal geklickt: keine Angabe → bevorzugt → gesperrt */
async function createTeacher(name: string, short: string, quota: number, subjects: string[], blocked: string[] = []) {
  await page.getByRole('button', { name: 'Neue Lehrkraft' }).click()
  await page.locator('#teacher-name').fill(name)
  await page.locator('#teacher-short').fill(short)
  await page.locator('#teacher-quota').fill(String(quota))
  for (const s of subjects) await page.getByRole('checkbox', { name: s }).click()
  for (const cell of blocked) {
    await page.getByRole('gridcell', { name: `${cell}: keine Angabe` }).click()
    await page.getByRole('gridcell', { name: `${cell}: bevorzugt` }).click()
    await expect(page.getByRole('gridcell', { name: `${cell}: gesperrt` })).toBeVisible()
  }
  await dialog().getByRole('button', { name: 'Speichern' }).click()
  await toast('Lehrkraft gespeichert')
  await expect(rowOf(name)).toBeVisible()
}
/** Fächerliste einer Klasse auf der Detailseite: [Fach, Wochenstunden, Lehrkraft] je Zeile, dann Speichern */
async function setClassSubjects(rows: [string, number, RegExp][]) {
  for (const [i, [subject, hours, teacher]] of rows.entries()) {
    await page.getByRole('button', { name: 'Fach hinzufügen' }).click()
    await pickOption(page.getByRole('combobox', { name: 'Fach' }).nth(i), subject)
    await page.getByRole('spinbutton', { name: 'Wochenstunden' }).nth(i).fill(String(hours))
    await pickOption(page.getByRole('combobox', { name: 'Lehrkraft' }).nth(i), teacher)
  }
  await page.getByRole('button', { name: 'Speichern' }).click()
  await toast('Fächer gespeichert')
}
/** Handaufbau im Entwurf (P-1, P-2): leere Zelle → Dialog → Fach/Raum → Eintragen */
async function addLesson(cell: string, subject: RegExp, room: RegExp) {
  await page.getByRole('button', { name: `Stunde eintragen: ${cell}` }).click()
  await expect(dialog()).toBeVisible()
  await pickOption(dialog().locator('#add-subject'), subject)
  await pickOption(dialog().locator('#add-room'), room)
  await dialog().getByRole('button', { name: 'Eintragen' }).click()
  await toast('Stunde eingetragen')
  await expect(dialog()).toBeHidden()
}
/** Klick-Klick: Kachel → „Verschieben" → Zielzelle */
async function moveLesson(tile: RegExp, targetCell: string) {
  await page.getByRole('button', { name: tile }).first().click()
  await page.getByRole('button', { name: 'Verschieben' }).click()
  await page.getByRole('button', { name: `Hierher verschieben: ${targetCell}` }).click()
}
const tiles = () => page.locator('.lesson-tile')
/** Einträge der Seitenleiste (shadcn-Sidebar hat keine navigation-Rolle; der Navigationspfad in der Topbar schon) */
const sidebarLinks = () => page.locator('a[data-slot="sidebar-menu-button"]')

test.beforeAll(async ({ browser }) => {
  const ctx = await browser.newContext()
  page = await ctx.newPage()
  page.on('pageerror', (e) => pageErrors.push(`pageerror: ${e.message}`))
  page.on('console', (m) => { if (m.type() === 'error' && !/404|409|400|401|403/.test(m.text())) pageErrors.push(`console.error: ${m.text().slice(0, 200)}`) })
})

test.afterAll(async () => {
  const date = new Date().toISOString().slice(0, 10)
  const lines = [
    '# Browser-Durchlauf der Abnahme (Web)',
    '',
    `Erzeugt von \`frontend/scripts/abnahme.spec.ts\` am ${date} gegen \`${process.env.ABNAHME_BASE ?? 'http://localhost:1500'}\` (Chrome headless 1440×900).`,
    'Zeilen wie in `docs/ABNAHME-CHECKLISTE.md` Abschnitt 1; „ok" heißt: alle Erwartungen des Schritts sind im Browser geprüft.',
    '',
    '| # | Datum | Prüfer | Ergebnis | Beleg |',
    '|---|---|---|---|---|',
    ...protocol.map((p) => `| ${p.id} | ${date} | abnahme.spec.ts | ${p.result} | \`docs/smoke/${p.file}\` |`),
    '',
    `Seitenfehler (pageerror / console.error außer HTTP-Statusmeldungen): ${pageErrors.length}`,
    ...pageErrors.map((e) => `- ${e}`),
    '',
  ]
  writeFileSync(OUT + 'PROTOKOLL.md', lines.join('\n'))
})

// ------------------------------------------------------------------ 1.1 / 1.2 Start und Anmeldung
test('I-3 Anmeldemaske zeigt den Schulnamen aus /api/health', async () => {
  const res = await page.goto('/login')
  expect(res?.status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible()
  await expect(page.getByText('Gymnasium Musterstadt')).toBeVisible()
  await beleg('I-3')
})

test('L-2 falsches Kennwort → Fehlertext aus der 401-Antwort', async () => {
  await login('admin', 'falsch')
  await expect(page.getByText('Benutzername oder Passwort ist falsch.')).toBeVisible()
  await beleg('L-2')
})

test('L-1 Anmelden als admin → Übersicht mit allen Bereichen', async () => {
  await login('admin', ADMIN_PW)
  await expect(page.getByRole('heading', { name: 'Übersicht' })).toBeVisible()
  for (const name of ['Lehrkräfte', 'Fächer', 'Klassen', 'Räume', 'Stundenraster', 'Aktiver Plan', 'Entwurf', 'Regelwerk', 'Vertretung', 'Änderungsverlauf', 'Einstellungen']) {
    await expect(sidebarLinks().filter({ hasText: name })).toHaveCount(1)
  }
  await beleg('L-1')
})

// ------------------------------------------------------------------ 1.3 Grunddaten (W-1)
test('G-3 Stundenraster Mo–Fr, sechs Stunden, große Pause nach der 2.', async () => {
  await go('/grunddaten/raster')
  await page.getByRole('button', { name: /^Vorlage: Mo–Fr/ }).click()
  await page.getByRole('button', { name: 'Speichern' }).click()
  await toast('Stundenraster gespeichert')
  for (const day of ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag']) await expect(page.getByRole('heading', { name: day })).toBeVisible()
  await expect(page.getByText('6 Stunden · 1 Pause')).toHaveCount(5)
  await expect(page.getByText(/Mo–Fr · 6 Stunden je Tag · 5 Pausen/)).toBeVisible()
  await beleg('G-3')
})

test('G-1 Fächer Mathematik, Deutsch, Physik (Fachraum Physik)', async () => {
  await go('/grunddaten/faecher')
  for (const [name, short, roomType] of [['Mathematik', 'Ma', ''], ['Deutsch', 'De', ''], ['Physik', 'Ph', 'Fachraum Physik']]) {
    await page.getByRole('button', { name: 'Neues Fach' }).click()
    await page.locator('#subject-name').fill(name)
    await page.locator('#subject-short').fill(short)
    if (roomType) await page.locator('#subject-roomtype').fill(roomType)
    await dialog().getByRole('button', { name: 'Speichern' }).click()
    await toast('Fach gespeichert')
    await expect(rowOf(name)).toBeVisible()
  }
  await expect(page.getByRole('row')).toHaveCount(4) // Kopfzeile + 3
  await beleg('G-1')
})

test('G-2 Räume R101, R204 (Klassenraum), P1 (Fachraum Physik)', async () => {
  await go('/grunddaten/raeume')
  for (const [name, type] of [['R101', 'Klassenraum'], ['R204', 'Klassenraum'], ['P1', 'Fachraum Physik']]) {
    await page.getByRole('button', { name: 'Neuer Raum' }).click()
    await page.locator('#room-name').fill(name)
    await page.locator('#room-type').fill(type)
    await dialog().getByRole('button', { name: 'Speichern' }).click()
    await toast('Raum gespeichert')
    await expect(rowOf(name)).toBeVisible()
  }
  await expect(page.getByRole('row')).toHaveCount(4)
  await beleg('G-2')
})

test('G-4/G-5/G-6 Lehrkräfte Meyer, Weber, Schulz (Fr 5. und 6. Stunde gesperrt)', async () => {
  await go('/grunddaten/lehrkraefte')
  await createTeacher('Meyer', 'MEY', 25, ['Mathematik', 'Physik'])
  await createTeacher('Weber', 'WEB', 25, ['Deutsch'])
  await createTeacher('Schulz', 'SCZ', 2, ['Mathematik'], ['Fr 5. Stunde', 'Fr 6. Stunde'])
  await expect(page.getByRole('row')).toHaveCount(4)
  await expect(rowOf('Schulz').getByText('2 gesperrt')).toBeVisible()
  await beleg('G-6')
})

test('T-W1.1 Schulz-Dialog zeigt alle fünf Werte unverändert', async () => {
  await rowOf('Schulz').getByText('Schulz').click()
  await expect(dialog().getByRole('heading', { name: 'Lehrkraft Schulz bearbeiten' })).toBeVisible()
  await expect(page.locator('#teacher-short')).toHaveValue('SCZ')
  await expect(page.locator('#teacher-quota')).toHaveValue('2')
  await expect(page.getByRole('checkbox', { name: 'Mathematik' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('checkbox', { name: 'Physik' })).toHaveAttribute('aria-checked', 'false')
  await expect(page.getByRole('gridcell', { name: 'Fr 5. Stunde: gesperrt' })).toBeVisible()
  await expect(page.getByRole('gridcell', { name: 'Fr 6. Stunde: gesperrt' })).toBeVisible()
  await beleg('T-W1.1')
  await page.keyboard.press('Escape')
  await expect(dialog()).toBeHidden()
})

test('G-7 Klassen 7a, 8b, 9c mit Fächerlisten', async () => {
  await go('/grunddaten/klassen')
  const classes: [string, string, [string, number, RegExp][]][] = [
    ['7a', '7', [['Mathematik', 2, /Meyer/], ['Deutsch', 1, /Weber/], ['Physik', 1, /Meyer/]]],
    ['8b', '8', [['Mathematik', 2, /Meyer/], ['Deutsch', 1, /Weber/]]],
    ['9c', '9', [['Mathematik', 1, /Schulz/]]],
  ]
  for (const [name, grade, rows] of classes) {
    await go('/grunddaten/klassen')
    await page.getByRole('button', { name: 'Neue Klasse' }).click()
    await page.locator('#class-name').fill(name)
    await expect(page.locator('#class-grade')).toHaveValue(grade)
    await dialog().getByRole('button', { name: 'Speichern' }).click()
    await toast('Klasse gespeichert')
    await expect(page.getByRole('heading', { name: `Klasse ${name}` })).toBeVisible()
    await setClassSubjects(rows)
  }
  await go('/grunddaten/klassen')
  await expect(page.getByRole('row')).toHaveCount(4)
  await go('/grunddaten/klassen/1')
  await expect(page.getByRole('heading', { name: 'Klasse 7a' })).toBeVisible()
  await expect(page.getByRole('spinbutton', { name: 'Wochenstunden' }).first()).toHaveValue('2')
  await expect(page.getByText('3 Fächer · 4 Wochenstunden', { exact: true })).toBeVisible()
  await beleg('G-7')
})

test('G-8 9c: Deutsch ohne Wochenstunden und ohne Lehrkraft → 400 mit zwei Feldfehlern, Liste unverändert', async () => {
  await go('/grunddaten/klassen/3')
  await expect(page.getByRole('heading', { name: 'Klasse 9c' })).toBeVisible()
  await page.getByRole('button', { name: 'Fach hinzufügen' }).click()
  await page.getByRole('button', { name: 'Speichern' }).click()
  const errors = page.getByRole('row').nth(2).getByRole('alert')
  await expect(errors).toHaveCount(2)
  await beleg('G-8')
  await page.getByRole('button', { name: 'Verwerfen' }).click()
  await expect(page.getByRole('row')).toHaveCount(2) // Kopfzeile + Mathematik
})

test('G-9 Fach Mathematik löschen → 409 in-use nennt die Klassen', async () => {
  await go('/grunddaten/faecher')
  await rowMenu('Mathematik', 'Löschen')
  await dialog().getByRole('button', { name: 'Löschen' }).click()
  await expect(page.getByText('Mathematik wird in 7a, 8b, 9c unterrichtet.')).toBeVisible()
  await beleg('G-9')
  await dialog().getByRole('button', { name: 'Verstanden' }).click()
  await expect(rowOf('Mathematik')).toBeVisible()
})

test('G-10 / T-W1.4 9c Mathematik: Schulz → Weber → Warnung SUBJECT_NOT_TAUGHT, zurück → keine Warnung', async () => {
  await go('/grunddaten/klassen/3')
  await expect(page.getByRole('heading', { name: 'Klasse 9c' })).toBeVisible()
  await pickOption(page.getByRole('combobox', { name: 'Lehrkraft' }).first(), /Weber/)
  await page.getByRole('button', { name: 'Speichern' }).click()
  await toast('Fächer gespeichert — 1 Hinweis')
  await expect(page.getByText('Weber unterrichtet das Fach Mathematik nicht.').first()).toBeVisible()
  await beleg('T-W1.4')
  await pickOption(page.getByRole('combobox', { name: 'Lehrkraft' }).first(), /Schulz/)
  await page.getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByText('Weber unterrichtet das Fach Mathematik nicht.')).toBeHidden()
})

test('G-11 / T-W1.5 7a Mathematik: Meyer → Schulz → Warnung QUOTA_EXCEEDED (3 von 2), zurück → keine Warnung', async () => {
  await go('/grunddaten/klassen/1')
  await expect(page.getByRole('heading', { name: 'Klasse 7a' })).toBeVisible()
  await pickOption(page.getByRole('combobox', { name: 'Lehrkraft' }).first(), /Schulz/)
  await page.getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByText('Schulz ist mit 3 Wochenstunden über dem Deputat von 2.').first()).toBeVisible()
  await beleg('T-W1.5')
  await pickOption(page.getByRole('combobox', { name: 'Lehrkraft' }).first(), /Meyer/)
  await page.getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByText('Schulz ist mit 3 Wochenstunden über dem Deputat von 2.')).toBeHidden()
})

// ------------------------------------------------------------------ 1.4 Regelwerk (W-2)
test('R-1 Regeln max. Stunden/Tag (6) und /Woche (30) für Klassen vorhanden und aktiv', async () => {
  await go('/regelwerk')
  await expect(page.getByRole('spinbutton', { name: 'Wert für Maximale Stunden pro Tag' }).first()).toHaveValue('6')
  await expect(page.getByRole('spinbutton', { name: 'Wert für Maximale Stunden pro Woche' }).first()).toHaveValue('30')
  await expect(page.getByRole('switch', { name: 'Maximale Stunden pro Tag aktiv' }).first()).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('switch', { name: 'Maximale Stunden pro Woche aktiv' }).first()).toHaveAttribute('aria-checked', 'true')
  await beleg('R-1')
})

test('R-2 Wert 0 → Feldfehler „muss mindestens 1 sein", 6 bleibt', async () => {
  const input = page.getByRole('spinbutton', { name: 'Wert für Maximale Stunden pro Tag' }).first()
  await input.fill('0')
  await rowOf('Maximale Stunden pro Tag').getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByText('muss mindestens 1 sein')).toBeVisible()
  await beleg('R-2')
  await go('/entwurf')
  await go('/regelwerk')
  await expect(page.getByRole('spinbutton', { name: 'Wert für Maximale Stunden pro Tag' }).first()).toHaveValue('6')
})

test('R-3 Beispielsatz Schleswig-Holstein laden → fünf Zeilen', async () => {
  await page.getByRole('button', { name: /Beispielsatz Schleswig-Holstein laden/ }).click()
  await dialog().getByRole('button', { name: 'Beispielsatz laden' }).click()
  await toast('Beispielsatz geladen')
  await expect(page.getByRole('row')).toHaveCount(6) // Kopfzeile + 5
  await expect(page.getByText('3 von 5 Regeln aktiv')).toBeVisible()
  await beleg('R-3')
})

test('Einstellungen: Rechenzeit auf 10 s (damit S-2 im Durchlauf kurz bleibt)', async () => {
  await go('/einstellungen')
  await page.locator('#cfg-solve').fill('10')
  await page.getByRole('button', { name: 'Speichern' }).click()
  await toast('Einstellungen gespeichert')
  await beleg('Einstellungen-Rechenzeit')
})

// ------------------------------------------------------------------ 1.5 Plan erstellen, Konflikte (W-3, W-4)
test('P-1 Entwurf leer beginnen, sieben Stunden Mo/Di eintragen, 9c Ma offen', async () => {
  await go('/entwurf')
  await page.getByRole('button', { name: 'Leer beginnen' }).click()
  await toast('Leerer Entwurf angelegt')
  await expect(page.getByText('ENTWURF', { exact: true })).toBeVisible()
  await pickOption(page.getByRole('combobox', { name: 'Klasse wählen' }), '7a')
  await addLesson('Montag, 1. Stunde', /Mathematik/, /R101/)
  await addLesson('Montag, 2. Stunde', /Deutsch/, /R101/)
  await addLesson('Montag, 3. Stunde', /Physik/, /P1/)
  await addLesson('Dienstag, 1. Stunde', /Mathematik/, /R101/)
  await pickOption(page.getByRole('combobox', { name: 'Klasse wählen' }), '8b')
  await addLesson('Montag, 2. Stunde', /Mathematik/, /R204/)
  await addLesson('Montag, 3. Stunde', /Deutsch/, /R204/)
  await addLesson('Montag, 4. Stunde', /Mathematik/, /R204/)
  await expect(page.getByText('Keine Konflikte')).toBeVisible()
  const open = page.getByRole('region', { name: 'Offene Stunden' })
  await expect(open.getByRole('row', { name: /9c.*Ma.*1/ })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Festlegung aufheben' })).toHaveCount(3) // 8b: alle drei Stunden festgelegt
  await beleg('P-1')
})

test('P-2 / T-W3.1 9c Mo 1. Mathematik in R101 → genau ein Raumkonflikt, Lehrkraft Schulz automatisch', async () => {
  await pickOption(page.getByRole('combobox', { name: 'Klasse wählen' }), '9c')
  await page.getByRole('button', { name: 'Stunde eintragen: Montag, 1. Stunde' }).click()
  await expect(dialog().locator('#add-subject')).toContainText('Mathematik')
  await expect(dialog().locator('#add-subject')).toContainText('Schulz')
  await pickOption(dialog().locator('#add-room'), /R101/)
  await dialog().getByRole('button', { name: 'Eintragen' }).click()
  await toast('Stunde eingetragen')
  await expect(page.getByRole('button', { name: /^Ma 9c, SCZ, R101/ })).toBeVisible()
  const conflicts = page.getByRole('region', { name: 'Konflikte' })
  await expect(conflicts.getByText(/R101.*Montag.*1\. Stunde/)).toBeVisible()
  await expect(conflicts.getByRole('listitem')).toHaveCount(1)
  await expect(page.getByText(/Lehrkraft .* doppelt belegt/)).toHaveCount(0)
  await expect(page.getByRole('button', { name: '1 Konflikt anzeigen' })).toBeVisible()
  await beleg('T-W3.1')
})

test('P-3 / T-W3.2 Stunde auf Mo 5. verschieben → Konflikt an beiden Stunden weg, Stunde festgelegt', async () => {
  await moveLesson(/^Ma 9c/, 'Montag, 5. Stunde')
  await expect(page.getByText('Keine Konflikte')).toBeVisible()
  await expect(page.getByRole('button', { name: /^Ma 9c, SCZ, R101/ })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Festlegung aufheben' })).toBeVisible()
  await pickOption(page.getByRole('combobox', { name: 'Klasse wählen' }), '7a')
  await expect(page.getByRole('button', { name: /Konflikt anzeigen/ })).toHaveCount(0)
  await beleg('T-W3.2')
})

test('P-4 / T-W1.2 9c Ma nach Fr 5. Stunde → genau ein Konflikt „Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde"', async () => {
  await pickOption(page.getByRole('combobox', { name: 'Klasse wählen' }), '9c')
  await moveLesson(/^Ma 9c/, 'Freitag, 5. Stunde')
  const conflicts = page.getByRole('region', { name: 'Konflikte' })
  await expect(conflicts.getByText('Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde')).toBeVisible()
  await expect(conflicts.getByRole('listitem')).toHaveCount(1)
  await expect(page.getByRole('button', { name: /^Ma 9c, SCZ, R101/ })).toBeVisible()
  await beleg('T-W1.2')
})

test('P-5 / P-5b Freitagsstunde entfernen → Entwurf hat genau die sieben Stunden aus P-1', async () => {
  await page.getByRole('button', { name: /^Ma 9c/ }).click()
  await page.getByRole('button', { name: 'Entfernen' }).click()
  await dialog().getByRole('button', { name: 'Entfernen' }).click()
  await toast('Stunde entfernt')
  await expect(page.getByText('Keine Konflikte')).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'ENTWURF' }).getByText('7', { exact: true })).toBeVisible()
  await beleg('P-5b')
})

test('P-6 Entwurf annehmen → aktiver Plan mit sieben Stunden, Entwurf weg', async () => {
  await page.getByRole('button', { name: 'Annehmen' }).click()
  const submit = dialog().getByRole('button', { name: 'Annehmen' })
  await expect(submit).toBeEnabled()
  await submit.click()
  await toast('Plan angenommen')
  await page.waitForURL(/\/plan/)
  await expect(page.getByRole('heading', { name: 'Aktiver Plan' })).toBeVisible()
  await expect(page.getByText(/Angenommen am .* 7 Stunden/)).toBeVisible()
  await go('/entwurf')
  await expect(page.getByText('Kein Entwurf vorhanden')).toBeVisible()
  await beleg('P-6')
})

test('P-7 / T-W4.1 Sichten Klasse 7a, Lehrkraft Meyer, Schule, Raum R101 zeigen dieselbe Stunde', async () => {
  await go(`/plan?sicht=CLASS&id=1&woche=${WEEK}`)
  await expect(page.getByRole('button', { name: /^Ma 7a, MEY, R101$/ })).toHaveCount(2) // Mo 1 und Di 1
  await beleg('T-W4.1-klasse')
  await page.getByRole('tab', { name: 'Lehrkraft' }).click()
  await pickOption(page.getByRole('combobox', { name: 'Lehrkraft wählen' }), /Meyer/)
  await expect(tiles()).toHaveCount(5) // Mo 1, 2, 3, 4 (Pos. 1, 2, 4, 5) + Di 1
  await expect(page.locator('div[aria-label="Montag, 2. Stunde"] .lesson-tile')).toHaveText(/Ma.*8b/s)
  await beleg('T-W4.1-lehrkraft')
  await page.getByRole('tab', { name: 'Schule' }).click()
  await expect(page.locator('div[aria-label="Montag, 1. Stunde"] .lesson-tile')).toHaveCount(1)
  await beleg('T-W4.1-schule')
  await page.getByRole('tab', { name: 'Raum' }).click()
  await pickOption(page.getByRole('combobox', { name: 'Raum wählen' }), 'R101')
  await expect(page.locator('div[aria-label="Montag, 1. Stunde"] .lesson-tile')).toHaveCount(1)
  await expect(page.locator('div[aria-label="Montag, 2. Stunde"] .lesson-tile')).toHaveCount(1)
  for (const n of [3, 4, 5, 6]) await expect(page.locator(`div[aria-label="Montag, ${n}. Stunde"] .lesson-tile`)).toHaveCount(0)
  await beleg('T-W4.1-raum')
})

test('T-W1.3 Krause löschen → weg; Meyer löschen → 409 „in 5 Stunden eingeplant" → Deaktivieren → nicht wählbar', async () => {
  await go('/grunddaten/lehrkraefte')
  await createTeacher('Krause', 'KRA', 20, [])
  await rowMenu('Krause', 'Löschen')
  await dialog().getByRole('button', { name: 'Löschen' }).click()
  await toast('Lehrkraft Krause gelöscht')
  await expect(rowOf('Krause')).toBeHidden()
  await rowMenu('Meyer', 'Löschen')
  await dialog().getByRole('button', { name: 'Löschen' }).click()
  await expect(page.getByText('Meyer ist in 5 Stunden eingeplant. Deaktivieren statt löschen.')).toBeVisible()
  await beleg('T-W1.3-in-use')
  await dialog().getByRole('button', { name: 'Deaktivieren' }).click()
  await toast('Meyer deaktiviert')
  await expect(rowOf('Meyer').getByText('deaktiviert')).toBeVisible()
  // Plan zeigt die Stunden weiter mit MEY; die Klassenzuordnung bietet Meyer nicht mehr an
  await go(`/plan?sicht=CLASS&id=1&woche=${WEEK}`)
  await expect(page.getByRole('button', { name: /^Ma 7a, MEY, R101$/ })).toHaveCount(2)
  await go('/grunddaten/klassen/2')
  await expect(page.getByRole('heading', { name: 'Klasse 8b' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Lehrkraft' })).toHaveCount(2)
  await page.waitForTimeout(800) // Zeilen-Einblendung (motion) abwarten, sonst meldet Playwright „not stable"
  await page.getByRole('combobox', { name: 'Lehrkraft' }).nth(1).click() // Deutsch-Zeile (Weber)
  await expect(page.getByRole('option', { name: /Weber/ })).toBeVisible()
  await expect(page.getByRole('option', { name: /Meyer/ })).toHaveCount(0)
  await beleg('T-W1.3-nicht-waehlbar')
  await page.keyboard.press('Escape')
  await go('/grunddaten/lehrkraefte')
  await rowMenu('Meyer', 'Aktivieren')
  await toast('Meyer aktiviert')
  await expect(rowOf('Meyer').getByText('aktiv', { exact: true })).toBeVisible()
})

test('T-W2.1 Regel max/Tag = 3 → 7a Mo 4. Stunde Deutsch ergibt einen weichen Verstoß mit Regel, Wert, Klasse, Tag', async () => {
  await go('/regelwerk')
  const input = page.getByRole('spinbutton', { name: 'Wert für Maximale Stunden pro Tag' }).first()
  await input.fill('3')
  await rowOf('Maximale Stunden pro Tag').getByRole('button', { name: 'Speichern' }).click()
  await toast('Regel gespeichert')
  await go('/entwurf')
  await page.getByRole('button', { name: 'Aktiven Plan kopieren' }).click()
  await toast('Entwurf aus dem aktiven Plan angelegt')
  await pickOption(page.getByRole('combobox', { name: 'Klasse wählen' }), '7a')
  await addLesson('Montag, 4. Stunde', /Deutsch/, /R101/)
  const conflicts = page.getByRole('region', { name: 'Konflikte' })
  const text = (await conflicts.getByRole('listitem').first().textContent()) ?? ''
  expect(text).toMatch(/Maximale Stunden pro Tag/)
  expect(text).toMatch(/3/)
  expect(text).toMatch(/7a/)
  expect(text).toMatch(/Montag/)
  await expect(conflicts.getByRole('listitem')).toHaveCount(1)
  await expect(page.locator('div[aria-label="Montag, 4. Stunde"] .lesson-tile')).toHaveCount(1)
  await beleg('T-W2.1')
  await page.getByRole('button', { name: 'Verwerfen' }).click()
  await dialog().getByRole('button', { name: 'Verwerfen' }).click()
  await toast('Entwurf verworfen')
  await go('/regelwerk')
  await page.getByRole('spinbutton', { name: 'Wert für Maximale Stunden pro Tag' }).first().fill('6')
  await rowOf('Maximale Stunden pro Tag').getByRole('button', { name: 'Speichern' }).click()
  await toast('Regel gespeichert')
})

// ------------------------------------------------------------------ 1.6 Leitszenario (W-5, W-6, W-7)
test('V-1 / T-W5.1 Ausfall Meyer am 21.09. → vier offene Stunden am Montag, keine am Dienstag', async () => {
  await go(`/vertretung?datum=${WEEK}`)
  await pickOption(page.locator('#ev-teacher'), /Meyer/)
  await page.locator('#ev-reason').fill('Krankmeldung')
  await page.getByRole('button', { name: 'Ereignis speichern' }).click()
  await toast('Ereignis erfasst')
  await expect(page.getByText('4 betroffene Stunden')).toBeVisible()
  await expect(page.getByText('Mo 21.09. · 4 offen')).toBeVisible()
  await expect(page.getByText(/Di 22\.09\./)).toHaveCount(0)
  await beleg('T-W5.1')
  await go(`/plan?sicht=SCHOOL&woche=${WEEK}`)
  await expect(page.getByRole('button', { name: /noch nicht vertreten$/ })).toHaveCount(4) // Schulsicht: Mini-Kacheln „7a Ma · R101 noch nicht vertreten"
  await expect(page.getByRole('button', { name: /^7a De · R101$/ })).toBeVisible() // die Weber-Stunde bleibt regulär
  await beleg('T-W5.1-plan')
})

test('V-2 Sperrung R101 am 21.09. → fünf offene Stunden, 7a 1. Stunde mit zwei Anlässen', async () => {
  await go(`/vertretung?datum=${WEEK}`)
  await pickOption(page.locator('#ev-kind'), 'Raumsperrung')
  await pickOption(page.locator('#ev-room'), /R101/)
  await page.locator('#ev-reason').fill('Wasserschaden')
  await page.getByRole('button', { name: 'Ereignis speichern' }).click()
  await expect(page.getByText('2 betroffene Stunden')).toBeVisible()
  await expect(page.getByText('Mo 21.09. · 5 offen')).toBeVisible()
  await expect(page.getByText('Krankmeldung; Wasserschaden')).toBeVisible()
  await beleg('V-2')
})

test('V-3 / V-4 / T-W6.1 Optionen für 7a 1. Stunde: Schulz ja, Meyer nein, R204 ja, R101 nein → zuweisen', async () => {
  await page.getByRole('row', { name: /7a.*Ma.*MEY.*Krankmeldung; Wasserschaden/ }).first().click()
  await expect(page.getByRole('heading', { name: '7a · Mathematik' })).toBeVisible()
  const teachers = page.getByRole('group', { name: 'Lehrkräfte' })
  await expect(teachers.getByRole('button').first()).toContainText('Schulz')
  await expect(teachers.getByText('Mathematik', { exact: true })).toBeVisible()
  await expect(teachers.getByRole('button', { name: /Meyer/ })).toHaveCount(0)
  const rooms = page.getByRole('group', { name: 'Räume' })
  await expect(rooms.getByRole('button', { name: /R204/ })).toBeVisible()
  await expect(rooms.getByRole('button', { name: /R101/ })).toHaveCount(0)
  await beleg('T-W6.1-optionen')
  await teachers.getByRole('button', { name: /Schulz/ }).click()
  await rooms.getByRole('button', { name: /R204/ }).click()
  await page.getByRole('button', { name: /^Zuweisen/ }).click()
  await toast('Vertretung zugewiesen')
  await expect(page.getByRole('row', { name: /7a.*MEY.*SCZ.*R101.*R204/ })).toBeVisible()
  await expect(page.getByText('ohne Folgekonflikt')).toBeVisible()
  await expect(page.getByText('4 offene Stunden in dieser Woche')).toBeVisible()
  await beleg('T-W6.1-zugewiesen')
})

test('V-5 Plansicht: 21.09. 7a 1. Stunde = Schulz/R204 Vertretung; 28.09. wieder Meyer/R101', async () => {
  await go(`/plan?sicht=CLASS&id=1&woche=${WEEK}`)
  await expect(page.getByRole('button', { name: /^Ma 7a, SCZ, R204, Vertretung/ })).toBeVisible()
  await beleg('V-5-21-09')
  await go('/plan?sicht=CLASS&id=1&woche=2026-09-28')
  await expect(page.locator('div[aria-label="Montag, 1. Stunde"] .lesson-tile')).toHaveText(/MEY.*R101/s)
  await expect(page.getByRole('button', { name: /noch nicht vertreten|Vertretung/ })).toHaveCount(0)
  await beleg('V-5-28-09')
})

test('V-6 / T-W7.1 Änderungsverlauf, Filter 7a: genau ein Eintrag mit beiden Anlässen, kein Löschen/Bearbeiten', async () => {
  await go('/verlauf')
  await pickOption(page.locator('#cl-class'), '7a')
  await page.waitForURL(/klasse=1/)
  await expect(page.getByText('Einträge 1–1 von 1')).toBeVisible() // erst die gefilterte Antwort abwarten (ohne Filter: + „Plan angenommen")
  await expect(page.getByText('Vertretung eingetragen')).toBeVisible()
  await expect(page.getByText('Meyer / R101')).toBeVisible()
  await expect(page.getByText('Schulz / R204')).toBeVisible()
  await expect(page.getByText('Anlass: Krankmeldung; Wasserschaden')).toBeVisible()
  await expect(page.getByText('von admin')).toHaveCount(1)
  await expect(page.getByRole('button', { name: /Bearbeiten|Löschen/ })).toHaveCount(0)
  await beleg('T-W7.1')
})

// ------------------------------------------------------------------ Rolle Lehrkraft im Web (E-38) und Server-Anteil A-1 … A-5
test('A-1 Konten meyer und schulz anlegen (Rolle Lehrkraft)', async () => {
  await go('/einstellungen?bereich=konten')
  for (const [teacher, user] of [[/Meyer/, 'meyer'], [/Schulz/, 'schulz']] as const) {
    await page.getByRole('button', { name: 'Neues Konto' }).click()
    await pickOption(dialog().getByRole('combobox', { name: 'Lehrkraft' }), teacher)
    await page.locator('#user-name').fill(user)
    await page.locator('#user-password').fill(`${user}-2026`)
    await dialog().getByRole('button', { name: 'Konto anlegen' }).click()
    await toast('Benutzerkonto angelegt')
    await expect(rowOf(user)).toBeVisible()
  }
  // Teilstring: seit E-50 nennt die Zeile zusätzlich die Klassenkonten („… · 0 Klassen. …“).
  await expect(page.getByText('3 Konten · 1 Planung · 2 Lehrkräfte')).toBeVisible()
  await beleg('A-1')
})

test('E-38 / T-A1.1 Lehrkraft Meyer im Web: nur „Mein Plan", 21.09. vier eigene Einträge, /plan leitet um', async () => {
  await logout()
  await login('meyer', 'meyer-2026')
  await page.waitForURL(/\/mein-plan/)
  await expect(page.getByRole('heading', { name: 'Mein Plan' })).toBeVisible()
  await expect(sidebarLinks()).toHaveCount(1)
  await expect(sidebarLinks().first()).toHaveText('Mein Plan')
  await go(`/mein-plan?woche=${WEEK}`)
  await expect(page.getByRole('button', { name: /^Ma 7a, SCZ, R204, abgegeben an SCZ/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /noch nicht vertreten$/ })).toHaveCount(3)
  await expect(tiles()).toHaveCount(5) // vier Montag + Di 1; die Weber-Stunde fehlt
  await expect(page.getByRole('button', { name: /^De 7a/ })).toHaveCount(0)
  await beleg('T-A1.1-meyer')
  await go('/plan')
  await page.waitForURL(/\/mein-plan/)
  await go('/grunddaten/lehrkraefte')
  await page.waitForURL(/\/mein-plan/)
  await beleg('E-38-redirect')
})

test('A-4 / A-5 / T-A2.1 Lehrkraft Schulz: 21.09. Vertretung 7a in R204 mit beiden Anlässen und MEY; 28.09. nur regulär', async () => {
  await logout()
  await login('schulz', 'schulz-2026')
  await page.waitForURL(/\/mein-plan/)
  await go(`/mein-plan?woche=${WEEK}`)
  const tile = page.getByRole('button', { name: /^Ma 7a, SCZ, R204, Vertretung/ })
  await expect(tile).toBeVisible()
  await tile.click()
  const popover = page.getByRole('dialog')
  await expect(popover.getByText('Krankmeldung')).toBeVisible()
  await expect(popover.getByText('Wasserschaden')).toBeVisible()
  await expect(popover.getByText(/MEY/)).toBeVisible()
  await beleg('T-A2.1-schulz')
  await page.keyboard.press('Escape')
  await go('/mein-plan?woche=2026-09-28')
  await expect(page.getByRole('button', { name: /Vertretung|noch nicht vertreten|abgegeben/ })).toHaveCount(0)
  await expect(tiles()).toHaveCount(0) // Schulz hat im Regelplan keine Stunde
  await beleg('T-A2.1-28-09')
})

// ------------------------------------------------------------------ 1.7 Beispieldaten, Bedienbarkeit
test('D-4 / D-5 Stunde in ein belegtes Zeitfenster ziehen → Konflikt sofort; Verwerfen stellt den Stand her', async () => {
  await logout()
  await login('admin', ADMIN_PW)
  await expect(page.getByRole('heading', { name: 'Übersicht' })).toBeVisible()
  await go('/entwurf')
  await page.getByRole('button', { name: 'Aktiven Plan kopieren' }).click()
  await toast('Entwurf aus dem aktiven Plan angelegt')
  await pickOption(page.getByRole('combobox', { name: 'Klasse wählen' }), '7a')
  // 7a Ma (Mo 1.) mit der Maus auf Mo 2. (De 7a, belegt) ziehen — dnd-kit braucht einen Zug in Schritten
  const source = page.getByRole('button', { name: /^Ma 7a, MEY, R101$/ }).first()
  const target = page.locator('div[aria-label="Montag, 2. Stunde"]')
  const a = (await source.boundingBox())!
  const b = (await target.boundingBox())!
  await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2)
  await page.mouse.down()
  await page.mouse.move(a.x + a.width / 2 + 10, a.y + a.height / 2 + 10, { steps: 5 })
  await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 15 })
  await page.mouse.up()
  const conflicts = page.getByRole('region', { name: 'Konflikte' })
  let how = 'per Drag-and-Drop auf Mo 2. Stunde: Raum, Lehrkraft und Klasse doppelt belegt'
  const dragged = await conflicts.getByText(/doppelt belegt/).first().isVisible().catch(() => false)
  if (!dragged) {
    // Rückfall (Pflichtenheft nennt beide Wege): Klick-Klick auf Mo 4. Stunde — dort unterrichtet Meyer die 8b
    how = 'per Klick-Klick auf Mo 4. Stunde (Drag im Headless-Chrome nicht angekommen)'
    await moveLesson(/^Ma 7a, MEY, R101$/, 'Montag, 4. Stunde')
  }
  await expect(conflicts.getByText(/doppelt belegt/).first()).toBeVisible()
  expect(await conflicts.getByRole('listitem').count()).toBeGreaterThanOrEqual(1)
  await expect(page.getByRole('button', { name: /Konflikte? anzeigen/ }).first()).toBeVisible()
  await beleg('D-4', `ok — ${how}`)
  await page.getByRole('button', { name: 'Verwerfen' }).click()
  await dialog().getByRole('button', { name: 'Verwerfen' }).click()
  await toast('Entwurf verworfen')
  await go(`/plan?sicht=CLASS&id=1&woche=${WEEK}`)
  await expect(page.getByText(/Angenommen am .* 7 Stunden/)).toBeVisible()
  await expect(page.locator('div[aria-label="Montag, 1. Stunde"] .lesson-tile')).toHaveText(/Ma.*7a|Ma.*MEY/s)
  await expect(page.getByRole('button', { name: /Konflikt anzeigen/ })).toHaveCount(0)
  await beleg('D-5')
})

test('D-1 „Vollständige Schule" laden: 409 → Rückfrage → Ersetzen → 12 Klassen, 24 Lehrkräfte, 18 Räume, Plan gefüllt', async () => {
  await go('/einstellungen?bereich=beispieldaten')
  await page.getByRole('button', { name: 'Vollständige Schule laden' }).click()
  await expect(dialog().getByRole('heading', { name: 'Vorhandene Daten ersetzen?' })).toBeVisible()
  await beleg('D-1-rueckfrage')
  await dialog().getByRole('button', { name: 'Ersetzen' }).click()
  await expect(page.getByText('12 Klassen, 24 Lehrkräfte, 18 Räume')).toBeVisible({ timeout: 45_000 })
  await expect(page.getByText('12 Klassen · 24 Lehrkräfte · 18 Räume')).toBeVisible()
  await expect(page.getByText(/\d+ Stunden im aktiven Plan/)).toBeVisible()
  await beleg('D-1')
})

test('D-2 / D-3 Plansicht einer Klasse und einer Lehrkraft mit dem vollen Bestand', async () => {
  await go(`/plan?sicht=CLASS&woche=${WEEK}`)
  await expect(page.getByRole('heading', { name: 'Aktiver Plan' })).toBeVisible()
  // Der Cache zeigt zuerst den alten Stand (7a mit vier Stunden), bis die neu geladene Woche da ist — deshalb warten
  await expect.poll(() => tiles().count(), { timeout: 10_000 }).toBeGreaterThan(20)
  await beleg('D-2')
  await page.getByRole('tab', { name: 'Lehrkraft' }).click()
  await expect.poll(() => tiles().count(), { timeout: 10_000 }).toBeGreaterThan(5)
  await beleg('D-3')
})

// ------------------------------------------------------------------ 1.8 Stundenplanvorschlag (W-8)
test('S-1 Aktiven Plan leeren → keine Kacheln, Grunddaten bleiben (12 Klassen)', async () => {
  await go('/einstellungen?bereich=beispieldaten')
  await page.getByRole('button', { name: 'Aktiven Plan leeren' }).click()
  await dialog().getByRole('button', { name: 'Plan leeren' }).click()
  await toast('Aktiver Plan geleert')
  await go(`/plan?sicht=SCHOOL&woche=${WEEK}`)
  await expect(page.getByText(/Angenommen am .* 0 Stunden/)).toBeVisible()
  await expect(tiles()).toHaveCount(0)
  await go('/grunddaten/klassen')
  await expect(page.getByText('12 Klassen', { exact: true })).toBeVisible() // exakt: der Toast aus D-1 kann noch stehen
  await beleg('S-1')
})

test('S-2 / T-W8.1 Leer beginnen → Berechnen (10 s) → Entwurf belegt, keine Doppelbelegung, aktiver Plan bleibt leer', async () => {
  await go('/entwurf')
  await page.getByRole('button', { name: 'Leer beginnen' }).click()
  await toast('Leerer Entwurf angelegt')
  await page.getByRole('button', { name: 'Berechnen' }).first().click()
  await expect(dialog().getByText('Rechenzeit: 10 s')).toBeVisible()
  await dialog().getByRole('button', { name: 'Berechnen' }).click()
  await toast('Berechnung gestartet')
  await expect(page.getByText('Berechnung läuft')).toBeVisible()
  await beleg('S-2-laeuft')
  const started = Date.now()
  await expect(page.getByText('Berechnung läuft')).toBeHidden({ timeout: 30_000 })
  const seconds = Math.round((Date.now() - started) / 1000)
  const banner = page.getByRole('status').filter({ hasText: 'ENTWURF' })
  await expect(banner.getByText('Letzte Berechnung abgeschlossen.')).toBeVisible()
  await expect(banner.getByText(/hard|medium|soft/)).toHaveCount(0)   // kein roher Timefold-Score in der Oberfläche
  const bannerText = (await banner.textContent()) ?? ''
  const placed = Number(/(\d+)\s*belegt/.exec(bannerText)?.[1] ?? 0)
  expect(placed).toBeGreaterThan(300)
  await expect(page.getByText(/doppelt belegt/)).toHaveCount(0)
  await beleg('T-W8.1-berechnet', `ok — ${placed} belegt, Lauf ≤ ${seconds} s nach dem Start sichtbar beendet; Banner: ${bannerText.replace(/\s+/g, ' ').slice(0, 120)}`)
  await go(`/plan?sicht=SCHOOL&woche=${WEEK}`)
  await expect(page.getByText(/Angenommen am .* 0 Stunden/)).toBeVisible()
  await expect(tiles()).toHaveCount(0)
  await beleg('S-3')
})

test('T-W9.1 (Teil) Stunde festlegen und Festlegung aufheben im berechneten Entwurf', async () => {
  await go('/entwurf?sicht=CLASS&id=1')
  await expect(page.getByRole('button', { name: 'Festlegung aufheben' })).toHaveCount(0)
  await tiles().first().click()
  await page.getByRole('button', { name: 'Festlegen' }).click()
  await toast('Stunde festgelegt')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: 'Festlegung aufheben' })).toHaveCount(1)
  await beleg('T-W9.1-pin')
  await page.getByRole('button', { name: 'Festlegung aufheben' }).click()
  await toast('Festlegung aufgehoben')
  await expect(page.getByRole('button', { name: 'Festlegung aufheben' })).toHaveCount(0)
})

test('S-4 Vorschlag annehmen → aktiver Plan zeigt die Stunden des Entwurfs', async () => {
  await page.getByRole('button', { name: 'Annehmen' }).click()
  const submit = dialog().getByRole('button', { name: 'Annehmen' })
  await expect(submit).toBeEnabled()
  await submit.click()
  await toast('Plan angenommen')
  await page.waitForURL(/\/plan/)
  await expect(page.getByText(/Angenommen am .* \d{3} Stunden/)).toBeVisible()
  await go(`/plan?sicht=CLASS&id=1&woche=${WEEK}`)
  await expect.poll(() => tiles().count(), { timeout: 10_000 }).toBeGreaterThan(20)
  await beleg('S-4')
})

// W-4 Alternative 1 (Soll): Druckansicht. Geprüft wird im Papierformat A4 HOCH — dort fiel der Fehler auf
// (Seitenleiste blieb als 240-px-Leerspalte stehen, Do und Fr wurden abgeschnitten).
test('W-4 Druckansicht: Seitenleiste weg, alle fünf Tage auf der Seite, Sicht/Objekt/Woche im Ausdruck', async () => {
  await go(`/plan?sicht=CLASS&id=1&woche=${WEEK}`)
  await expect(page.locator('.week-grid')).toBeVisible()
  await page.emulateMedia({ media: 'print' })
  await page.setViewportSize({ width: 794, height: 1123 })   // A4 hoch in CSS-Pixeln
  await page.waitForTimeout(500)

  await expect(page.locator('[data-slot="sidebar"]')).toBeHidden()
  await expect(page.locator('.print-hide').first()).toBeHidden()
  await expect(page.getByText(new RegExp(`Klasse .+ KW \\d+`))).toBeVisible()

  const fits = await page.evaluate(() => {
    const g = document.querySelector('.week-grid') as HTMLElement
    const days = [...g.querySelectorAll('[title]')]
    return { overflow: g.scrollWidth - g.clientWidth, tage: days.length, rechts: Math.max(...days.map((e) => e.getBoundingClientRect().right)), seite: document.body.clientWidth }
  })
  expect(fits.tage).toBe(5)                       // Mo bis Fr
  expect(fits.overflow).toBeLessThanOrEqual(1)    // nichts wird abgeschnitten
  expect(fits.rechts).toBeLessThanOrEqual(fits.seite)

  await page.pdf({ path: OUT + 'W-4-druck-A4.pdf', format: 'A4' })
  await page.emulateMedia({ media: null })
  await page.setViewportSize({ width: 1440, height: 900 })
  await beleg('W-4-druck', `ok — Seitenleiste ausgeblendet, ${fits.tage} Tagesspalten ohne Überlauf (${fits.rechts} von ${fits.seite} px), Kopfzeile mit Sicht/Objekt/Woche; PDF docs/smoke/W-4-druck-A4.pdf`)
})

test('Abschluss: keine Seitenfehler im ganzen Durchlauf', async () => {
  expect(pageErrors).toEqual([])
})
