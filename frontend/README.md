# Stundenplanung — Web-App (`frontend/`)

React 19 + Vite 8 + Tailwind 4 + shadcn/ui + TanStack Query + react-router 7, TypeScript. Läuft gegen das
Spring-Boot-Backend (`/api`), gegen den Prism-Mock aus `../api-contract/openapi.yaml` **oder** gegen die eingebaute
Attrappe (`VITE_MOCK=true`). Verbindlicher Vertrag: `../api-contract/openapi.yaml`; Einstieg ins Ganze: `../STARTSTAND.md`;
Belege und Abweichungen aus dem Bau der Attrappe: `AGENT-NOTIZEN.md`.

Stand 05.09.2026: alle 20 Routen sind gebaut, gegen die Attrappe durchgeklickt (W-1..W-9, Lehrkraft im Web,
A-1..A-3 in der App-Vorschau) **und gegen das echte Backend geprüft** (Karte T-502): der Browser-Durchlauf
`scripts/abnahme.spec.ts` spielt die Web-Zeilen der `../docs/ABNAHME-CHECKLISTE.md` gegen den Compose-Stack nach,
Belege in `../docs/smoke/` (Screenshots + `PROTOKOLL.md`).

## Start

```bash
npm ci                      # einmal (node 20.19+ oder 22.12+; gemessen mit node 20.20.1, npm 10.8.2)
npm run dev:mock            # Attrappe im Browser, http://localhost:5173 — kein Backend nötig
npm run dev                 # echter Client gegen /api → Vite-Proxy → http://localhost:8080 (Backend muss laufen)
npm run dev:prism           # echter Client gegen /api → Vite-Proxy → http://localhost:1501 (Prism aus ../api-contract)
```

Demo-Konten (nur Attrappe, Passwort = Benutzername): `admin` (Planung), `meyer`, `schulz`, `weber` (Lehrkraft).
Neu laden hält die Sitzung (Token im `localStorage`, seit F-5a; Abmelden über das Benutzermenü) — wie im echten System. Jede Route akzeptiert im Mock `?demo=admin`
(stille Anmeldung) und `?theme=light|dark`.

| Befehl | Zweck |
|---|---|
| `npm run typecheck` | `tsc -p tsconfig.app.json --noEmit` — muss vor jedem „fertig" grün sein |
| `npm run lint` | ESLint (0 Fehler, 0 Warnungen; `components/ui/*` bleibt unangetastet) |
| `npm test` | Vitest, zwei Sorten (**89 Fälle**, gemessen 05.09.): `*.test.ts` prüft Logik ohne Oberfläche (Attrappe, Grunddaten-Abläufe, Sitzung, Klassensicht, Entwurfs-Wettlauf) in der Umgebung `node`; `*.test.tsx` rendert Komponenten mit `@testing-library/react` und beginnt dafür mit `// @vitest-environment jsdom`. Eine ganze Seite rendert `renderPage` aus `src/test-utils.tsx` (Router, QueryClient, angemeldete Attrappe) |
| `npm run build` | Typecheck + Produktionsbuild nach `dist/` (echter Client gegen `/api`) |
| `npm run build:mock` | Build mit Attrappe (`.env.mock`) — für `npx vite preview --outDir dist --port 4170` |
| `npm run build:demo` | Ein-Datei-Demo (`.env.demo`, Hash-Router, Fonts als data:-URLs) → `../demo/webapp-demo.html` |
| `npm run screenshots` | Chrome-headless-Screenshots aller Seiten aus der Demo-Datei nach `../demo/screenshots/` |
| `node scripts/klickpfad.mjs` | Klick-Durchlauf aller Anwendungsfälle gegen eine Mock-Preview (39 Schritte; braucht `npm i -D playwright-core`, Anleitung im Skriptkopf) |
| `npx playwright test -c scripts/playwright.config.ts` | **Abnahme gegen das echte Backend**: 42 Schritte der Checkliste (I-3 … S-4) im Chrome headless gegen nginx `http://localhost:1500`; vorher leere Datenbank (`cd ../deployment && docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v && BACKEND_PORT=8089 docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build`) und einmal `npm i --no-save playwright@1.60.0`; `ABNAHME_BASE`/`ABNAHME_ADMIN_PW` wie in `scripts/playwright.config.ts` |

Vor dem Screenshot-Lauf einmal deterministisch bauen (festes Datum, keine Latenz, Leitszenario vorgelegt), danach normal:

```bash
VITE_MOCK_TODAY=2026-09-21 VITE_MOCK_LATENCY=0 VITE_MOCK_SCENARIO=leitszenario npm run build:demo && npm run screenshots
npm run build:demo          # ausgelieferter Stand: echtes Datum, Latenz, leere Ereignisse
```

