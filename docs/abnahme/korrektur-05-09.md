# Phase Korrektur — Belege (05.09.)

Eingearbeitet werden die acht Befunde aus `docs/ABNAHME-CHECKLISTE.md` Abschnitt 5.4. Diese Datei
nennt je Befund den Befehl und die **Ausgabe**, nicht nur das Ergebnis. Prüfer und Bearbeiter:
Claude (Subagent Korrektur), 08:15–09:00.

Werkzeuglage: kein JDK, kein Gradle, kein Android-SDK auf dem Rechner — alles im Container.

---

## Zusammenfassung

| Befund | Schwere | Ergebnis |
|---|---|---|
| 1 Vollausbau nicht committet | hoch | **nicht bearbeitet** — Commits sind Philipps Entscheidung (Auftragsgrenze) |
| 2 Android-App fehlt | hoch | **teilweise** — Gerüst baut jetzt (gemessen); die App-Musskriterien bleiben Bauphase W3 |
| 3 `Using generated security password` im Log | mittel | **erledigt**, gemessen: 0 Treffer |
| 4 Platzhalter-Kennwort `bitte-aendern` | mittel | **halb erledigt** — WARN im Log gemessen; Eintrag in `testdurchlauf.tex` ist fremdes Repo |
| 5 `testdurchlauf.tex` weicht ab | mittel | **vorbereitet** — Ersatztexte in `docs/PFLICHTENHEFT-NACHTRAG.md`, fremdes Repo |
| 6 Annehmen übernimmt offene Stunden stumm | mittel | **erledigt**, gemessen in API und Oberfläche |
| 7 Sitzung nur im Seitenspeicher (F5) | mittel | **bewusst nicht geändert** — Vertragsänderung, gehört dem Auftraggeber; Hinweistext vorbereitet |
| 8 Attrappe `mock` im Produktions-Compose | mittel | **erledigt**, gemessen: drei Dienste statt vier |

---

## Befund 3 — kein Zufallspasswort mehr im Log

**Geändert:** `backend/src/main/java/de/wsp3_1/BackendApplication.java`

```java
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
```

Der Klassenname liegt in Spring Boot 4 **nicht** mehr unter
`org.springframework.boot.autoconfigure.security.servlet`, sondern unter
`org.springframework.boot.security.autoconfigure` (im Modul `spring-boot-security`). Nachgesehen im
Jar des Gradle-Caches, nicht geraten:

```
$ docker run --rm=true -v stundenplan-gradle:/g python:3.12-slim python -c "…zipfile…"
org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.class
```

**Gemessen** am frisch gebauten Stack (`docker compose -p korrektur up -d --build`):

```
$ docker compose -p korrektur logs backend | grep -c "Using generated security password"
0
```

Vorher stand die Zeile in jedem Start-Log (`ABN/I-2-compose-ps-und-log.txt` des Laufs vom 05.09.).

## Befund 4 — Warnung bei unverändertem Beispielkennwort

**Geändert:** `backend/src/main/java/de/wsp3_1/auth/domain/AdminBootstrap.java` — wird das Konto
`admin` mit dem Platzhalter `bitte-aendern` aus `deployment/.env.example` angelegt, folgt eine
WARN-Zeile. Der Start wird **nicht** abgebrochen: das würde den dokumentierten Weg
`cp -n .env.example .env && docker compose up -d` zerbrechen, und genau den geht die Testperson.
Das Kennwort selbst steht weiterhin in keiner Log-Zeile.

**Gemessen** (Stack mit `ADMIN_INITIAL_PASSWORD=bitte-aendern` gestartet):

```
backend-1  | 2026-09-05T08:42:12.873+02:00  WARN 1 --- [stundenplanung] [main]
  de.wsp3_1.auth.domain.AdminBootstrap : ACHTUNG: Das Konto "admin" wurde mit dem unveraenderten
  Beispielwert aus deployment/.env.example angelegt. Der steht im Repository und ist damit
  oeffentlich bekannt. Bitte das Passwort sofort in der Anwendung aendern (Einstellungen > Passwort)
  oder mit einem eigenen ADMIN_INITIAL_PASSWORD in deployment/.env neu aufsetzen
  (docker compose down -v loescht dabei alle Daten).
```

Die zweite Hälfte des Befundes — das Kennwort in der Tabelle `tab:abgabeangaben` von
`testdurchlauf.tex` — liegt im Repository `SoSe2026_WSP3_1` und wird hier nicht angefasst; der
Vorschlag steht in `docs/PFLICHTENHEFT-NACHTRAG.md` unter P-5.

## Befund 6 — Annehmen nennt die offenen Stunden

