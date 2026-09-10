# API-Vertrag

`openapi.yaml` (OpenAPI 3.1) ist die **einzige Wahrheit** der REST-API. Backend, Web-App und Android-App
richten sich danach; wo Code und Vertrag auseinanderlaufen, gewinnt der Vertrag.

Stand: **v1** (`info.version: 1.0.0`, 05.09.2026, Vollausbau) — v0 (Erreichbarkeit, Anmeldung, Konten, Grunddaten,
Stundenraster, Regelwerk, Konfiguration, Beispieldaten; 39 Operationen) plus additiv: aktiver Plan, Wochensichten in
vier Sichten, eigene Woche der Lehrkraft, Entwurf mit Handaufbau, Berechnung und Annehmen, Ereignisse, offene Stunden,
Vertretungsoptionen, datierte Ausnahmen, Änderungsverlauf, unterrichtsfreie Zeiträume. **63 Operationen.**
Was sich gegen v0 geändert hat und wie ein Modul den Vertrag erweitern darf: `docs/VERTRAG.md`.

## Drei Befehle

```bash
npm ci          # einmalig
npm run lint    # Redocly: muss 0 Fehler melden
npm run mock    # Prism auf http://localhost:1501
```

Ohne Node geht der Mock auch aus dem Container (Rolle 7 startet ihn so im Compose-Stack):

```bash
cd ../deployment && docker compose up -d mock     # gleicher Port 1501 — nie beides gleichzeitig
```

Probe, ob der Mock läuft:

```bash
curl -s localhost:1501/api/health
# {"status":"UP","schoolName":"Gymnasium Musterstadt"}
```

## Wie man den Vertrag ändert

1. `openapi.yaml` im **selben Merge Request** wie den Code ändern, Label `api-change` setzen.
2. `npm run lint` — 0 Fehler.
3. `npm run mock` starten und die geänderte Antwort einmal mit `curl` holen. Jede 2xx-Antwort **muss** ein
   `example` haben; davon lebt der Mock, und ohne Beispiel liefert Prism nur generierte Platzhalter.
4. Erst dann den Controller bauen. Reviewer: Rolle 4 (API) plus die betroffene Frontend-Rolle, dazu ein
   Post im Mattermost.
5. `frontend/src/api/types.ts` ist die TypeScript-Abschrift des Vertrags und wird im selben MR nachgezogen.

**Freeze ab Freitag 12.09.: nur noch additiv.** Neue Felder und neue Endpunkte sind erlaubt, Umbenennen und
Entfernen nicht. Das federt die Anforderungsänderung von adesso am 21.–23.09. ab: ein zusätzliches Feld
bricht keinen Client, ein umbenanntes bricht alle drei.

## Konventionen (gelten für v1 genauso)

- Basis `/api`; die Pfade im Vertrag tragen sie ausgeschrieben (`/api/teachers`), `servers` bleibt `/`.
  Grund: Prism blendet einen Server-Pfad aus — stünde `/api` nur im Server, beantwortete der Mock
  `/health` statt `/api/health`. Im Vertrag steht bewusst keine Adresse; das Ziel wählen nginx bzw. der
  Vite-Proxy.
- JSON, Feldnamen camelCase, Bezeichner englisch. Alle Texte für Menschen (`title`, `detail`, `message`,
  `label`) deutsch in Schulsprache.
- Datum `yyyy-MM-dd`, Uhrzeit `HH:mm`, Wochentag `MO`…`SA`, IDs `integer`/`int64`.
- Listen unpaginiert (Schulgröße); erst `/changelog` in v1 bekommt Seiten.
- DTOs flach mit ID **und** Anzeigetext (`teacherId` und `teacherShort`), damit Web und App nichts nachladen.
- Fehler immer `application/problem+json` nach RFC 9457, `type: urn:pushnpull:problem:<slug>`. Slugs:
  `validation`, `unauthenticated`, `forbidden`, `not-found`, `in-use`, `draft-exists`, `solving`,
  `master-data-incomplete`, `pinned-contradiction`, `data-exists`, `internal`.
- **Konflikte und Warnungen sind Daten, keine Fehler**: `warnings[]` und `conflicts[]` stehen in
  200/201-Antworten. Speichern wird nie blockiert.
- `x-role` an jeder Operation ist eine Lesehilfe (`ANY`, `PLANNING`, `TEACHER`); durchgesetzt wird die
  Rolle im Backend (`SecurityConfig`, E-45), nicht vom Vertrag.

## Beispielwerte

Die `example`-Blöcke sind der Testdatenbestand des Pflichtenhefts, damit Mock, Attrappe der Web-App und
Testfälle dieselben Zahlen zeigen:

- Lehrkräfte Meyer (MEY, Deputat 25, Mathematik + Physik), Schulz (SCZ, Deputat 2, Mathematik,
  Freitag 5./6. Stunde gesperrt = Zeitfenster 34 und 35), Weber (WEB, Deputat 25, Deutsch).
- Klassen 7a, 8b, 9c; Räume R101, R204 (Klassenraum), P1 (Fachraum Physik).
- Stundenraster Mo–Fr mit sieben Zeitfenstern je Tag: sechs Unterrichtsstunden und die große Pause an
  Position 3. IDs laufen 1–35 (Montag 1–7, Dienstag 8–14, …, Freitag 29–35).
- Regelwerk: Klasse max. 6 Stunden/Tag und max. 30 Stunden/Woche (aktiv), Lehrkraft 6/28 (inaktiv),
  keine Stunden in Pausen. Hinweis: der Seed V2 hat für SH Lehrkraft-Woche **26** und die Pausenregel **hart**
  (Recherche 04.09.); die Beispiele zeigen noch den v0-Stand — Beispiel, kein Seed (`docs/VERTRAG.md` D-10).
