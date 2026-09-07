# AGENT-NOTIZEN — web/ (08-Build)

Pflichtdatei: jede Paketänderung, jede Abweichung von `SPEZIFIKATION.md`, DoD-Belege, Wünsche an W1.
Eine Kopfzeile je Agent, neue Einträge unten anhängen. Format: `W<n> · <Datum> <Uhrzeit> · <Was> · Grund: …`

## W1 · Gerüst (04.09.2026, 16:00–16:40)

### Pakete (Abweichungen vom Zielzustand SPEZIFIKATION 1.2)
- W1 · 04.09. 16:05 · `cn@^0.2.5` statt `clsx` + `tailwind-merge` · Grund: `npx shadcn@4.20.1 init` schreibt heute `src/lib/utils.ts` als `export { cn } from "cn"` (Registry-Stand 04.09.). `cn` ist das offizielle shadcn-Paket (github.com/shadcn-ui/cn, „Drop-in replacement for clsx + tailwind-merge"). clsx/tailwind-merge sind nicht installiert; `cn(...)` verhält sich gleich.
- W1 · 04.09. 16:08 · `vitest@5.0.0` (devDependency) + Script `"test": "vitest run"` · Grund: DoD des Auftrags verlangt einen Vitest-Test des Mock-Stores — Abweichung von Δ-15 („kein test-Script vor T-509"). Install nur mit `npm install -D vitest@5.0.0 --legacy-peer-deps`: npm 10.8.2 bricht sonst mit `Cannot read properties of null (reading 'edgesOut')` (Arborist-Fehler am Peer-Set von vitest). Lockfile danach konsistent (`npm install` ohne Flag läuft fehlerfrei).
- W1 · 04.09. 16:10 · `shadcn` in package.json auf `^4.20.1` zurückgesetzt (init hatte `^4.21.0` eingetragen) · Grund: Pin laut Δ-25; `init` und `add` liefen mit `npx shadcn@4.20.1`.
- Über shadcn kamen: `radix-ui@^1.6.7`, `class-variance-authority@^0.7.1`, `cmdk@^1.1.1`, `sonner@^2.0.8`, `tw-animate-css@^1.4.0`, `lucide-react@^1.41.0`, `react-day-picker@^10.0.1`, `date-fns@^4.4.0`. `next-themes` und `@fontsource-variable/geist` deinstalliert (1.6 Schritt 4).
- `shadcn init` fragt trotz `-y` nach dem Überschreiben einer vorhandenen `components.json` — Antwort per `yes |` durchgereicht; Ergebnis identisch mit Abschnitt 1.5 (`"tsx": true`).

### Abweichungen / Ergänzungen zur Spezifikation
- `src/api/errors.ts` (neu): `problemOf`, `isValidation`, `isUnauthorized`, `toastProblem`. `toastProblem` ist Standard-`onError` aller Mutationen (QueryClient `defaultOptions.mutations`) — Seiten wiederholen keine Toast-Logik; 400-Feldfehler fängt `useFieldErrors().handle(err)` im `catch`.
- `src/mock/logic/exceptions.ts` (neu): `createException`/`deleteException` samt Verlaufseintrag, gemeinsam für `mock-api.ts` und `scenario.ts`.
- `useAuth().login()` liefert `Promise<LoginResponse>` (Spec: `void`), damit `LoginPage` nach Rolle umleiten kann. Zusätzlich `useAuth().role` und `booting` (stille `?demo=`-Anmeldung).
- `useDraft()` liefert `data === null` bei 404 (die Query fängt den 404) — Seiten prüfen `draft.data == null`, nicht `isError`.
- `Shell.tsx` zeigt eine schmale Entwurfs-Leiste („ENTWURF vorhanden — nicht aktiv, n offene Stunden") auf allen Seiten außer `/entwurf`; den vollen `DraftBanner` baut W3 auf `/entwurf`.
- Brotkrume dritte Stufe: Route setzt `handle: { crumb: string | (params) => string }` (Beispiel in `routes/w2.tsx`). Lehrkraft-Routen setzen `handle: { role: 'TEACHER' }` und landen in der Lehrkraft-Shell.
- ESLint: `react-refresh/only-export-components` auf `warn` mit `allowConstantExport` (Provider-Dateien exportieren Hook + Komponente); für `components/ui/**` und `hooks/use-mobile.ts` sind `only-export-components` und `react-hooks/set-state-in-effect` aus, weil das shadcn-Generat unverändert bleibt (die Regeln waren dort Fehler, nicht Warnungen).
- Typen: `TimegridInput`/`TimeslotInput` (id optional) für `PUT /timegrid`; `ClassSubjectInput.weeklyHours/teacherId` erlauben `null`, damit der 400-Fall (Zeile ohne Angaben, Testdurchlauf W-1 Schritt 8) tippbar ist; `EntryOriginal` trägt zusätzlich `teacherId`/`roomId` (für die Sicht GIVEN_AWAY). `MeDto` hat `id` und `username`.
- Mock: `replaceClassSubjects` zieht den Lehrkraft-Schnappschuss der Stunden nach (Lesson → ClassSubject → Teacher wie im Backend). `startSolve` platziert nach fest 3000 ms, `cancelSolve` sofort. Der Greedy-Solver platziert ungepinnte Stunden mit hartem Konflikt neu — Ergebnis nie doppelt belegt (T-W8.1). `EXTRA_LESSON` liefert `affected: []` mit `hint: "Zusatzstunde für <Klasse> am <Datum> angelegt."`. Konten von gelöschten Lehrkräften werden mitgelöscht.
- `mockApi.reset('SMALL'|'FULL')` und `mockApi.asUser(username)` (für `/app-demo`) exportiert; `createMockApiForTests(store, username)` für Vitest.
- `vitest.config.ts` importiert `./vite.config.ts` mit Endung (sonst Vite-Warnung `configLoader: 'native'`); `vite.demo.config.ts` blieb wie in 1.3 (Warnung dort tolerierbar).
- `index.html`: Anti-Flash-Script steht direkt vor dem Modul-Script (Abschnitt 3.4).

### Definition of Done (Belege, alle 04.09.)
1. `npm run typecheck` → exit 0.
2. `npx vite build --mode mock --outDir .agent-out/w1` → `✓ built in 804ms`; `index-*.js` 750.51 kB (gzip 235.38 kB), `index-*.css` 109.93 kB (gzip 17.92 kB), 7 Inter-`woff2` lokal (zusammen ≈ 218 kB). Bundle-Grep auf `http(s)://` trifft nur SVG-Namespaces, `react.dev/errors`, `reactrouter.com`-Doku-Links in Fehlermeldungen — keine Lade-URL.
3. `npm run lint` → 0 Fehler, 0 Warnungen.
4. `npm test` → 9/9 grün: Konfliktprüfer (T-W3.1, exakte Meldung „Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c)."), Warnungen (T-W1.4, T-W1.5), Verschieben löst Konflikt + pinned (T-W3.2/T-W9.1), Berechnen ohne Doppelbelegung (T-W8.1), Ausfall Meyer → 4 offene Stunden, Optionen Schulz/R204 ohne Meyer, Vertretung → SUBSTITUTED/GIVEN_AWAY, Verlauf ein Eintrag (T-W5.1/T-W6.1/T-W7.1), Rolle Lehrkraft 403 (E-38), FULL 12/24/18 ohne Doppelbelegung.
5. `npx vite --mode mock --port 5171` startet („VITE v8.2.2 mock ready in 232 ms"), `/login` → HTTP 200, `<title>Stundenplanung</title>`.
6. Screenshots (Preview 4171 auf `.agent-out/w1`, Chrome headless 1440×900, PNG geöffnet und geprüft) in `08-Build/demo/screenshots/`: `w1-login-light.png`, `w1-login-dark.png` (Glaskarte mit auth-bg, Schulname aus /health, drei Demo-Chips, Theme-Knopf), `w1-shell-light.png`, `w1-shell-dark.png` (Sidebar 4 Gruppen + App-Vorschau, aktiver Balken, Topbar mit Suche ⌘K, Avatar „PL", Platzhalter „in Arbeit"), `w1-shell-plan-light.png` (Brotkrume „Planung › Aktiver Plan"), `w1-mein-plan-dark.png` (Lehrkraft Meyer: nur „Mein Plan", Avatar „ME"). Dark Mode ohne weiße Flecken, Texte deutsch.
7. `npm run build:demo` → `../demo/webapp-demo.html` 1.10 MB, Inter als `data:`-URLs, keine Lade-URL; `file://…/webapp-demo.html#/grunddaten/klassen/1?demo=admin&theme=light` gerendert und geprüft (`w1-demo-file-shell-light.png`, Brotkrume „Grunddaten › Klassen › Klasse 1").
8. Angelegte Dateien: alle W1-Zeilen aus Abschnitt 8 plus `src/api/errors.ts`, `src/mock/logic/exceptions.ts`, `src/mock/mock.test.ts`, `vitest.config.ts`. Keine Datei außerhalb der W1-Zeilen. `src/components/ui/*` unverändert außer `sonner.tsx`-Import.

### Ungeprüft
- Echter HTTP-Client (`src/api/client.ts`) gegen ein laufendes Backend — es gibt noch keins; nur Typprüfung und Lesen.
- Command-Palette, Theme-Dropdown, Passwort-Dialog, Sidebar-Einklappen, Toasts, 401-Redirect: nur Typen/Build, nicht per Screenshot.
- FULL-Datensatz nur per Test (Zahlen, keine Doppelbelegung), nicht optisch; Leitszenario (`VITE_MOCK_SCENARIO`) nicht gebaut/gesehen.
- Motion-Übergänge (AnimatedOutlet, `layoutId`-Balken) nicht in Bewegung gesehen.
- Screenshots aller Seiten (`npm run screenshots`) erst nach W2–W5 sinnvoll.

### Übergabe an W2–W5
- Eingefroren: `src/api/types.ts`, `src/mock/**`, `src/index.css`, `components/ui/**`, `app/**`, `package.json`. Fehler darin → Zeile hier, W1 behebt.
- Eigene Routen in `src/routes/w<n>.tsx` (Kommentar zeigt das Muster). `/mein-plan` braucht `handle: { role: 'TEACHER' }`.
- Ports/OutDirs wie SPEZIFIKATION 11.1. Kein zweites `npm install`.
- Wünsche für gemeinsame Bausteine: im eigenen Ordner anlegen + Zeile hier; W1 zieht beim Zusammenführen hoch.
- Konventionen mit Codebeispiel: `README.md` (Abschnitt „Wie man eine Seite hinzufügt").

## W2 · Grunddaten · Regelwerk · Raster · Einstellungen (04.09.2026, 16:45–17:15)

### Pakete
- keine neuen Pakete, kein `npm install`.

### Angelegte Dateien (nur W2-Zeilen aus SPEZIFIKATION 8 + zwei Ergänzungen im eigenen Ordner)
- `src/routes/w2.tsx` (gefüllt: 8 Routen, Detailroute mit `handle.crumb`).
- `src/pages/masterdata/TeachersPage.tsx · SubjectsPage.tsx · RoomsPage.tsx · ClassesPage.tsx · ClassDetailPage.tsx · TimegridPage.tsx`, `src/pages/rules/RulesPage.tsx`, `src/pages/settings/SettingsPage.tsx`.
- `src/components/masterdata/TeacherDialog.tsx · TeacherTimeGrid.tsx · ClassDialog.tsx · ClassSubjectsTable.tsx · RoomDialog.tsx · SubjectDialog.tsx · TimegridEditor.tsx · RulesTable.tsx · UsersTable.tsx · UserDialog.tsx · SampleDataCard.tsx · EmptyActivePlanCard.tsx · HolidaysTable.tsx`.
- Ergänzt (eigener Ordner, Wunsch an W1 zum Hochziehen): `src/components/masterdata/shared.tsx` (SearchField, NoMatches, ActiveBadge, WarningChip, QuotaMeter, ColorDot, RowMenu, InUseDialog, Field, SectionCard) und `src/components/masterdata/helpers.ts` (STATE_LABEL, SCHOOL_TYPE_LABEL, ROLE_LABEL, SCOPE_LABEL, RULE_TYPE_LABEL, weeklyHoursOf, classSignature, matches, templateSlots/normalize/newSlot, isInUse, useDeleteWithInUse).
- Test: `src/components/masterdata/w2-flows.test.ts` (15 Fälle gegen die Attrappe, läuft mit `npm test`).
- `08-Build/demo/README.md`: Abschnitte „W-1 Grunddaten (W2)", „W-2 Regelwerk (W2)", „Einstellungen (W2)" ausgefüllt.

### Abweichungen / Entscheidungen
- W2 · 04.09. 16:50 · **Löschen mit 409 `in-use` über eigene `useMutation` (`useDeleteWithInUse`) statt `useDeleteTeacher/…`** · Grund: der Standard-`onError` der Hooks toastet jeden 409; die Spezifikation (Abschnitt 4) verlangt für `in-use` den Dialog „Wird noch verwendet — deaktivieren?" mit `detail`. Mit Hook + Dialog erschien beides. Der lokale Hook zeigt bei `in-use` nur den Dialog, sonst `toastProblem`, und invalidiert bei Erfolg alle Queries. Gleiches Muster in `TimegridPage` (409 → `ProblemBanner` inline) und `SampleDataCard` (409 `data-exists` → Dialog „Vorhandene Daten ersetzen?"). Wunsch an W1: Option `{ quiet?: (err) => boolean }` in `useApiMutation`, dann fallen die drei lokalen Mutationen weg.
- W2 · 04.09. 16:55 · **Formulare ohne `useEffect`** · Grund: `react-hooks/set-state-in-effect` (eslint-plugin-react-hooks 7) ist Fehler. Dialoge initialisieren `useState` aus Props; die Seite gibt beim Öffnen `key={zähler}` → frisches Formular, Schließen-Animation bleibt. Tabellen mit Zeilenzustand (`ClassSubjectsTable`, `TimegridForm`, `RuleRow`, `SchoolForm`) bekommen `key={serverSignatur}` und starten nach dem Speichern vom Serverstand.
- W2 · 04.09. 17:00 · `SettingsPage` Reiter in der URL als `?bereich=schule|beispieldaten|konten|passwort|ferien` (deutsch, wie `woche`/`sicht`).
- W2 · 04.09. 17:00 · `SampleDataCard` importiert `mockApi` aus `@/mock/mock-api` nur lesend für „Demo zurücksetzen" (hinter `IS_MOCK`); `mockApi.reset()` erhält die Tokens (store.ts Zeile 117), die Sitzung bleibt.
- W2 · 04.09. 17:05 · Warnungen je Zeile (`ClassSubjectsTable`): Zuordnung nach `teacherId` wie in 6.2, bei `SUBJECT_NOT_TAUGHT` zusätzlich der Fachname im `message` (sonst stünde die Warnung an jeder Zeile derselben Lehrkraft).
- W2 · 04.09. 17:05 · Deaktivierte Lehrkraft fehlt in der Auswahl der Klasse (T-W1.3 Schritt 5); ist sie in einer Zeile bereits eingetragen, bleibt sie dort mit Zusatz „deaktiviert" sichtbar (sonst wäre das Select leer).
- W2 · 04.09. 17:10 · Brotkrume der Detailseite zeigt „Klasse <id>" (crumb bekommt nur Params, kein Klassenname). Wunsch an W1: `crumb` optional als Komponente, die `useClass(id)` lesen darf.

### Definition of Done (Belege, alle 04.09.)
1. `npx tsc -p tsconfig.app.json --noEmit` → exit 0, Ausgabe leer.
2. `npx vite build --mode mock --outDir .agent-out/w2` → `✓ built in 555ms`, `index-*.js` 1 133 kB (gzip 336 kB), CSS 132 kB (gzip 21 kB). Zuwachs gegenüber W1 (750 kB) enthält auch die parallel gebauten Seiten von W3–W5 im selben Baum — nicht getrennt gemessen.
3. `npx eslint src/components/masterdata src/pages/masterdata src/pages/rules src/pages/settings src/routes/w2.tsx` → 0 Fehler, 0 Warnungen (Nicht-Komponenten-Exporte in `helpers.ts` ausgelagert).
4. `npx vitest run` → 24/24 grün (9 W1 + 15 W2: T-W1.1, 400-Feldfehler Lehrkraft, T-W1.3 inkl. 409-Text „Deaktivieren statt löschen", T-W1.4, T-W1.5, W-1 Schritt 8 (zwei Zeilenfehler), Schritt 9 (Mathematik 409 mit „7a, 8b, 9c"), Raster idempotent + 409 bei belegtem Slot, T-W2.1 (0 → 400, 6 bleibt; 3 gespeichert), Beispielsatz/Jahrgangsregel, Konfiguration, Beispieldaten 409/12-24-18, Konten, eigenes Passwort, Aktiven Plan leeren).
5. Preview `npx vite preview --outDir .agent-out/w2 --port 4172`; Chrome headless 1440×900 hell + dunkel → `08-Build/demo/screenshots/w2-<seite>-{light,dark}.png` für lehrkraefte, faecher, klassen, klasse-7a, raeume, raster, regelwerk, einstellungen, einstellungen-konten, einstellungen-beispieldaten (20 PNG, alle geöffnet: keine weißen Flecken im Dark Mode, Texte deutsch, keine Überlappung).
6. Im Browser durchgeklickt (Playwright-MCP gegen die Preview), Screenshots geöffnet: `w2-lehrkraft-dialog-light.png` (T-W1.1: Schulz Deputat 2, Mathematik, Fr 5./6. gesperrt im Mini-Raster), `w2-lehrkraft-in-use-light.png` + `w2-lehrkraft-deaktiviert-light.png` (T-W1.3: Meyer → 409-Dialog → Deaktivieren → Badge „deaktiviert", Toast), `w2-klasse-lehrkraft-auswahl-light.png` (Fachlehrkräfte zuerst, Deputat im Eintrag), `w2-klasse-warnung-light.png` (T-W1.4: amber Chip „Weber unterrichtet das Fach Mathematik nicht.", Kachel „Hinweise 1 — Speichern bleibt möglich", Toast „Fächer gespeichert — 1 Hinweis"), `w2-klasse-feldfehler-light.png` (Schritt 8: „Wochenstunden fehlen" / „Lehrkraft fehlt" an der Zeile), `w2-regelwerk-feldfehler-light.png` + `w2-regelwerk-gespeichert-light.png` (T-W2.1), `w2-fach-in-use-light.png` (Schritt 9), `w2-raster-409-light.png` (Montag aus → Banner „4 Zeitfenster sind im aktiven Plan oder Entwurf belegt"), `w2-beispieldaten-ersetzen-light.png` + `w2-beispieldaten-geladen-light.png` (409 data-exists → Ersetzen → Toast 12/24/18), `w2-lehrkraefte-full-light.png` (24 Zeilen), `w2-fach-dialog-light.png` (12 Farbfelder + Vorschau-Kachel).
   Testfälle: T-W1.1 ok · T-W1.3 ok · T-W1.4 ok · T-W1.5 ok (per Test; im Browser nicht separat) · T-W2.1 ok · Testdurchlauf W-1 Schritt 8/9 ok · Testdurchlauf „Beispieldatenbestand" 12/24/18 ok.

### Ungeprüft
- T-W1.5 (7a Mathematik auf Schulz) nur per Vitest, nicht im Browser geklickt (gleicher Codepfad wie T-W1.4).
- Konten-Dialog, Passwort-Dialoge, Ferien-Dialog, Jahrgangsregel-Dialog, Klassen-Dialog, Raum-Dialog: Typen, Build und Mock-Test — nicht per Screenshot.
- Dark Mode der Dialoge (nur Seiten dunkel geschossen).
- Echter HTTP-Client — kein Backend vorhanden.
- Motion (Zeilen-Einblendung, Dialog-Animationen) nicht in Bewegung gesehen.

### Wünsche an W1
- `useApiMutation` mit Option, bestimmte Fehler nicht zu toasten (siehe oben) — dann `useDeleteWithInUse`, `TimegridForm.put`, `SampleDataCard.load` auf die Hooks umstellen.
- `shared.tsx`/`helpers.ts` hochziehen: `SearchField`, `RowMenu`, `Field`, `SectionCard`, `WarningChip`, `InUseDialog` sind für W3/W4 brauchbar.
- Brotkrume mit Objektname (Klasse 7a statt Klasse 1).

## W3 · WeekGrid · Plan · Entwurf · Mein Plan (04.09.2026, 16:30–17:20)

### Pakete
- W3 · 04.09. · keine Paketänderung. dnd-kit, motion, react-day-picker, radix-ui wie von W1 installiert.

### Angelegte Dateien (nur eigene Zeilen aus Abschnitt 8, plus drei Hilfsdateien im eigenen Ordner)
- `src/components/week-grid/`: `WeekGrid.tsx` (Platzhalter ersetzt, gleiche Signatur + optionale Props `highlightLessonId`, `closedDays`, `busy`), `LessonTile.tsx` (LessonTile, MiniTile, DraggableTile, Changed), `TimeAxis.tsx`, `GridCell.tsx`, `dnd.tsx` (GridDndContext, DroppableCell), `ConflictPopover.tsx` (ConflictPopover, ConflictItem), `WeekGridSkeleton.tsx`, **`grid-entry.ts`** (Normalisierung LessonDto/EntryDto — Spec 6.3 nennt `lib/grid-entry.ts`; `lib/` gehört W1, deshalb hier; W1 darf hochziehen).
- `src/components/plan/`: `ViewSwitch.tsx`, `ObjectSelect.tsx`, `WeekPicker.tsx`, `ConflictList.tsx`, `EntryDetailPopover.tsx`, `Legend.tsx`, `ChangedTodayBar.tsx`, **`object-options.ts`** (`useObjectOptions(view)`, `VIEW_LABEL` — Hook getrennt von der Komponente wegen `react-refresh/only-export-components`), **`plan-params.ts`** (`useUrlState`, `parseView/parseMonday/parseId`, `todayDayIn` — gemeinsam für /plan, /entwurf, /mein-plan).
- `src/components/draft/`: `DraftBanner.tsx`, `OpenLessonsPanel.tsx`, `AddLessonDialog.tsx`, `LessonDetailPopover.tsx`, `SolveDialog.tsx`, `AcceptDialog.tsx`.
- `src/pages/plan/PlanPage.tsx`, `src/pages/draft/DraftPage.tsx`, `src/pages/my-plan/MyPlanPage.tsx`, `src/routes/w3.tsx` (drei Routen, `/mein-plan` mit `handle: { role: 'TEACHER' }`).
- Nur für den isolierten Build (gitignored, löschbar): `.agent-out/w3-config/vite.w3.config.ts` + `w2-stub.tsx` — ersetzt `@/routes/w2` durch ein leeres Array, weil W2s Dateien während meines Laufs Syntaxfehler hatten (s. u.).

### Abweichungen / Entscheidungen zur Spezifikation
- **Klick-Klick** (Rückfall zum Ziehen): Kachel-Klick öffnet immer den `LessonDetailPopover`; darin der Knopf **„Verschieben"** setzt `selectedLessonId` (Ring, Hinweisleiste „Zielzelle anklicken … Esc"), danach Klick auf eine leere Zelle → `onMove`. Grund: Wäre jeder Kachel-Klick zugleich die Auswahl, würde der nächste Klick auf eine leere Zelle verschieben statt den Handaufbau-Dialog zu öffnen (Falle beim Ansehen von Details). Esc hebt auf; Sichtwechsel hebt auf.
- `DraggableTile` liegt in `LessonTile.tsx` (nicht in `dnd.tsx`): sonst Import-Zyklus GridCell ↔ dnd und Hook-Export neben Komponenten (Lint-Warnung). `dnd.tsx` hält `GridDndContext` (PointerSensor distance 6, `pointerWithin`, deutsche Screenreader-Ansagen) und `DroppableCell`.
- Pausenzellen sind Drop-Ziel (Spec), aber **nicht** per Klick belegbar (kein „+" in der Pausenzeile — die Pausenregel würde sofort melden).
- Mini-Kacheln (Schulsicht, Doppelbelegung): eine Zeile `7a Ma · R101` (Spec: `classShort · roomShort` — Fachkürzel ergänzt, sonst ist die Schulsicht fachlos); bei geändertem Raum nur der neue Raum fett orange (kein Durchstreichen, Platz), Details im Popover. Ab 4 Einträgen: 2 Mini-Kacheln + Chip „+n" mit Popover.
- Kachel-Zeile 3 zeigt bei `GIVEN_AWAY`/`CANCELLED` den Text aus `entryLabel()` („abgegeben an SCZ", „entfällt", „verlegt → Di 3.") statt des Raums; bei `NEEDS_SUBSTITUTION` bleibt der Raum, das Badge „!" trägt die Bedeutung (Legende), der Text steht im Popover.
- Konfliktrahmen schlägt Art-Rahmen; Pin-Ring nur ohne Konflikt (sonst zwei Ringe).
- `EntryDetailPopover` lädt Langnamen (Fächer/Lehrkräfte/Räume/Klassen) nur mit `lookup=true` — `/mein-plan` übergibt `lookup={false}` (TEACHER darf diese Endpunkte nicht, E-45) und zeigt Kürzel.
- `AcceptDialog`: `warnings[]` des Entwurfs im Dialog; Warnungen aus der 200-Antwort („n Vertretungen ab heute verfallen") als Beschreibung des Erfolgs-Toasts (kein Fehler-Toast). Danach `navigate('/plan')`.
- `SolveDialog`: 409 `master-data-incomplete`/`pinned-contradiction` zusätzlich zum automatischen Toast als `ProblemBanner` im Dialog (missing[]/conflicts[]).
- `ConflictList.onJump`: Stunde nicht in der aktuellen Sicht → Wechsel auf `sicht=SCHOOL`, dann Scroll + 1 s `ring-primary`.
- Legende auf `/plan` und `/entwurf` eingeklappt, auf `/mein-plan` aufgeklappt (Lehrkräfte sehen die Seite selten).
- `?aktion=berechnen|annehmen` öffnet auf `/entwurf` den jeweiligen Dialog (für W4-Deep-Links); der Parameter wird beim Schließen entfernt.
- Druckansicht: `print-hide` an Toolbar, Konfliktliste, Legende, Druckknopf; Sidebar/Topbar haben es von W1. Nicht gedruckt/geprüft.

### Falle (gemessen, wichtig für W2/W4)
- **Radix Select feuert `onValueChange('')`**, wenn sich `value` ändert, bevor die Optionen im versteckten nativen `<select>` registriert sind (BubbleSelect dispatcht ein `change`-Event; `event.target.value` ist dann `''`). `Number('')` = 0 → ungültige ID → 400 „Die Klasse hat dieses Fach nicht" (per eigenem Playwright-Skript reproduziert). Abhilfe: in jedem `onValueChange` nur nicht-leere Werte übernehmen (`if (v) …`). In allen W3-Selects umgesetzt.
- JSX-Attribute mit deutschen Anführungszeichen `„…"` brechen (das schließende `"` beendet das Attribut): `description={'… „Annehmen" …'}` statt `description="…"`. W2s `SampleDataCard.tsx`/`SubjectDialog.tsx` hatten um 16:50 genau diesen Fehler (TS1003/TS1382) — deshalb mein isolierter Build.

### Definition of Done (Belege, 04.09. abends)
1. `npx tsc -p tsconfig.app.json --noEmit`: **0 Fehler in W3-Dateien**. Gesamt-Exit ≠ 0 nur wegen W2-Dateien (`src/components/masterdata/SampleDataCard.tsx`, `SubjectDialog.tsx`, Syntaxfehler, Stand 16:50–17:15) — nicht meine Dateien, nicht behoben.
2. Build: `npx vite build --mode mock -c .agent-out/w3-config/vite.w3.config.ts --outDir .agent-out/w3` (W2-Stub) → `✓ built in 453ms`, `index-*.js` 1.051,52 kB (gzip 317,28 kB), `index-*.css` 132,41 kB (gzip 21,03 kB). Der normale `npx vite build --mode mock --outDir .agent-out/w3` bricht an W2s Dateien ab (s. o.); sobald W2 grün ist, läuft er ohne Stub — ungeprüft, weil W2 parallel arbeitet.
3. `npx eslint` über alle W3-Ordner + `routes/w3.tsx`: **0 Fehler, 0 Warnungen**.
   **Nachtrag 17:25 (W2 hatte inzwischen nachgebessert):** `npx tsc -p tsconfig.app.json --noEmit` gesamt → **0 Fehler**; `npx vite build --mode mock --outDir .agent-out/w3` **ohne Stub** → `✓ built in 578ms`, JS 1.133,08 kB (gzip 335,81 kB), CSS 132,41 kB; `npm run build` (tsc + echter Client, schreibt `dist/`) → `✓ built in 478ms`, JS 1.101,43 kB (gzip 328,54 kB); `npx eslint .` gesamt → 0 Probleme. Der Klick-Durchlauf (Punkt 5) lief gegen den Stub-Build; die W3-Dateien sind in beiden Builds identisch.
4. Screenshots (Preview 4173, Chrome headless 1440×900, `VITE_MOCK_TODAY=2026-09-21`, `VITE_MOCK_LATENCY=0`, `VITE_MOCK_SCENARIO=leitszenario`; alle PNGs geöffnet und geprüft) in `08-Build/demo/screenshots/`: `w3-plan-schule-light/dark` (Mini-Stapel, Vertretung V mit R204, vier „!"), `w3-plan-klasse-dark` (7a: `~~MEY~~ → SCZ`, `~~R101~~ → R204`), `w3-plan-lehrkraft-light` (Meyer: „abgegeben an SCZ", drei „!"), `w3-plan-raum-dark` (R101), `w3-entwurf-klasse-light/dark` (Banner, Pins, ⚠ 1, Konfliktliste), `w3-entwurf-raum-dark` (Doppelbelegung 7a/9c als Mini-Stapel mit ⚠), `w3-mein-plan-meyer-light` („Heute geändert: 4 Stunden", Legende offen), `w3-mein-plan-schulz-dark` (V, „Heute geändert: 1 Stunde"). Dark Mode ohne weiße Flecken, Texte deutsch.
5. Klick-Durchlauf per eigenem Playwright-Skript (playwright-core aus dem npx-Cache + installiertes Chrome, Skript im Scratchpad `w3build/w3-check.mjs`, 17 Schritte, 0 Seitenfehler):
   - T-W3.1: ok — Konfliktliste „Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c).", Badge ⚠ 1 an 7a Ma Mo 1.
   - T-W3.2: ok — Klick-Klick 7a Ma → Mo 5, danach „Keine Konflikte".
   - T-W9.1: ok — verschobene Stunde trägt Pin; Pin-Klick → Toast „Festlegung aufgehoben", Pin weg. Nach Berechnen (W-8) bleibt der Pin an Mo 3 (1 vorher / 1 nachher).
   - W-3 Handaufbau: ok — leere Zelle Mi 1 → Dialog „Stunde eintragen — 7a", Fach vorbelegt „Mathematik — Meyer", Raum R101 → „Stunde eingetragen".
   - T-W8.1: ok — Dialog „Rechenzeit: 60 s (Einstellungen)", Banner „Berechnung läuft" + Abbrechen, nach 3 s wieder EDITING, keine Doppelbelegung in der Konfliktliste.
   - T-W1.2: ok — 9c Ma (Schulz) nach Fr 5 → „Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde"; Klick in der Konfliktliste hebt die Kachel hervor.
   - Verwerfen → „Kein Entwurf vorhanden" → „Aktiven Plan kopieren" → Banner: ok. Annehmen → Dialog → Toast „Plan angenommen" → `/plan`: ok.
   - T-W4.1: ok — Klasse 7a Mo 1 „MEY → SCZ / R101 → R204 / V"; Lehrkraft Meyer „abgegeben an SCZ" + Di 1 Ma 7a; Raum R101 (Woche 28.09.) Mo 1 + 2 belegt.
   - T-W5.1: ok — Schule 21.09.: 4 × „noch nicht vertreten" am Montag, 0 am Dienstag (Zählung per aria-label; mit der Raumsperrung sind es 5 Einträge inkl. Weber, T-W5.1 zählt die 4 nach dem Ausfall — beide Zählungen stimmen mit der Lernnotiz vom 04.09.).
   - Popover „noch nicht vertreten": Anlass „Wasserschaden", Link „Zur Vertretung" → `/vertretung?datum=2026-09-21`: ok. Wochenpfeil → `woche=2026-09-28`, Legende klappt auf: ok.
   - Mein Plan Meyer „Heute geändert: 4 Stunden", Sidebar nur „Mein Plan"; Schulz „Heute geändert: 1 Stunde", Mo 1 V + R204, Popover ohne Grunddaten-Abrufe: ok.
6. Rundgang-Abschnitt in `08-Build/demo/README.md` (W-3/W-9/W-8/W-4 + Mein Plan) geschrieben.

### Ungeprüft
- Drag-and-Drop mit echter Maus: Playwrights `dragTo` landet bei dnd-kit auf der Quellzelle (Ansage „In Zeitfenster 1 abgelegt." kam, d. h. DndContext/Drop-Ziel funktionieren), ein echter Zug über Zellen wurde nicht gesehen. Der Klick-Klick-Weg ist belegt.
- Druckansicht (`window.print()`), FULL-Datensatz im Raster (12 Klassen — Schulsicht mit vielen Mini-Stapeln), Sa-Spalte, `validFrom`-Annahme, Berechnung nur für eine Klasse (Kann), Verhalten bei `FAILED` (Mock erzeugt es nie), Motion in Bewegung.
- `npm run build` als Ganzes und die Ein-Datei-Demo mit W3-Seiten (W2-Dateien blockierten; W1 prüft beim Zusammenführen).

### Wünsche an W1
- `components/week-grid/grid-entry.ts` nach `lib/grid-entry.ts` heben (Spec-Pfad), `components/plan/plan-params.ts` (`useUrlState`) ggf. nach `lib/`, wenn W4 dieselben URL-Parameter braucht.
- `lib/entry-kind.ts`: `LEGEND`-Text „Rot gestrichelt + ⚠ = Warnung" — passt; nichts zu ändern.
- Hinweis für W4 (`StartPage`): `WeekGrid` braucht `todayDay`/`dateByDay`/`closedDays` aus `WeekDto.days` — Helfer `todayDayIn(monday)` in `components/plan/plan-params.ts`.

## W4 · Übersicht · Vertretung · Verlauf (04.09.2026, 16:20–17:00)

### Angelegte Dateien (nur W4-Zeilen aus Abschnitt 8 plus drei Helfer im eigenen Ordner)
- `src/pages/start/StartPage.tsx` (`/`), `src/pages/substitution/SubstitutionPage.tsx` (`/vertretung?datum=`), `src/pages/changelog/ChangelogPage.tsx` (`/verlauf?von=&bis=&klasse=&lehrkraft=&stunde=&seite=`), `src/routes/w4.tsx` (drei Routen eingetragen).
- `src/components/substitution/EventForm.tsx`, `PendingTable.tsx`, `OptionsPanel.tsx`, `MoveDialog.tsx`; zusätzlich `DateField.tsx` (Popover + shadcn `Calendar`, Wert yyyy-MM-dd — Wunsch: nach `components/forms/` hochziehen, W2 braucht dasselbe für Ferien), `position-label.ts` (Rasterposition → „1." über `lessonNumber`, Pausen nicht mitgezählt), `types.ts` (`SelectedLesson`, `ResolvedRow`).
- `src/components/changelog/EventChip.tsx`, `ChangelogFilters.tsx`.

### Abweichungen / Ergänzungen zur Spezifikation
- `PendingTable` hat zusätzlich `resolved?: ResolvedRow[]`; `OptionsPanel.onAssigned(result: ResolvedRow)` liefert Art, neue Kürzel und `conflicts[]` zurück. Grund: die aufgelöste Zeile bleibt bis zum Wochenwechsel sichtbar (orange durchgezogen, `~~WEB~~ → SCZ`, Badge V) — der Refetch würde sie sonst sofort aus `/substitutions/pending` entfernen, und das Demo-Skript verlangt genau dieses Bild. Zustand liegt in der Seite (`useState`), kein Store.
- Markierte Stunde ist abgeleitet: gewählte Zeile, sonst erste offene der Woche; nach Zuweisen/Entfall/Verlegen die nächste offene nach der erledigten. `OptionsPanel` bekommt `key=<date:lessonId>`, damit die Auswahl beim Zeilenwechsel zurückgesetzt wird.
- Zusatzstunde: Formular hat zusätzlich das Feld „Lehrkraft" (aus der Klassenzuordnung vorbelegt), weil `ExtraLessonInput.teacherId` Pflicht ist und der Mock ihn validiert. `dateFrom/dateTo` = Datum der Zusatzstunde.
- Folgekonflikte einer `POST /exceptions`-Antwort stehen unter dem Optionen-Panel als `ProblemBanner` (Status 200, nur `conflicts[]`); ohne Konflikt eine grüne Statuszeile „… — ohne Folgekonflikt".
- Wochenwechsel auf `/vertretung`: eigene Pfeile + `DateField` + „Heute" (der `WeekPicker` gehört W3; Wunsch: nach dem Zusammenführen ersetzen).
- Startseite: 4 `StatTile` wie Abschnitt 4; „n Stunden heute" steht in der Kopfzeile. Zwei Pending-Queries (diese Woche für die Kachel, 14 Tage für „Nächste offene Stunden", max. 5). Lehrkraft-Konto → `<Navigate to="/mein-plan">` (zusätzlich zu `RequireRole`). WeekGrid nur über die Signatur 6.3; der letzte Build (16:54) enthielt bereits W3s echtes Raster — `w4-start-light/dark.png` zeigen Kacheln mit V/!-Badges, Heute-Spalte und Pausenzeile, die Übergabe `slots/entries/todayDay/dateByDay/conflictsByLessonId` passt.
- `/verlauf`: Filter stehen komplett in der URL (deutsch), Seitengröße 50, keine Bearbeiten-/Löschen-Aktion. Klick auf eine Sidebar-Seite verliert `?demo=` (in-app-Navigation) — Token bleibt im Speicher, kein Problem.

### Definition of Done (Belege, alle 04.09.)
1. `npx tsc -p tsconfig.app.json --noEmit`: keine Fehler in W4-Dateien. **Fremd:** `src/components/masterdata/SubjectDialog.tsx` (W2, in Arbeit) hatte um 16:45 Parse-Fehler (Zeilen 55–98) — nicht angefasst.
2. `VITE_MOCK_TODAY=2026-09-21 VITE_MOCK_SCENARIO=leitszenario VITE_MOCK_LATENCY=0 npx vite build --mode mock --outDir .agent-out/w4` → `✓ built in 821ms`, `index-*.js` 984 kB (gzip 300 kB), CSS 132 kB — enthält bereits Dateien von W2/W3, W4-Anteil nicht getrennt messbar.
3. `npx eslint` auf alle W4-Dateien → 0 Fehler, 0 Warnungen.
4. Screenshots (Preview 4174, Chrome headless 1440×900, alle geöffnet und geprüft) in `08-Build/demo/screenshots/`: `w4-start-light/dark`, `w4-vertretung-light/dark`, `w4-verlauf-light/dark`; per Playwright durchgeklickt und festgehalten: `w4-vertretung-nach-zuweisen.png` (Schulz zugewiesen → Zeile `~~WEB~~ → SCZ`, Badge V, nächste Stunde 8b markiert, Statuszeile ohne Folgekonflikt), `w4-vertretung-nach-ereignis.png` (Raumsperrung R204 „Heizung defekt" → „4 betroffene Stunden", Anlass-Spalte „Krankmeldung; Heizung defekt"), `w4-verlauf-filter-weber.png` (Filter Lehrkraft Weber → genau 1 Eintrag, URL `?lehrkraft=3`). Dark Mode ohne weiße Flecken. Browser-Konsole: 0 Fehler, 0 Warnungen.
5. Testfälle gegen den Mock (Leitszenario, Heute = 21.09.2026): T-W5.1: ok (nach Ausfall Meyer + Vertretung 7a 1. sind 4 offene Stunden gelistet: 7a 2. (Wasserschaden), 8b 2., 7a 3., 8b 4.; Sidebar-Zähler 4). T-W6.1: ok (Optionen für 8b Ma 2.: Weber, nicht Meyer; R204 ja — für 7a De 2.: Schulz, nicht Meyer; R101 fehlt). T-W7.1: ok (Verlauf-Eintrag „Meyer / R101 → Schulz / R204", Anlass „Krankmeldung; Wasserschaden", von admin; keine Bearbeiten-Aktion). Zuweisen, Ereignis erfassen, Verlauf-Filter: ok. Entfall (ConfirmDialog) und Verlegen (MoveDialog): nur Typen/Build, **ungeprüft** im Browser. `/?demo=meyer` → `/mein-plan`: ok.
6. Rundgang-Schritte in `08-Build/demo/README.md` (Abschnitt W-5/W-6/W-7) eingetragen.

### Ungeprüft / Wünsche an W1
- Entfall- und Verlegen-Pfad, Zusatzstunde-Formular, Feldfehler (400) im Ereignisformular, leere Woche (`EmptyState`) — nicht im Browser gesehen.
- `DateField` nach `components/forms/` ziehen (W2 Ferien, W3 AcceptDialog könnten es nutzen).
- Startseite mit dem Zusammenführungs-Build noch einmal ansehen (Kachel „7a M. · ~~R101~~ → …" in der Schulsicht wird bei 140 px Spaltenbreite abgeschnitten — Sache der Mini-Kachel von W3, nicht der Seite).
- zsh-Falle im Screenshot-Skript: die Schleifenvariable darf nicht `path` heißen (überschreibt `PATH`) — Skript 10.3 nutzt `path`, unter zsh bricht danach `cp`/`ls`; unter bash (Shebang) kein Problem.

## W5 · App-Vorschau (04.09.2026, 16:30–17:10)

### Angelegte Dateien (nur eigene Zeilen aus Abschnitt 8 + zwei Helfer im eigenen Ordner)
- `src/pages/app-demo/AppDemoPage.tsx` · `src/routes/w5.tsx` (Route `/app-demo`)
- `src/components/phone/PhoneFrame.tsx` (auch `PhoneAppBar`), `PhoneApp.tsx`, `PhoneSettingsScreen.tsx`, `PhoneDayScreen.tsx` (auch `PhoneBanners`, `PhoneErrorState`), `PhoneWeekScreen.tsx`, `PhoneDetailSheet.tsx`, `PhoneEntryCard.tsx`, `PhoneBanner.tsx`, `DemoControls.tsx`
- `src/components/phone/phone-state.ts` (Reducer = `UiState`/`Screen` aus 5.1, `isReachableUrl`, `countChangedToday`) · `src/components/phone/phone-theme.css` (siehe Wunsch an W1)

### Abweichungen / Entscheidungen
- W5 · 04.09. 16:40 · `phone-theme.css` mit Klasse `.phone-light` = wörtliche Kopie der `:root`-Tokens aus `index.css` · Grund: „Telefon hell" auf dunkler Seite braucht einen Light-Override unter `.dark`; index.css kennt nur `.dark`. **Wunsch an W1:** `.light { … }` (gleiche Werte wie `:root`) in index.css aufnehmen, dann `phone-theme.css` löschen und in `PhoneFrame` `'phone-light'` → `'light'`.
- URL-Parameter der Seite: `als=meyer|schulz|weber`, `datum=yyyy-MM-dd` (= „Heute" des Telefons: Statusleiste, Heute-Knopf, „Heute geändert"), `offline=1`, `telefon=dunkel|hell` (ohne Angabe folgt das Telefon dem Seiten-Theme). `scripts/screenshots.sh` (Zeile `app-demo`) passt unverändert.
- Konto-Wechsel in der Steuerung meldet still an (Passwort = Benutzername, einmal je Konto); „Abmelden" im Telefon meldet nicht wieder an. Anmeldung im Telefon über `mockApi.login` (prüft Passwort, setzt **kein** Web-Token); Abmelden nur lokal, weil `mockApi.logout()` das Token der Web-Sitzung (`authToken`) löschen würde.
- „Verbindung prüfen"/Abruf: Adresse gilt als erreichbar, wenn http(s)-URL und Host nicht in TEST-NET-1 (`192.0.2.0/24`, RFC 5737) — so liefert `http://192.0.2.1:9999` die Warnung aus T-A3.1, alles andere antwortet über die Attrappe. Gespeicherte unerreichbare Adresse oder „Server aus" → Offline-Cache (Ref je Montag) oder Fehler „Server nicht erreichbar und kein gespeicherter Stand."
- Stundendetail zeigt Kürzel statt Langnamen (Fach/Raum/Lehrkraft): `EntryDto` trägt nur `*Short`, und die Rolle TEACHER darf `/subjects`, `/rooms`, `/teachers` nicht lesen (E-45). Langnamen bräuchten Felder im Vertrag (`subjectName`, `roomName`, `teacherName`) — offen für api.md.
- `PhoneFrame` 360 × 760 wie 6.5 (der Auftragstext nennt 390 × 844; 844 px passen nicht unter Topbar + Entwurfsleiste in 900 px Höhe).
- Wochenwechsel/Tageswechsel über Wochengrenze rufen `load()` (wie `refresh()` in 5.4); Pull-to-Refresh = Knopf in der App-Bar + Hinweistext; „Neustart" behält Adresse/Anmeldung (DataStore) und startet bei `Day(heute)`.
- Kein neues Paket, keine fremde Datei geändert. `AppDemoPage` zeigt außerhalb des Mock einen `EmptyState` („Nur in der Demo verfügbar").

### Definition of Done (Belege, alle 04.09.)
1. `npx tsc -p tsconfig.app.json --noEmit`: eigene Dateien fehlerfrei. **Fremd:** `src/components/masterdata/SubjectDialog.tsx` (W2, in Arbeit) hatte um 16:55 sechs Syntaxfehler (TS2657/TS1003/TS17002 …) — nicht angefasst.
2. `VITE_MOCK_TODAY=2026-09-21 VITE_MOCK_LATENCY=0 VITE_MOCK_SCENARIO=leitszenario npx vite build --mode mock --outDir .agent-out/w5` → `✓ built`, JS 984 kB (gzip 300 kB), CSS 132 kB (Stand enthält auch W2–W4-Dateien, die zu dem Zeitpunkt im Baum lagen).
3. `npx eslint src/components/phone src/pages/app-demo src/routes/w5.tsx` → 0 Fehler, 0 Warnungen.
4. Preview 4175, Playwright 1440 × 900, PNGs geöffnet und geprüft: `demo/screenshots/w5-app-demo-tag-schulz-light.png`, `w5-app-demo-detail-schulz-light.png`, `w5-app-demo-woche-schulz-light.png`, `w5-app-demo-einstellungen-light.png`, `w5-app-demo-tag-meyer-dark.png`, `w5-app-demo-woche-meyer-dark.png`, `w5-app-demo-hell-auf-dunkel.png`, `w5-app-demo-offline-fehler-dark.png`. Dark ohne weiße Flecken, Texte deutsch, Legende in der Wochenansicht.
5. Testfälle gegen den Mock (Leitszenario, 21.09.): T-A3.1 ok (Warnung, „Trotzdem speichern", nach „Neustart" steht die Adresse noch im Feld); T-A1.1 ok (Meyer Tag 21.09.: vier Einträge — 1 abgegeben, 3 noch nicht vertreten; Woche zeigt die Matrix, keine Bearbeitung); T-A2.1 ok (Schulz: „Vertretung" 7a, R101 → R204, 08:00–08:45, MEY → SCZ, Anlässe „Krankmeldung", „Wasserschaden"; Meyer: „abgegeben an SCZ" + drei „noch nicht vertreten"); E-40 ok („Heute geändert: 1 Stunde" bei Schulz, „4 Stunden" bei Meyer; Offline-Banner „Keine Verbindung — Stand: 21.09.2026 16:53" nach „Trotzdem speichern" der TEST-NET-Adresse).
6. Rundgang-Abschnitt in `08-Build/demo/README.md` (A-1/A-2/A-3) geschrieben.

### Ungeprüft
- Swipe/Touch, echte Pull-to-Refresh-Geste (im Browser Knopf), Bildschirmleser.
- Verhalten mit Datensatz FULL (nur SMALL gesehen); `MOVED`/`ADDED`/`CANCELLED` im Telefon nur per Typ, nicht per Screenshot (Leitszenario legt nur die Vertretung an).
- 401-Pfad („Sitzung abgelaufen") — die Attrappe lässt Tokens nicht ablaufen.

## W6 · Zusammenführung, Rundgang, Demo (04.09.2026, 17:20–17:50)

### Pakete
- keine neuen Pakete, kein `npm install` (vite-plugin-singlefile war seit W1 installiert).

### Geänderte fremde Dateien (jetzt erlaubt) — Wünsche aus W2–W5 umgesetzt
- `src/api/hooks.ts`: `MutationOpts { quiet?: (err) => boolean }` — `useApiMutation` toastet jeden Fehler außer denen, die `quiet` bejaht. Optional auf `useDeleteTeacher/Subject/Room/Class`, `usePutTimegrid`, `useLoadSampleData`. Damit fielen die drei lokalen `useMutation` weg: `helpers.useDeleteWithInUse(useDeleteX)` (nimmt jetzt den Hook, Invalidierung bleibt im Hook), `TimegridPage` (`usePutTimegrid({ quiet: isInUse })`), `SampleDataCard` (`useLoadSampleData({ quiet: isDataExists })`). Vier Grunddaten-Seiten importieren `api` nicht mehr.
- `src/app/Topbar.tsx`: `handle.crumb` darf `ReactNode` liefern → `routes/w2.tsx` gibt `<ClassCrumb id={p.id} />` (neu: `pages/masterdata/ClassCrumb.tsx`, liest `useClass`). Brotkrume zeigt „Klasse 7a" statt „Klasse 1" (Screenshot `klasse-7a-light.png`).
- `src/index.css`: `:root, .light { … }` plus `color-scheme: light|dark` — W5s `phone-theme.css` (Token-Kopie) nach `~/.Trash`, `PhoneFrame` nutzt `'light'`. Gemessen im Klick-Durchlauf: Telefon-Hintergrund rgb(250,250,250) auf dunkler Seite.
- `components/week-grid/grid-entry.ts` → `lib/grid-entry.ts` (Spec-Pfad), sechs Importe angepasst.
- `components/substitution/DateField.tsx` → `components/forms/DateField.tsx`; zusätzlich benutzt in `draft/AcceptDialog.tsx` (statt eigenem Popover/Calendar) und `masterdata/HolidaysTable.tsx` (statt nativer `type="date"`-Eingaben).
- `pages/substitution/SubstitutionPage.tsx`: eigene Wochenpfeile/DateField/„Heute" durch W3s `WeekPicker` ersetzt — Wochenwahl ist auf /plan, /vertretung, /mein-plan dieselbe; Zeile darunter zählt nur noch offene Stunden.
- `components/phone/PhoneApp.tsx` + `pages/app-demo/AppDemoPage.tsx` (Fehler, im Klick-Durchlauf gefunden): nach „Erststart → Zurücksetzen" blieb `firstStart` für immer `true`, ein Kontowechsel in der Steuerung meldete deshalb nicht mehr still an — das Telefon zeigte weiter Meyer, obwohl „Schulz" gewählt war (T-A2.1 nach T-A3.1 nicht durchführbar). Fix: Kontowechsel setzt `firstStart` zurück; `autoLoginDone` steht vor `loginAs` und wird auch bei Handanmeldung gesetzt (keine Doppelanmeldung).
- `src/mock/mock.test.ts`: neuer Fall „Vertretungsoptionen: fachgleiche Lehrkraft zuerst, dann geringere Belastung, dann Name; passender Raum zuerst (W-6)" (vierte Lehrkraft „Zimmer" im Store, damit die Belastungsreihenfolge sichtbar wird).
- `README.md` (web) fertig: Start, Modi, Struktur, Seite anlegen, Übernahme nach `web-app/frontend/` (7 Schritte für Rolle 5, ohne Push). `../demo/README.md` überarbeitet (Reihenfolge, Meyer wieder aktivieren, offene Stunden bei FULL, Screenshot-Tabelle).
- `scripts/klickpfad.mjs` (neu): der Klick-Durchlauf aus Punkt 5 als wiederholbares Skript (playwright-core lokal installieren, Anleitung im Kopf; Screenshots nach `.agent-out/klickpfad-shots/`).
- `scripts/screenshots.sh` unverändert (bash-Shebang, `path` als Variable ist unter bash harmlos); Agenten-Screenshots nach `demo/screenshots/agenten/` verschoben.

### Belege (alle 04.09., gemessen)
1. `npx tsc -p tsconfig.app.json --noEmit` → exit 0. `npx eslint .` → 0 Fehler, 0 Warnungen. `npx vitest run` → 25/25 (9 W1 + 15 W2 + 1 W6).
2. `npm run build` (echter Client) → `dist/assets/index-*.js` 1 101 kB (gzip 329 kB), CSS 132 kB; Vite warnt ab 500 kB (Code-Splitting offen, s. u.). Mock-Build `.agent-out/w6`: JS 1 133 kB (gzip 336 kB).
3. Demo: deterministisch (`VITE_MOCK_TODAY=2026-09-21 VITE_MOCK_LATENCY=0 VITE_MOCK_SCENARIO=leitszenario npm run build:demo`) → 1 556 kB; danach normal `npm run build:demo` → **`demo/webapp-demo.html` 1 555 513 Byte (1,48 MB, gzip 577 kB)**, echtes Datum, Latenz, leere Ereignisse. `grep` auf `http(s)://` trifft nur SVG/XML-Namespaces, `react.dev/errors`, `reactrouter.com`, zwei GitHub-Links in Fehlertexten und die Vorbelegungen der Serveradresse (`134.245.1.240`, `192.0.2.1`, `localhost`) als Strings — keine Lade-URL. `file://…/webapp-demo.html#/login`, `#/?demo=admin`, `#/plan?demo=admin&sicht=CLASS&id=1`, `#/grunddaten/lehrkraefte?demo=admin&theme=dark` per Chrome headless gerendert und angesehen.
4. Screenshots: `demo/screenshots/<route>-{light,dark}.png`, 38 PNG (19 Routen × 2), aus der deterministischen Demo-Datei, alle geöffnet: keine leere Fläche, Dark Mode ohne weiße Flecken, Texte deutsch, Brotkrume „Klasse 7a", WeekPicker auf /vertretung, Telefon in beiden Themes.
5. Klick-Durchlauf (eigenes Playwright-Skript, playwright-core aus dem npx-Cache + installiertes Chrome, gegen `npx vite preview --outDir .agent-out/w6 --port 4176`, Mock mit `VITE_MOCK_TODAY=2026-09-21`, Latenz 0, ohne Szenario — Navigation in-App per pushState/popstate, damit der Mock-Zustand bleibt): **39/39 Schritte ok, 0 Seitenfehler, 0 console.error**. Reihenfolge = demo/README: T-W1.3 (Krause anlegen/löschen; Meyer → 409 „Lehrkraft wird noch verwendet … Deaktivieren statt löschen." → Badge „deaktiviert" → wieder aktivieren), T-W1.1 (Schulz-Dialog: Deputat 2, Mathematik), T-W1.4, T-W1.5, Schritt 8 (zwei Feldfehler), Schritt 9 (409 „Mathematik wird in 7a, 8b, 9c unterrichtet."), T-W2.1 (0 → „muss mindestens 1 sein", 3 → gespeichert), T-W3.1 (⚠ 1 + Konfliktliste), T-W3.2 (Klick-Klick nach Mo 5 → „Keine Konflikte"), T-W9.1 (Pin aufheben/festlegen), T-W1.2 (Fr 5 → „Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde"), weicher Verstoß „Maximale Stunden pro Tag (3) überschritten: Klasse 7a, Montag: 4 Stunden", Annehmen → /plan, Beispieldaten 12/24/18, Aktiven Plan leeren (0 Kacheln, Grunddaten 12 Klassen), T-W8.1 (Leer beginnen → Berechnen → „Berechnung läuft" → 332 belegt / 18 offen, keine Doppelbelegung; aktiver Plan bleibt leer bis Annehmen, danach Kacheln), Demo-Reset Testdaten, T-W4.1 (vier Sichten), Dark-Mode + ⌘K „R101" → `sicht=ROOM&id=1`, T-W5.1 (4 betroffene, Di keine), Raumsperrung (5 offen, „Krankmeldung; Wasserschaden"), T-W6.1 (Schulz mit Chip „Mathematik"/„0 Vertretungen", Weber; kein Meyer; R204 ja, R101 nein), Zuweisen (Zeile ~~MEY~~ → SCZ, nächste markiert, „ohne Folgekonflikt"), Plan 21.09. V / 28.09. unverändert, T-W7.1 (genau 1 Eintrag, kein Bearbeiten), E-38 (meyer → /mein-plan, „Heute geändert: 4 Stunden", 1 abgegeben + 3 noch nicht vertreten, /plan → Redirect), T-A3.1 (Warnung, Trotzdem speichern, Fehlerzustand ohne Cache, Neustart behält Adresse und Anmeldung), T-A1.1 (Tag 4 Einträge, Woche 5 Kacheln, kein Bearbeiten), T-A2.1 (Schulz: „Ma 7a, Vertretung", Sheet mit Krankmeldung/Wasserschaden/MEY), E-40 (Offline-Banner „Keine Verbindung — Stand: …"), Telefon hell auf dunkler Seite.

### Abweichungen / Entscheidungen
- T-W8.1 mit der vollständigen Schule: der Greedy-Solver lässt 18 Kunst-/Sport-Stunden offen (Fachraum-Engpass) — der Testfall erlaubt „belegt oder offen", README sagt es dazu. Kein Eingriff in `src/mock/logic/solver.ts`.
- demo/README: nach T-W1.3 „Meyer wieder aktivieren" ergänzt — sonst fehlt Meyer im Ereignisformular (nur aktive Lehrkräfte), und W-5 ist nicht mehr durchführbar.
- Vertretungsoptionen zeigen bei Fächern ohne Raumanforderung an jedem Raum „passend" (Mock: `matchesRequirement = true`); belassen, in README erklärt.
- `vite build` Chunk-Warnung (> 500 kB) bleibt: `vite-plugin-singlefile` braucht ein Bundle ohne Code-Splitting; für den echten Build wäre `React.lazy` je Route möglich (nicht gemacht — Team-Entscheidung, Bundle 329 kB gzip ist für ein Intranet-Werkzeug tragbar).

### Ungeprüft
- Drag-and-Drop mit echter Maus (Klick-Klick ist belegt), Druckansicht, Entfall-/Verlegen-Dialog, Zusatzstunde, Konten-/Passwort-/Ferien-Dialoge (nur Typen/Build/Vitest), Motion in Bewegung, Sa-Spalte, `validFrom`/„nur Klasse X" (Kann), FAILED-Zustand (Mock erzeugt ihn nie), Safari/Firefox (nur Chrome headless), echter HTTP-Client (kein Backend), Doppelklick auf die Datei im Finder (nur `file://` per Chrome headless).

## Nacharbeit 04.09. abends — Codex-Review (GPT-5.4) eingearbeitet

Acht Befunde aus einer read-only Prüfung durch ein zweites Modell. Sieben umgesetzt, einer teilweise verworfen (Nr. 5).

| Nr. | Schwere | Erledigt |
|---|---|---|
| 1 Annehmen ohne informierte Bestätigung | hoch | ja — `AcceptDialog` lädt beim Öffnen `GET /api/exceptions?from=heute&to=heute+365`, nennt die verfallenden Ausnahmen **vor** dem Klick und sperrt „Annehmen" (`FormDialog.submitDisabled`) bis das Häkchen gesetzt ist. Solange die Abfrage läuft, ist „Annehmen" gesperrt; schlägt sie fehl, wird trotzdem nachgefragt (Text: „bleibt ungeprüft"). Kein neuer Endpunkt, keine Vertragsänderung. |
| 2 Wettlauf Polling ↔ Entwurfs-Mutation | hoch | ja — `MutationOpts.cancel`; alle acht Entwurfs-Hooks tragen `{ cancel: [qk.draft] }`, `useApiMutation` ruft `cancelQueryKeys` in `onMutate`. Test `src/api/draft-race.test.ts` zeigt beide Richtungen (ohne Abbruch gewinnt die alte Antwort, mit Abbruch die neue). |
| 3 `acceptDraft` ohne `warnings` im Typ | mittel | ja — `AcceptDraftResponse extends TimetableDto { warnings: string[] }` in `types.ts`, im `Api`-Interface, im Mock (immer ein Array) und im Dialog; der Cast ist weg. |
| 4 ARIA-Radios ohne Pfeiltasten | mittel | ja — es sind Umschalter (Auswahl ist abwählbar, Lehrkraft und/oder Raum): `aria-pressed` statt `role="radio"`, `role="group"` statt `radiogroup`. |
| 5 `role="button"` an jeder Kachel | mittel | teilweise — die Behauptung „Plan- und Lehrkraftsichten" stimmt nicht: `PlanPage`/`MyPlanPage`/`DraftPage` geben `onEntryClick`, jeder Klick öffnet ein Detailfenster (der `isLesson`-Zweig dient nur der Typverengung, Wochendaten sind immer `EntryDto`). Echt betroffen sind das Raster der **Übersicht** (`StartPage` übergibt kein `onEntryClick`) und die Ghost-Kopie im DragOverlay. Fix daher in `LessonTile`/`MiniTile`: `role`/`tabIndex` nur bei `onClick || drag`, sonst `role="group"` ohne Tabstopp. Neuer Schritt im Klick-Durchlauf prüft es. |
| 6 „Close" in `components/ui` | mittel | ja — `dialog.tsx`, `sheet.tsx`, `sidebar.tsx`, `breadcrumb.tsx` (Schließen, Mehr, Seitenleiste umschalten, Navigationspfad). Damit ist `components/ui/*` nicht mehr unverändert. |
| 7 Platzhalter-Routen | mittel | ja — `routes/placeholders.tsx` und `pages/PlaceholderPage.tsx` nach `~/.Trash/webapp-placeholders-20260904/`; `app/router.tsx` sammelt nur noch w2..w5, unbekannte Pfade landen auf `*`. Alle 15 Platzhalterpfade waren durch echte Routen gedeckt. |
| 8 roher `SyntaxError` bei kaputtem 200-JSON | mittel | ja — `client.ts` fängt `JSON.parse` und wirft ein deutsches ProblemDetail (`urn:pushnpull:problem:invalid-response`). |

Nebenbefund (nicht von Codex): `scripts/klickpfad.mjs` benutzte `new URL(...).pathname` — bei einem Pfad mit Leerzeichen legte das Skript die Screenshots unter `~/Desktop/Software%20Projekt/` an. Jetzt `fileURLToPath()`; das Streuverzeichnis liegt im Papierkorb.

### Belege (04.09. abends, gemessen)
- `npx tsc --noEmit` und `npx tsc -p tsconfig.app.json --noEmit` → exit 0. `npm run lint` → 0 Fehler, 0 Warnungen. `npx vitest run` → **27/27** (25 alt + 2 neu).
- `npm run build` → `dist/assets/index-BpWCwgRy.js` 1 101,04 kB (gzip 328,74 kB), CSS 130,91 kB; Chunk-Warnung > 500 kB bleibt (siehe oben).
- `npm run build:demo` → `demo/webapp-demo.html` **1 555 510 Byte (1,48 MB, gzip 578,82 kB)**.
- Klick-Durchlauf `node scripts/klickpfad.mjs http://localhost:4176` gegen einen Mock-Preview (`VITE_MOCK_TODAY=2026-09-21`, Latenz 0): **41/41 Schritte ok, 0 Seitenfehler, 0 console.error**. Zwei Schritte sind neu: „Übersicht: Raster ohne Klickziel hat keine Schaltflächen-Kacheln" und „Annehmen fragt vorher nach, wenn Ausnahmen ab heute verfallen" (Dialog zeigt den Hinweis, „Annehmen" ist gesperrt, nach dem Häkchen freigegeben; Screenshot `26-annehmen-rueckfrage.png`).
- Für den Durchlauf wurde `playwright-core` mit `npm i --no-save` installiert; `package.json` und `package-lock.json` sind byteweise unverändert (geprüft mit `diff`).

### Ungeprüft (zusätzlich)
- Das Verhalten gegen das echte Backend: ob `POST /api/draft/accept` `warnings[]` wirklich immer als Array liefert, und ob `GET /api/exceptions` dieselbe Menge zählt wie der Server für seine Warnung. Weicht das Backend ab, gewinnt das Backend.
- Screenshots in `demo/screenshots/` wurden **nicht** neu erzeugt (an den abgebildeten Seiten hat sich optisch nichts geändert; der neue Dialog kommt in keiner der 19 Routen-Aufnahmen vor).

## Frontend-Phase · echtes Backend (05.09.2026, Vollausbau)

Ziel: `VITE_MOCK=false` gegen den Compose-Stack (`deployment/`, Backend über nginx unter `/api`). Vertrag gewinnt (`api-contract/openapi.yaml` v1, `docs/VERTRAG.md`).

### Pakete
- keine Änderung an `package.json`/`package-lock.json` (mit `diff` gegen den Starter geprüft). Für den Browser-Durchlauf: `npm i --no-save playwright@1.60.0` (Chrome aus `/Applications`, `channel: 'chrome'`, kein Browser-Download).

### Client-Typen ↔ Vertrag (additiv, `src/api/types.ts`, `client.ts`, `hooks.ts`)
- `Api.deleteEvent(id)` + `httpApi.deleteEvent` + `useDeleteEvent` (Vertrag V-6; die Oberfläche hat noch keinen Knopf dafür).
- `ProblemDetail.instance?: string` (steht im Vertrag, fehlte in der Abschrift).
- `TeacherOption.substitutionsThisWeek` entfernt (Belastungshinweis am 04.09. gestrichen, Vertrag V-5); `OptionsPanel` zeigt die Zeile nicht mehr.
- Sonst feldgenau gleich — mit einem Skript über `js-yaml` alle 63 Operationen und Schemas gegen `types.ts`/`client.ts` abgeglichen (Scratchpad `dump-openapi.mjs`).

### Attrappe nachgezogen, wo der Server abweicht (Vertrag D-9, R-M1, R-M2, PL-4, V-7, V-11)
- `PATCH /draft/lessons/{id}` auf eine offene Stunde ohne `roomId` → 400 `roomId` „Eine eingeplante Stunde braucht Zeitfenster und Raum" (vorher: erster aktiver Raum geraten).
- Zweite Ausnahme für dieselbe Stunde am selben Tag → 409 `exception-exists` (vorher: ersetzt). Slug in `problems.ts`, `preset-missing` raus.
- `POST /rules/reset`: alles aktiv, nur schulweite Lehrkraft-Zeilen aus (wie Seed V2); `PUT /config` prüft `state` gegen die 16 Kürzel (400 am Feld `state`).
- `POST /rules` mit vorhandenem `(type, scope, grade)` → 409 `data-exists` mit dem Servertext.
- Passwörter mindestens 8 Zeichen (`createUser`, `setUserPassword`, `changeMyPassword`); Hinweise in `UserDialog`, `UsersTable`, `SettingsPage` auf „8 Zeichen".
- `POST /sample-data`: Lehrkraft-Konten werden gelöscht, keine neuen angelegt; Planungs-Konten bleiben (`mockApi.reset('FULL')` für die Demo unverändert mit Demo-Konten).
- `deleteEvent` in der Attrappe: Ereignis weg, Zusatzstunde (`ADDED`) mit, andere Ausnahmen verlieren nur den Anlass.
- Tests angepasst (`mock.test.ts`, `w2-flows.test.ts`): 28/28 grün.
- **Nicht** nachgezogen (bewusst, kleiner Nutzen): Folgekonflikte einer Vertretung prüft die Attrappe weiter nur auf Doppelbelegung (Server: voller `ConflictChecker`, SU-7); die 400-Fälle aus SU-19/SU-20 (falscher Wochentag, Ferientag, unbekannte Kennungen) kennt die Attrappe nicht.

### Browser-Durchlauf (`scripts/abnahme.spec.ts`, `scripts/playwright.config.ts`)
- 42 Schritte in Serie auf EINER Seite (Sitzung nur im Speicher → Navigation per pushState), Zeilen I-3 … S-4 der `docs/ABNAHME-CHECKLISTE.md`; Screenshots nach `docs/smoke/`, Protokoll `docs/smoke/PROTOKOLL.md`.
- Aufruf: leere Datenbank, dann `cd frontend && npx playwright test -c scripts/playwright.config.ts` (Basis `ABNAHME_BASE`, Standard nginx `http://localhost:1500`; Passwort `ABNAHME_ADMIN_PW`).
- Fallen, alle gemessen: Vite-Dev-Server + Dateiberührung = page reload = Sitzung weg (deshalb gegen nginx laufen); Sidebar ohne `navigation`-Rolle; `FormDialog`-Titel doppelt (sr-only); Cache zeigt nach „Ersetzen" den alten Wochenstand (→ `expect.poll`); `/verlauf` erst nach gefilterter Antwort prüfen. Einzelheiten in `07-Konzept/workflows/lernnotizen.md` (05.09., Frontend-Phase).
- D-4 versucht echtes Drag-and-Drop (`page.mouse` in Schritten) und fällt auf Klick-Klick zurück; im Beleglauf gegen nginx griff der Rückfall (steht so im Protokoll), gegen den Dev-Server kam der Zug an — Ziehen mit der Maus bleibt Handprüfung.

### Belege (05.09., gemessen)
- `npx tsc -p tsconfig.app.json --noEmit` → exit 0 · `npx eslint .` → 0 Fehler, 0 Warnungen · `npx vitest run` → 28/28 · `npm run build` → `✓ built`, JS 1 126 kB (gzip 332 kB), keine Lade-URL im Bundle · `npm run build:demo` → `demo/webapp-demo.html` 1,51 MB (Mock-Modus erhalten).
- Browser-Durchlauf gegen `http://localhost:1500` (nginx + Backend, leere Datenbank): **42/42 Schritte ok, 0 Seitenfehler**, 55 Screenshots + `docs/smoke/PROTOKOLL.md`. T-W8.1 auf dem vollen Bestand: 357 von 360 Stunden belegt, 3 offen, 0 harte Konflikte, Ergebnis `0hard/…` nach 10 s Rechenzeit.
- `package.json` und `package-lock.json` byteweise wie im Starter (`diff -q`).

### Geltungsbeginn und Sitzung (05.09., Nachmittag)
- **Plan gilt ab Annahmetag (Befund F-8).** Nach „Annehmen" am Samstag zeigte `/plan` die Woche von heute (Mo–Fr davor) und damit den vorherigen, leeren Stand — es sah aus, als sei nichts gespeichert (`GET /timetable/week?date=2026-09-07` → 360 Einträge, `date=2026-09-04` → 0). Neu: `firstWeekMonday`/`planValidFrom` in `src/lib/dates.ts`, Regel in `src/lib/plan-validity.ts`, Standardwoche = max(Woche von heute, erste Woche ab „gilt ab"), `ValidFromBanner` mit Sprungknopf, „gilt ab …" im Untertitel und in der Erfolgsmeldung, Sprung nach dem Annehmen auf `?woche=<erste gültige Woche>`. Das Banner erscheint nur, wenn die Woche leer ist UND der Plan Stunden hat UND ihr Freitag vor dem Geltungsbeginn liegt — in der Attrappe (Plan ohne `validFrom` gilt immer) also nie. `/mein-plan` bleibt unverändert: die Rolle TEACHER darf `GET /api/timetable` nicht lesen (SecurityConfig `/api/**` → `hasRole('PLANNING')`), der Geltungsbeginn ist dort nicht bekannt.
- **F-5a: Sitzung im localStorage, Token 30 Tage** (Entscheidung Nutzer 05.09., weicht von F-5 ab, siehe `docs/VERTRAG.md`). `src/api/session.ts` legt Token und Principal unter `stundenplan.session` ab, `restoreSession` prüft beim Start mit `GET /api/me`; 401/Netzfehler löschen den Eintrag, Abmelden und jede 401 im Client ebenso. Backend: `app.auth.token-ttl` (Standard `P30D`) statt fester 12 Stunden. Die Attrappe erkennt ein wiederhergestelltes Mock-Token am Benutzernamen im Token (`mock-<name>-…`), damit die Demo nach F5 angemeldet bleibt.

### Zweiter Korrekturlauf des Audits (05.09., Abend) — PFLICHTENHEFT-9, FRONTEND-6, FRONTEND-8

- **Verfallende Vertretungen kommen jetzt vom Server** (Befund PFLICHTENHEFT-9). `DraftDto` trägt das neue
  Pflichtfeld `expiringExceptions`: wie viele datierte Vertretungen das Annehmen dieses Entwurfs verfallen
  ließe — nur die, deren Stunde er verschiebt, umräumt oder streicht. Vertretungen an unverändert
  übernommenen Stunden wandern beim Annehmen mit (Server hängt sie um). Folgen im Frontend:
  `AcceptDialog` zählt nicht mehr selbst über `GET /api/exceptions`, sondern nennt dieselbe Zahl wie der
  Server; ohne verfallende Vertretungen entfällt das Häkchen ganz. `useExceptions` hat damit keinen
  Aufrufer mehr und ist entfernt — die Query-Schlüssel bleiben stehen (Kommentar in `query-keys.ts`),
  damit eine spätere Liste ihre Invalidierung nicht still verliert. Attrappe nachgezogen: `sameLessons`
  und `expiringCount` in `mock-api.ts` spiegeln `DraftService.sameLessons` Feld für Feld.
- **Komponententests** (Befund FRONTEND-6, Karte T-509 umgeschrieben). Neu: `@testing-library/react`
  16.3.0, `@testing-library/user-event` 14.6.1, `jsdom` 26.1.0. `vitest.config.ts` nimmt `*.test.tsx` auf;
  jede solche Datei beginnt mit `// @vitest-environment jsdom` (node bleibt die Vorgabe, jsdom kostet
  Startzeit). `src/test-setup.ts` legt nach, was jsdom fehlt und Radix beim Öffnen ruft (`matchMedia`,
  `hasPointerCapture`, `scrollIntoView`, `ResizeObserver`) und räumt nach jedem Fall auf.
  `src/test-utils.tsx` rendert eine ganze Seite: `renderPage(ui, { route, username })` stellt
  MemoryRouter, QueryClient und eine echte Anmeldung gegen die Attrappe.
  Drei Testdateien: `LessonTile.test.tsx` (jeder `kind`-Wert trägt Badge oder Text, nie nur Farbe),
  `AcceptDialog.test.tsx` (Sperre bis zum Häkchen, in beide Richtungen), `ReadOnlyWeekPage.test.tsx`
  (beide Nur-Lese-Seiten, Tag-Umschalter nur bei `/mein-plan`). 69 → **89 Fälle**.
  Falle, gemessen: `MemoryRouter` muss aus `react-router-dom` kommen — die App importiert von dort, und
  aus `react-router` kommt eine zweite Modulinstanz („useLocation() may be used only in the context of a
  <Router>", obwohl der Router da ist).
- **`ReadOnlyWeekPage`** (Rest von Befund FRONTEND-8). `/mein-plan` und `/klassenplan` teilen sich den
  Rumpf: Kopfzeile, Druckzeile, `ChangedTodayBar`, Raster bzw. Tagesliste, Legende, Detail-Popover.
  `MyPlanPage` und `ClassPlanPage` sind nur noch Hook-Auswahl und Beschriftung. Der Tag-Umschalter ist an
  `dayView` gebunden und bleibt bei `/mein-plan`: das Musskriterium nennt ihn für die Lehrkraft, nicht für
  die Klassensicht (die zudem unter Rückfrage R5 steht).

**Belege (05.09., 22 Uhr, alle drei Stände):** `tsc` exit 0 · ESLint 0/0 · `vitest run` **89/89** (11
Dateien) · `vite build` Hauptbündel **485 081 Byte** (gzip 152 776) · `build:demo` → `webapp-demo.html`
**1 751 925 Byte**, Attrappe vollständig (35 × „Herbstferien", 32 × „Sommerferien").
`plattform/frontend/src`, `starter/frontend/src` und `web/src` sind byte-gleich (`diff -rq` leer).