`../demo/` ist der Ordner `web-app/demo/` — er gehört **nicht** ins Repo: die Wurzel-`.gitignore` schließt `/demo/` aus.
Die Demo-Datei ist ein Anhang für den Anwendungspartner, kein Artefakt der Auslieferung.

## Modi und Umgebungsvariablen

| Variable | Wirkung |
|---|---|
| `VITE_MOCK=true` | Attrappe statt `/api` (`.env.mock`, `.env.demo`) |
| `VITE_HASH_ROUTER=true` | `createHashRouter` für die Datei-Demo (`.env.demo`) |
| `VITE_MOCK_DATASET=SMALL\|FULL` | Testdaten des Pflichtenhefts (Standard) oder 12 Klassen / 24 Lehrkräfte / 18 Räume |
| `VITE_MOCK_TODAY=2026-09-21` | festes „Heute" (`lib/today.ts`) für Screenshots und Klick-Durchläufe |
| `VITE_MOCK_LATENCY=0` | Kunstlatenz aus (Standard 150–400 ms) |
| `VITE_MOCK_SCENARIO=leitszenario` | Entwurf + Ereignisse Meyer/R101 + Vertretung 7a beim Start vorlegen |
| `VITE_PROXY_TARGET=…` | Ziel des Vite-Proxys für `/api` (`.env.prism` setzt `http://localhost:1501`; Standard `http://localhost:8080`) |

Es gibt **keine** `VITE_API_BASE`: die Basis ist immer `/api` (Umzug über Proxy/nginx, ohne Neukompilieren). Siehe `.env.example`.
`VITE_PROXY_TARGET` wirkt nur im Entwicklungsserver (`vite.config.ts`); im Produktionsbuild reicht nginx `/api/` an das
Backend weiter (`../deployment/nginx.conf`). Der Modus `prism` ist genau das: derselbe Client, anderes Proxy-Ziel.

| Datei | Modus | Wirkung |
|---|---|---|
| `.env.example` | — | Vorlage zum Kopieren nach `.env.local` (wird nicht eingelesen) |
| `.env.mock` | `npm run dev:mock`, `npm run build:mock` | Attrappe im Browser |
| `.env.prism` | `npm run dev:prism` | echter Client, `/api` → `http://localhost:1501` |
| `.env.demo` | `npm run build:demo` | Attrappe + Hash-Router für die Ein-Datei-Demo |

## Ordner

```
src/
  api/        types.ts (DTOs + Api-Interface = Vertrag) · client.ts (fetch) · hooks.ts (Query/Mutation-Hooks) · errors.ts · query-keys.ts
  mock/       Attrappe: mock-api.ts, store.ts, data-small.ts, data-full.ts, scenario.ts, logic/* (Konflikte, Warnungen, Solver, Optionen)
  app/        Shell, Sidebar, Topbar, CommandPalette, Router, Auth, RequireRole, nav.ts (die eine Navigationstabelle)
  routes/     w2.tsx … w5.tsx (Routen je Bereich) — zusammen genau die 20 Routen der Spezifikation
  pages/      Seiten je Bereich: login/, start/, plan/, draft/, my-plan/, masterdata/, rules/, settings/, substitution/, changelog/, app-demo/
  components/ ui/ (shadcn, unverändert) · theme/ · layout/ · states/ · stats/ · dialogs/ · forms/ (FieldError, DateField) · table/
              week-grid/ (WeekGrid, LessonTile, dnd) · plan/ (ViewSwitch, ObjectSelect, WeekPicker, ConflictList, Legend)
              draft/ · masterdata/ (shared.tsx = SearchField, RowMenu, Field, SectionCard, InUseDialog …) · substitution/ · changelog/ · phone/
  lib/        utils.ts (cn) · dates.ts · today.ts · timegrid.ts · entry-kind.ts · grid-entry.ts · subject-colors.ts · motion.ts · form-errors.ts
  index.css   ALLE Farben, Schriftgrößen, Radien, Schatten (Tokens; :root/.light und .dark) — kein Hex im JSX
scripts/      copy-demo.mjs (Demo-Datei kopieren) · screenshots.sh (Chrome headless) · klickpfad.mjs (Playwright-Rundgang)
```

Routen (19, Liste auch in `../STARTSTAND.md`): `/login`, `/` (Übersicht), `/plan`, `/entwurf`, `/vertretung`, `/verlauf`,
`/grunddaten/{lehrkraefte,faecher,klassen,klassen/:id,raeume,raster}`, `/regelwerk`, `/einstellungen?bereich=…`,
`/mein-plan` (Rolle Lehrkraft), `/app-demo` (nur Attrappe). URL-Parameter sind deutsch (`woche`, `sicht`, `id`, `datum`, `klasse`, `lehrkraft`, `seite`).

## Wie man eine Seite hinzufügt