**Geändert:**
`backend/.../planning/domain/DraftService.java` (Antwort `warnings[]`),
`frontend/src/components/draft/AcceptDialog.tsx` + `pages/draft/DraftPage.tsx` (Hinweis im Dialog),
`frontend/src/mock/mock-api.ts` (Attrappe nachgezogen, Regel 3.9),
`api-contract/openapi.yaml` (Beschreibung von `accept` und von `warnings[]`).

Offene Stunden werden **nicht** verworfen: ein angenommener Plan darf sie enthalten (PL-11), und sie
zu löschen hieße, Wochenstunden stillschweigend fallen zu lassen. Stattdessen sagen es beide Seiten.

**Gemessen gegen den Stack** (`scratchpad/check-accept.py`, Beispielbestand geladen):

```
sample-data: 200 {'classes': 12, 'teachers': 24, 'rooms': 18}
aktiver Plan: 360 Stunden, davon offen 3
draft anlegen: 201
  offene Stunden im Entwurf (openLessons): [{"classShort":"6b","subjectShort":"Ch","missing":1},
                                            {"classShort":"7b","subjectShort":"Mu","missing":1},
                                            {"classShort":"8b","subjectShort":"Ch","missing":1}]
  Stunden ohne Zeitfenster: 3
accept: 200
  warnings: ["3 Stunden bleiben unverplant: sie stehen im aktiven Plan, aber in keiner Woche."]
```

**Gemessen im Browser** (Chrome headless 1440×900 gegen nginx `:1520`), Dialogtext:

> Entwurf annehmen | Der Entwurf wird zum aktiven Plan. Der bisherige Plan wird archiviert;
> Lehrkräfte sehen die Änderung beim nächsten Abruf. | **3 Stunden sind noch offen und bleiben
> unverplant. Sie stehen danach im aktiven Plan, aber in keiner Woche. Vorher schließen: berechnen
> lassen oder von Hand einplanen.** | Erst ab einem Datum gültig | Abbrechen | Annehmen

Screenshot: `docs/smoke/K-annehmen-offene-stunden.png`.

## Befund 8 — Attrappe raus aus dem Produktions-Compose

**Geändert:** der Dienst `mock` steht jetzt in `deployment/docker-compose.dev.yml`; aus
`deployment/docker-compose.yml` ist er verschwunden (mit Verweis an seiner alten Stelle). Nachgezogen:
`README.md`, `deployment/README-VM.md` (vier Stellen), `docs/ABNAHME-CHECKLISTE.md` Zeile I-2.
Start weiterhin möglich mit
`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mock`.

**Gemessen** (`docker compose -p korrektur up -d --build`, danach `ps`):

```
NAME                  IMAGE                      SERVICE   STATUS
korrektur-backend-1   korrektur/backend:latest   backend   Up About a minute (healthy)   8080/tcp
korrektur-db-1        postgres:16-alpine         db        Up About a minute (healthy)   5432/tcp
korrektur-web-1       korrektur/web:latest       web       Up 46 seconds   0.0.0.0:1520->8080/tcp
```

Drei Dienste, nach außen nur `web`. Danach:

```
$ curl -s -o /dev/null -w '%{http_code}\n' http://localhost:1520/login
200
$ curl -s http://localhost:1520/api/health
{"status":"UP","schoolName":"Gymnasium Musterstadt"}
```

## Befund 2 — Android: das Gerüst baut jetzt

Die App selbst ist nicht gebaut (das ist die Bauphase android, W3 nach E-30). Was hier passiert ist:
der bis dahin nie ausgeführte Build wurde ausgeführt — und er scheiterte.

**Erster Lauf, mit dem Stand des Gerüsts** (`composeBom = "2026.08.00"`):

```
BUILD FAILED in 4m 48s
 14. Dependency 'androidx.compose.ui:ui-graphics-android:1.12.0' requires Android Gradle plugin
     9.1.0 or higher. This build currently uses Android Gradle plugin 8.13.2.
 13. … requires libraries and applications that depend on it to compile against version 37 or later
     of the Android APIs. :app is currently compiled against android-36.
     (18 gleichlautende Meldungen für alle Compose-Artefakte)
```

**Korrektur:** `composeBom` auf `2026.06.01` (zieht Compose 1.11.4 statt 1.12.0). Nachgesehen im
Google-Maven-Verzeichnis, nicht geraten:

```
$ curl -s .../compose-bom/2026.06.01/compose-bom-2026.06.01.pom | grep compose-ui -A1
  <version>1.11.4</version>
```