- v1-Beispiele: Woche 21.–25.09.2026 nach dem Leitszenario (Ausfall Meyer, Sperrung R101, Vertretung durch
  Schulz in R204, verlegte Dienstagsstunde, Zusatzstunde 9c am Donnerstag) — alle sieben `kind`-Werte.

## Grenzen des Mocks

Prism ist eine Attrappe, kein Server. Wer das verwechselt, sucht Fehler an der falschen Stelle:

- **Statische Beispiele, kein Zustand.** Ein `POST /api/teachers` legt nichts an; das nächste
  `GET /api/teachers` liefert wieder Meyer, Schulz, Weber. Für einen Durchlauf mit Zustand nimmt man die
  eingebaute Attrappe der Web-App (`npm run dev:mock`) oder das echte Backend.
- **Fehlerfälle nur auf Ansage.** Prism antwortet immer mit dem ersten 2xx. Einen Fehlerfall holt man mit
  dem Header `Prefer: code=<status>`:
  ```bash
  curl -s -H 'Prefer: code=409' -X DELETE localhost:1501/api/teachers/1
  # {"type":"urn:pushnpull:problem:in-use", …}
  ```
- **Sicherheit wird geprüft, Rollen nicht.** Ohne `Authorization: Bearer <irgendwas>` antwortet Prism auf
  geschützte Pfade mit 401 — das ist erwünscht und deckt vergessene Header auf. Welche *Rolle* ein Token
  hat, prüft nur das Backend; `GET /api/teachers` mit Lehrkraft-Token gibt beim Mock 200, am Server 403.
- **Keine Fachlogik.** Konflikte, Warnungen und berechnete Felder (`plannedHours`) sind im Beispiel
  eingefroren.

## Wie ein Modul den Vertrag erweitert (Regel in `docs/VERTRAG.md` Abschnitt 3)

Muster ist jede vorhandene Ressource. Für einen neuen Endpunkt:

1. `paths` um den Pfad ergänzen — mit `/api`-Präfix, `tags`, `operationId` (camelCase, **gleich dem
   Methodennamen in `frontend/src/api/client.ts`**), `summary`, `description`, `x-role`.
2. Antworten anlegen: `2xx` mit `schema` **und** `example`, dazu die passenden Fehler als
   `$ref: '#/components/responses/…'` (`Validation400`, `Unauthenticated401`, `Forbidden403`,
   `NotFound404`, `Conflict409`).
3. Neue Typen unter `components/schemas` — Feldnamen und Typen 1:1 aus `frontend/src/api/types.ts`.
   `ConflictDto`, `ConflictObject`, `Severity`, `Weekday` und `ProblemDetail` stehen schon bereit.
4. `npm run lint`, dann `npm run mock` und den neuen Pfad einmal mit `curl` holen.
5. Beispielwerte aus dem Testdatenbestand nach dem Leitszenario nehmen (Kommentarblock vor `/api/timetable` in
   `openapi.yaml`; Woche vom 21.09.2026; IDs wie dort).
6. Nur additiv (Regel 3.1 in `docs/VERTRAG.md`); Enum-Werte zählen als brechend.

API-Freeze Fr 11.09.; der Stand bekommt den Tag `contract-v1`.

## Prüfung dieses Stands

| Prüfung | Befehl | Ergebnis am 04.09. |
|---|---|---|
| Lint | `scripts/ci-contract-lint.sh` | 0 Fehler, 1 Warnung (`operation-4xx-response` an `GET /api/health` — ohne Token, kein 4xx möglich) |
| Mock lokal | `npm run mock`, dann `curl localhost:1501/api/health` | Health-Beispiel |
| Alle 39 Operationen (v0) | je ein `curl` mit `Authorization: Bearer …` | jede antwortet mit ihrem Beispiel (200/201/204) |
| v1 (05.09.): Lint | `redocly lint openapi.yaml` | 0 Fehler, dieselbe eine Warnung |
| v1 (05.09.): 18 Aufrufe gegen Prism 5.14.2 (Port 1509) | `GET /api/timetable/week`, `/api/me/timetable/week`, `/api/draft`, `POST /api/draft/lessons`, `/solve`, `/accept`, Events, Pending, Options, Exceptions, Changelog, Holidays, je ein `Prefer: code=409` | alle sieben `kind`-Werte über die zwei Wochen-Endpunkte; 409-Beispiele `draft-exists`, `master-data-incomplete`, `exception-exists`; Belegtabelle in `docs/VERTRAG.md` Abschnitt 9 |
| Fehlerfall | `curl -H 'Prefer: code=409' -X DELETE …/api/teachers/1` | ProblemDetail `in-use` |
| Mock im Container | `docker run --rm --platform linux/amd64 -v "$PWD/api-contract/openapi.yaml":/contract/openapi.yaml:ro -p 1502:4010 stoplight/prism:5.14.2 mock -m false -h 0.0.0.0 -p 4010 /contract/openapi.yaml` | dieselbe Antwort wie lokal |

**Version und `-m false` sind Pflicht, nicht Geschmack (Korrektur 04.09.).** Mit dem beweglichen Tag `stoplight/prism:5`
(= 5.15.10) und ohne `-m false` bricht der Container beim Mehrprozess-Start ab — `TypeError: Cannot read properties of
undefined (reading 'isPrimary')` — und startet endlos neu; der Port antwortet nie. `deployment/docker-compose.yml` ist
darum auf `stoplight/prism:5.14.2` festgelegt, dieselbe Version wie in `package.json`. Das Bild gibt es nur für
`linux/amd64`; auf Apple Silicon läuft es unter Rosetta.