1. Datei anlegen: `src/pages/<bereich>/<Name>Page.tsx`, exportiert `function <Name>Page()`.
2. In der Routen-Datei des Bereichs eintragen (`src/routes/w2.tsx` …): `{ path: '/grunddaten/raeume', element: <RoomsPage /> }`.
   `app/router.tsx` sammelt nur noch w2..w5 ein; unbekannte Pfade landen auf der einen `*`-Route (Seite nicht gefunden).
   Detailseiten geben die dritte Brotkrume mit: `handle: { crumb: (p) => <ClassCrumb id={p.id} /> }` — eine Komponente darf
   Daten laden (`pages/masterdata/ClassCrumb.tsx` zeigt „Klasse 7a" statt „Klasse 1"); ein String geht auch.
   Lehrkraft-Seiten setzen `handle: { role: 'TEACHER' }` und landen in der Lehrkraft-Shell.
3. Seitenaufbau: `PageHeader` → Zustände (`PageSkeleton` / `ErrorState` / `EmptyState`) → Inhalt.
4. Daten nur über die Hooks in `src/api/hooks.ts`; Texte deutsch in Schulsprache; Farben nur über Tailwind-Klassen aus `index.css`.

```tsx
// src/pages/masterdata/RoomsPage.tsx — Muster für Liste + Dialog + Mutation + Feldfehler
import { useState } from 'react'
import { toast } from 'sonner'
import { DoorOpen, Plus } from 'lucide-react'
import { useCreateRoom, useRooms } from '@/api/hooks'
import type { RoomDto, RoomInput } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { DataTable } from '@/components/table/DataTable'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { FieldError } from '@/components/forms/FieldError'
import { useFieldErrors } from '@/lib/form-errors'

export function RoomsPage() {
  const rooms = useRooms()                                   // Query: { data, isPending, isError, error, refetch }
  const create = useCreateRoom()                             // Mutation: invalidiert rooms/timetable/draft selbst
  const fields = useFieldErrors()                            // 400 validation → errors[field]
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<RoomInput>({ name: '', roomType: 'Klassenraum', active: true })

  const save = async () => {
    fields.clear()
    try {
      await create.mutateAsync(form)                         // 409/500 → Toast kommt automatisch (errors.ts)
      toast.success('Raum gespeichert')                      // Erfolg meldet die Seite, kurz und in Schulsprache
      setOpen(false)
    } catch (err) {
      fields.handle(err)                                     // nur 400-Feldfehler landen im Formular
    }
  }

  if (rooms.isPending) return <PageSkeleton />
  if (rooms.isError) return <ErrorState problem={rooms.error} retry={() => void rooms.refetch()} />
  return (
    <>
      <PageHeader title="Räume" actions={<Button onClick={() => setOpen(true)}><Plus /> Neuer Raum</Button>} />
      <DataTable<RoomDto>
        rows={rooms.data}
        rowKey={(r) => r.id}
        columns={[
          { key: 'name', header: 'Name', cell: (r) => r.name },
          { key: 'type', header: 'Raumart', cell: (r) => r.roomType },
        ]}
        empty={<EmptyState icon={DoorOpen} title="Noch keine Räume angelegt" />}
      />
      <FormDialog open={open} onOpenChange={setOpen} title="Neuer Raum" onSubmit={save} pending={create.isPending}>
        <div>
          <Label htmlFor="room-name">Name</Label>
          <Input id="room-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="mt-1" />
          <FieldError message={fields.errors.name} />
        </div>
      </FormDialog>
    </>
  )
}
```

## Konventionen (kurz)

- **Zustand**: TanStack Query (Server) + URL-Parameter (Sicht, Woche, Datum) + lokales `useState` (Dialog offen, Formularfelder). Kein Store.
- **Queries**: `useX()` aus `hooks.ts`; Schlüssel in `query-keys.ts`. `useDraft()` liefert `data === null`, wenn kein Entwurf existiert (404 ist ein Zustand).
- **Mutationen**: `useCreateX()/useUpdateX()/useDeleteX()` → `mutateAsync`. Invalidierung ist fest im Hook. Erfolg: `toast.success('… gespeichert')`.
  Fehler 409/404/500/Netz: Toast automatisch. 400 validation: `fields.handle(err)` → `FieldError`. Fehler, die die Seite selbst darstellt
  (409 `in-use` als Dialog, 409 `data-exists` als Rückfrage), bekommen keinen Toast: `useDeleteTeacher({ quiet: isInUse })`.
  Konflikte/Warnungen in 200-Antworten: **nie** Toast, sie stehen an der Stunde/Zeile.
- **Formulare**: kontrolliertes `useState<Input>`, `Label` + `Input`/`Select`/`Switch`/`DateField` + `FieldError`; keine Formularbibliothek.
  Kein `setState` in `useEffect` (Lint-Fehler): Dialoge bekommen beim Öffnen ein neues `key`, Tabellen mit Zeilenzustand `key={serverSignatur}`.