Nur diese eine Zeile geändert — der Wechsel auf AGP 9 wäre laut README dieses Ordners „nur komplett"
(AGP 9.4.0, Wrapper 9.6, Kotlin 2.4.10, `android.builtInKotlin=false`) und gehört nicht in eine
Korrekturphase.

**Zweiter Lauf:**

```
> Task :app:testDebugUnitTest
> Task :app:assembleRelease
BUILD SUCCESSFUL in 19m 14s
73 actionable tasks: 59 executed, 8 from cache, 6 up-to-date
-rw-r--r-- 1 root root 25708501 Sep  5 06:48 app-release-unsigned.apk
```

Unit-Tests: `tests="3" skipped="0" failures="0" errors="0"` (`ApiFactoryTest`).

Wiederholbar mit dem neuen `scripts/ci-android-build.sh` (Docker, `--platform linux/amd64`, SDK und
Gradle-Ablage in Volumes; erster Lauf 19 min unter Emulation auf Apple Silicon).

**Was das nicht ist:** kein `app/stundenplan.apk`. Das Release-APK ist unsigniert und enthält nur
den Einstellungs-Screen; I-4, I-5, K-1, K-2 und die App-Anteile von T-A1.1, T-A2.1, T-A3.1 bleiben
offen. Der Wert des Laufs liegt woanders: die vier Risiken, die `android-starter/README.md` als
„ungebaut, deshalb offen" führte, sind erledigt — die Versionen lösen sich auf, die
Compose-Signaturen passen, der `kotlin { compilerOptions … }`-Block wird akzeptiert, der JVM-Test
läuft. Und der Fehler, der Rolle 6 am 08.09. einen halben Tag gekostet hätte, ist vorher gefunden.

## Befund 1 — nicht bearbeitet (Auftragsgrenze)

Der Befund stimmt: `git status` zeigt den gesamten Vollausbau als `??`/`M`, kein Commit. Nachgesehen,
ob die Ignorierliste etwas verschluckt, das später fehlen würde:

```
$ git status --porcelain --ignored=matching | grep '^!!' | grep -v node_modules
!! android-starter/.gradle/  !! android-starter/app/build/  !! backend/.gradle/  !! backend/build/
!! demo/  !! deployment/.env  !! deployment/backups/  !! frontend/.agent-out/  !! frontend/.demo-dist/
!! frontend/dist/  !! frontend/test-results/
```

Alles davon ist richtig ignoriert (Baustände, Zugangsdaten, Agentenordner). `deployment/`, `docs/`,
`scripts/`, `api-contract/`, `android-starter/` und `.gitlab-ci.yml` sind **untracked, aber nicht
ignoriert** — ein Commit des Arbeitsbaums nimmt sie mit. Mehr ist hier nicht zu tun: Commits und Push
sind ausdrücklich Philipps Entscheidung.

## Befund 7 — nicht geändert (Vertragsfrage)

`backbone/api.md`, Auth-Fluss 5 sagt wörtlich: „Web: Token nur im Speicher (kein localStorage);
Seite neu laden = neu anmelden." Das ist eine verbindliche Festlegung des Rückgrats, und
`README.md` hält seit dem 05.09. fest, dass eine Änderung die Zustimmung des Auftraggebers braucht.
Der technische Weg ist bekannt und klein (Token in `sessionStorage`, beim Start `GET /api/me` — der
Endpunkt existiert), aber die Entscheidung gehört nicht dem Bearbeiter. Vorbereitet:
`docs/PFLICHTENHEFT-NACHTRAG.md` P-6 mit dem Hinweistext für den Testdurchlauf.

---

## Regressionsläufe nach den Änderungen

| Lauf | Befehl | Ergebnis |
|---|---|---|
| Backend, Ebene 1–4 | `scripts/ci-backend-test.sh` | **BUILD SUCCESSFUL in 7m 11s**, 300 Tests, 0 Fehler, 0 übersprungen |
| Frontend Typen | `npm run typecheck` | ohne Ausgabe = grün |
| Frontend Lint | `npm run lint` | ohne Ausgabe = grün |
| Frontend Tests | `npm test` | 3 Dateien, 28 Tests, alle grün |
| API-Vertrag | `npm run lint` in `api-contract` | „Your API description is valid", 1 Warnung (`operation-4xx-response` bei `/api/health`, vorbestehend) |
| Android | `scripts/ci-android-build.sh` | **BUILD SUCCESSFUL in 19m 14s**, 3 Unit-Tests grün, Release-APK gebaut |
| Stack | `docker compose -p korrektur up -d --build` | drei Dienste `Up`, db und backend `healthy`, `/login` 200, `/api/health` UP |