- **Radix Select**: in `onValueChange` nur nicht-leere Werte übernehmen (`if (v) …`) — beim Nachladen der Optionen feuert es einmal `''`.
- **Dialoge**: `FormDialog` (Enter sendet) für Anlegen/Bearbeiten, `ConfirmDialog` für Löschen/Verwerfen/Ersetzen.
  Ein nicht umkehrbarer Schritt nennt seine Folge **vor** dem Klick und sperrt das Senden bis zur Bestätigung (`submitDisabled`): `AcceptDialog` zählt vorher die Ausnahmen ab heute, die mit der Annahme verfallen.
- **Entwurf schreiben**: jede Entwurfs-Mutation trägt `{ cancel: [qk.draft] }`. Ohne das kann die Antwort des Solver-Pollings (alle 2 s) nach der Mutation eintreffen und den frischen Entwurf überschreiben (`src/api/draft-race.test.ts` zeigt beide Richtungen).
- **Tastatur/Screenreader**: `role="button"` und `tabIndex` nur, wo der Klick etwas bewirkt (`LessonTile`: `interactive`). Eine Auswahl, die man wieder aufheben kann, ist ein Umschalter mit `aria-pressed`, keine Radiogruppe.
- **Texte**: deutsch, Schulsprache (Lehrkraft, Klasse, Raum, Stundenraster, Regelwerk, Entwurf, aktiver Plan, festgelegt, Vertretung, Änderungsverlauf).
  `problem.detail` vom Server unverändert anzeigen. Kein „Failed to fetch", keine Statuscodes. Die Rolle heißt „Planung", nicht „Admin".
- **Farben/Größen**: nur Tailwind-Klassen aus `index.css` (`bg-substitution-soft`, `text-draft-fg`, `text-2xs`, …). Einzige Ausnahme:
  `subject.color` als `style={{ '--subject': color }}` auf `.lesson-tile`. Bedeutung nie nur durch Farbe — immer Badge oder Text dazu (`lib/entry-kind.ts`).
- **Bewegung**: Zeiten nur aus `lib/motion.ts`; `DataTable` animiert Zeilen, Seitenwechsel macht `AnimatedOutlet`; `prefers-reduced-motion` schaltet alles ab.
- **Datum**: nie `new Date()` in Seiten — `today()`/`now()` aus `lib/today.ts`, Rechnen mit `lib/dates.ts` (alle Werte `yyyy-MM-dd`).
- **TypeScript**: `erasableSyntaxOnly` — keine `enum`, keine Parameter-Properties, keine `namespace`; Enums sind String-Unions (`types.ts`).
- **JSX-Falle**: deutsche Anführungszeichen `„…"` nicht in `attr="…"` (das `"` schließt das Attribut) — `attr={'…'}` schreiben.

## Was vom Team-Gerüst blieb

Das Vite-Gerüst des Teams (Anthony, 03.09.) ist die Grundlage: **Vite, Tailwind, react-router, die ESLint-Basis und der
`src/index.css`-Ansatz** sind geblieben; TypeScript ist dazugekommen (E-43: neue Dateien sind `.tsx`, `allowJs` bleibt an).
Die Beispielseiten des Gerüsts sind nicht gelöscht, sondern liegen unverändert unter `src/example/` — sie sind **nicht
geroutet** und werden nicht gebaut, nicht gelintet und nicht getestet. Ihr Gegenstück im Backend ist
`backend/src/main/java/de/wsp3_1/example/` (`GET /api/clients`). Beides ist Lesestoff und verschwindet vor der
Liveschaltung; siehe `src/example/README.md`.

## Wenn das Backend anders antwortet als die Attrappe

Es gewinnt das Backend. `src/api/types.ts` ist die TypeScript-Abschrift von `../api-contract/openapi.yaml`; weicht eine
Antwort ab, wird zuerst der Vertrag geändert (Rolle 4), dann `types.ts`, dann `src/mock/**` — in einem Merge Request mit
dem Label `api-change`. Der Reihenfolge nach: Vertrag, Prism, Controller, Client.

## Grenzen der Attrappe

Kein Timefold (Greedy-Platzierung, feste 3 s; bei der vollständigen Schule bleiben einige Stunden offen — Fachraum-Engpass), keine
Persistenz über Neuladen, Passwörter im Klartext im Bundle, zweite Ausnahme für dieselbe Stunde am selben Tag ersetzt die erste (statt 409),
kein Token-Ablauf, keine Raumsperrzeit-Prüfung im Entwurf. Wo Attrappe und Backend abweichen, gewinnt das Backend; die Attrappe wird nachgezogen.
