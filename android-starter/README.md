# Stundenplan — Android-App

Lesende App für Lehrkräfte: Einstellungen (Serveradresse, Anmeldung), Tagesansicht, Wochenansicht,
Stundendetail, Offline-Stand. Baut auf dem Gerüst vom 05.09. auf und deckt die App-Musskriterien
(A-1, A-2, A-3) sowie die Testfälle T-A1.1, T-A2.1, T-A3.1 ab — gegen den Vertrag
`api-contract/openapi.yaml` und den laufenden Compose-Stack.

Später wird daraus das GitLab-Projekt `android-app`.

## Stand (05.09., nach Einarbeitung der Prüfbefunde)

**Gebaut und getestet im Container, nicht in Android Studio** (auf diesem Rechner gibt es kein JDK und
kein SDK): `scripts/ci-android-build.sh` → `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
Ergebnis: `BUILD SUCCESSFUL in 10m 50s` (warmer Cache, x86-Emulation auf Apple Silicon),
**64 Unit-Tests in 6 Klassen grün** (ApiFactory 6, EntryKindUi 9, WeekView 9, OfflineCache 6,
TimetableRepository 17, TimetableViewModel 17), 0 Fehler, **Lint: „No issues found"**.

Die Läufe davor zur Einordnung: Gerüst früh `BUILD SUCCESSFUL in 19m 14s`; Vollausbau mittags 41m 35s
(kalter Lint), danach 15m 55s mit warmem Cache. Lint erster Lauf: 0 Fehler, 26 Warnungen — 13 Hinweise auf
neuere Versionen (bewusst festgezurrt, in `app/build.gradle.kts` begründet abgeschaltet), Rest korrigiert
(Activity-Label, ungenutzte Ressourcen, Monochrome-Icon, Klartext-Ausnahme mit Begründung; `ObsoleteSdkInt`
bleibt abgeschaltet, weil aapt2 die adaptiven Icons ohne `-v26` nicht findet — gemessen). Die Korrektur der
Prüfbefunde brachte zwei neue Lint-Hinweise, beide behoben: `DataExtractionRules` (zu
`dataExtractionRules` gehört für Android 11 auch `fullBackupContent` → `res/xml/backup_rules.xml`) und
`AutoboxingStateCreation` (die Wischstrecke ist jetzt eine gewöhnliche Variable in der Gestenerkennung,
kein Compose-Zustand — sie wird beim Zeichnen nie gelesen).

**`app/stundenplan.apk`** (Kopie von `app/build/outputs/apk/debug/app-debug.apk`, 33.380.315 Byte) ist mit
dem **Debug-Schlüssel** signiert — installierbar auf Emulator und Gerät (`adb install app/stundenplan.apk`),
**für die Abgabe neu signieren** (Release-Build + eigener Keystore, siehe unten).

**Nicht geprüft, weil dafür ein Gerät oder ein Emulator nötig ist:** wie die Screens aussehen, ob das
Launcher-Icon sauber rendert, das Bedienen von Pull-to-refresh und Bottom Sheet, der Verbindungstest gegen
einen echten Server von einem Telefon aus. Die Logik dahinter ist auf der JVM getestet (unten), die
Serverantworten sind echte Antworten des Stacks vom 05.09. (`app/src/test/.../SampleJson.kt`).

## Bauen

```bash
# im Ordner 08-Build/plattform, nur Docker noetig:
scripts/ci-android-build.sh                                              # Unit-Tests + Debug-APK -> app/stundenplan.apk
scripts/ci-android-build.sh :app:testDebugUnitTest :app:assembleDebug :app:lintDebug   # dazu Lint-Bericht
scripts/ci-android-build.sh :app:assembleRelease                         # unsigniertes Release-APK
```

Das Skript startet einen benannten Container `stundenplan-android-build` (ohne `--rm`; `docker logs` zeigt
den Lauf, der nächste Lauf räumt ihn weg). SDK und Gradle-Ablage liegen in Docker-Volumes (`android-sdk`,
`android-gradle`) oder — mit `ANDROID_SDK_DIR`/`ANDROID_GRADLE_DIR` — in Ordnern. Erster Lauf ohne Cache
rund 19 Minuten (Apple Silicon, x86-Emulation für aapt2/d8), mit warmem Cache deutlich kürzer.

Berichte: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

### Was „Debug-Signatur" bedeutet

Jedes Android-APK muss signiert sein, sonst installiert es kein Gerät. `assembleDebug` nimmt dafür
**keinen** eigenen Schlüssel, sondern den `debug.keystore`, den das Android-SDK beim ersten Build selbst
anlegt — Passwort und Alias sind bei Google dokumentiert und auf jedem Rechner der Welt dieselben. Das ist
kein Versehen, sondern der Zweck: Entwickeln ohne Schlüsselverwaltung.

Praktisch heißt das dreierlei. **Erstens: installierbar, aber nicht ausweisbar.** `adb install app/stundenplan.apk`
läuft auf Emulator und Gerät; die Signatur sagt nur „mit irgendeinem Debug-Schlüssel gebaut", nicht „von
dieser Gruppe". **Zweitens: die Signatur des Containers ist eine andere als die von Android Studio** — wer
die App aus Android Studio installiert und danach dieses APK darüber installiert, bekommt
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`; dann vorher `adb uninstall de.wsp3_1.stundenplan`. **Drittens: nicht
Play-Store-fähig** und für die Abgabe zu ersetzen.

**Release signieren (Abgabe):** `:app:assembleRelease` liefert `app-release-unsigned.apk`; danach
`zipalign -p 4 app-release-unsigned.apk aligned.apk` und `apksigner sign --ks <keystore> --out stundenplan.apk aligned.apk`
(beides in `build-tools/36.0.0`). Der Keystore gehört nicht ins Repo, das Keystore-Passwort in keine Datei
dieses Ordners. Das Release-APK ist zusätzlich `minify`-frei (`proguard-rules.pro` ist bewusst leer) —
es verhält sich also wie das Debug-APK, nur anders signiert.

## In Android Studio

1. **`Open`** (nicht `New Project`) auf dieses Verzeichnis.
2. Gradle-JDK 17 (mitgelieferte JBR), SDK-Platform 36 + Build-Tools 36.0.0.
3. Sync, Emulator mit API 30+, Start. Es erscheint der Einstellungs-Screen.

## Serveradresse

| Wo die App läuft | Adresse |
|---|---|
| Emulator, Stack auf demselben Rechner | `http://10.0.2.2:1500` (Vorbelegung) |
| Echtes Gerät im selben WLAN | `http://<LAN-IP des Rechners>:1500` |
| Projekt-VM | `http://134.245.1.240:1500` |

**`http://localhost:1500` funktioniert im Emulator nie** — `localhost` ist dort das Telefon selbst.
Die Adresse liegt im DataStore, ist jederzeit im Einstellungs-Screen änderbar und überlebt den Neustart
(T-A3.1). Der Server spricht HTTP; `res/xml/network_security_config.xml` erlaubt Klartext. Sobald TLS
steht: dort `base-config` auf `false`.

**Nur Rechner und Port, ohne `/api`.** Die App hängt die Pfade des Vertrags selbst an. Wer
`http://134.245.1.240:1500/api` einträgt (naheliegend, die Web-App hängt `/api` an), bekäme
`/api/api/health` — der Stack antwortet darauf mit 401, und „Verbindung prüfen" meldete nur „Server
nicht erreichbar". Ein `/api` am Ende wird deshalb beim Prüfen und beim Speichern entfernt
(`ApiFactory.stripApiSuffix`); das Eingabefeld zeigt danach die bereinigte Adresse.

**Eine andere Adresse ist ein anderer Server:** Speichern meldet ab und löscht den Offline-Stand.
Sonst ginge das Token der einen Schule an den Rechner der nächsten, und der gespeicherte Plan bliebe
unter einer neuen Anmeldung sichtbar. Dieselbe Adresse erneut zu speichern ändert nichts.

Weiterleitungen folgt die App nicht (`followRedirects(false)`): jede Anfrage geht an genau das Schema,
den Rechner und den Port aus den Einstellungen — eine 307/308-Antwort könnte sonst den Anmelderumpf
mit dem Passwort woanders hin tragen. Der Vertrag kennt keine Weiterleitung; eine solche Antwort wird
wie ein Fehlercode behandelt.

## Konten zum Ausprobieren

Der Beispielbestand (`POST /api/sample-data`) legt **keine** Lehrkraft-Konten an — er legt je Klasse ein
Klassenkonto an. Ein Lehrkraft-Konto legt die Planung an: Web-App → Einstellungen → Konten (oder
`POST /api/users` mit `role: TEACHER` und `teacherId`).

**Stand des laufenden Stacks, gemessen 05.09. über `GET /api/users` mit dem Planungskonto:** 14 Konten —
12 Klassenkonten (`5a` … `10b`, Rolle `STUDENT`), ein Planungskonto (Rolle `PLANNING`) und **genau ein
Lehrkraft-Konto, Benutzername `meyer`** (id 2, Rolle `TEACHER`).

**Das Passwort dieses Kontos ist hier nicht bekannt.** Die im Auftragstext genannte Kombination
`meyer` / `meyer` wird vom Stack mit **401** abgewiesen (gemessen), und sie *kann* nicht stimmen: der
Vertrag schreibt für jedes Passwortfeld `minLength: 8` vor (`openapi.yaml`, `SetPasswordRequest`,
`CreateUserRequest`), fünf Zeichen sind also gar nicht setzbar. Wer den Klickdurchlauf macht, setzt das
Passwort vorher neu — Web-App → Einstellungen → Benutzerkonten → „Passwort setzen", oder
`PUT /api/users/2/password` — und schreibt es **nicht** in eine Datei dieses Ordners.

Ein Planungskonto kann sich in der App anmelden, bekommt aber „Dieses Konto ist keiner Lehrkraft
zugeordnet." — das ist Absicht (404 des Vertrags, `TimetableViewModelTest`: „404 heisst Konto ohne
Lehrkraft").

## Gegen den laufenden Stack prüfen (T-A1.1, T-A2.1, T-A3.1)

Vorbereitung, einmal: Stack starten (`deployment/`, `docker compose up -d`), `http://localhost:1500/api/health`
muss `{"status":"UP","schoolName":"Gymnasium Musterstadt"}` liefern; Emulator API 30+ starten;
`adb install -r ../app/stundenplan.apk`. Die App fragt **denselben** Rechner über `10.0.2.2` an.

| Schritt | Was zu tun ist | Erwartet |
|---|---|---|
| **T-A3.1** zuerst | Erststart landet in den Einstellungen. `http://192.0.2.1:9999` eintragen → „Verbindung prüfen" | Warnung „Server nicht erreichbar", Knopf **„Trotzdem speichern"** erscheint |
| | „Trotzdem speichern", App **ganz beenden** (Übersicht → wegwischen), neu starten | Die Adresse steht noch da — sie liegt im DataStore, nicht im Build |
| | `http://10.0.2.2:1500` eintragen → „Verbindung prüfen" | „Erreichbar: Gymnasium Musterstadt" |
| **T-A1.1** | Als Lehrkraft anmelden (Konto `meyer`, Passwort vorher setzen — siehe oben) | „Angemeldet als …", Sprung in die Tagesansicht |
| | Auf das Datum im Kopf tippen → **21.09.2026** wählen | Nur **eigene** Stunden; kein Knopf, der etwas ändert, keine Auswahl anderer Lehrkräfte |
| | „Woche" | Matrix Mo–Fr mit denselben Stunden |
| **T-A2.1** | Auf eine markierte Stunde tippen (7a, 1. Stunde) | Bottom Sheet: Art der Änderung als Titel, vertretene bzw. übernehmende Lehrkraft, Raum vorher → neu, **alle** Anlässe („Krankmeldung", „Wasserschaden") |
| | Dieselbe Woche als die vertretende Lehrkraft | „Vertretung", orange; in der Sicht der abgebenden Lehrkraft „abgegeben an SCZ" |
| **Offline** | Flugmodus an, Tagesansicht ziehen | Banner „Keine Verbindung — Stand: dd.MM. HH:mm", der Plan bleibt lesbar |

**Belegstand — was hier schon gemessen ist und was nicht.** Es gibt auf diesem Rechner kein Android
Studio, kein SDK und keinen Emulator; **kein Schritt der Tabelle oben ist ausgeführt worden.** Gemessen
ist die Logik darunter, auf der JVM, gegen echte Serverantworten:

| Testfall | Was auf der JVM belegt ist | Was ein Gerät braucht |
|---|---|---|
| **T-A1.1** | `TimetableViewModelTest`: „ohne Serveradresse springt die App in die Einstellungen", „ohne Token verlangt die App die Anmeldung", „Anmeldung mit falschem Passwort zeigt den Text, mit richtigem laedt sie den Plan", „Online liefert Data mit Raster, Zeitstempel und Zaehler". `TimetableRepositoryTest`: Login 200/401, `GET /api/me/timetable/week` 200/401/404/500 gegen MockWebServer mit **echten Antworten des Stacks** (`SampleJson.kt`) | Das Bild der Liste, Tippen, Wischen, Kalender |
| **T-A2.1** | `EntryKindUiTest` (9 Fälle): jeder `kind`-Wert auf Rahmen, Badge, Label, Durchstreichen, Abblenden — inklusive „Vertretung" vs. „Raumwechsel" und „abgegeben an SCZ". `WeekViewTest`: Sortierung, Listenschlüssel, „Heute geändert" | Bottom Sheet in der Hand, Lesbarkeit der Farben auf einem echten Bildschirm, Dark Mode |
| **T-A3.1** | `ApiFactoryTest` (6): Schema-Zwang, Schrägstrich, `/api` am Ende. `TimetableRepositoryTest`: „Health zeigt den Schulnamen, Unsinn ist nicht erreichbar", „Server aus liefert Offline, der alte Cache bleibt lesbar". `OfflineCacheTest` (6): Zeitstempel, vier Wochen, halber Stand, `clear` | „Trotzdem speichern" als Knopf, der **Neustart** der App, Flugmodus |

Die Zahlen: **64 Unit-Tests in 6 Klassen, 0 Fehler** (`app/build/test-results/testDebugUnitTest/TEST-*.xml`,
nachgezählt), **Lint 0 Befunde** (`app/build/reports/lint-results-debug.xml` enthält kein einziges
`<issue>`-Element). Mehr ist ohne Gerät nicht ehrlich behauptbar.

## Was die App zeigt

| Screen | Inhalt | Zustände |
|---|---|---|
| **Einstellungen** | Serveradresse, „Verbindung prüfen" (`GET /api/health` → „Erreichbar: Gymnasium Musterstadt"), „Speichern" bzw. „Trotzdem speichern"; Anmeldung (`POST /api/auth/login`, 401 → „Benutzername oder Passwort ist falsch."); „Angemeldet als …" + „Abmelden" | `NoServerConfigured`, `LoginRequired` |
| **Tagesansicht** (Start) | Wochentag + Datum, Pfeile, „Heute", „Woche"; Banner „Keine Verbindung — Stand: 05.09. 12:15" und „Heute geändert: n Stunden"; Liste mit Stundennummer, Uhrzeit (`GET /api/timegrid`), Fach, Klasse, Raum, Farbbalken in Fachfarbe, Rahmen/Badge/Textlabel je `kind`; Pull-to-refresh | `Loading`, `Data`, `Error` |
| **Wochenansicht** | Matrix Tage × Positionen, Pausenzeilen grau, Kacheln mit Kürzeln und derselben Farblogik; Tipp auf Kachel → Detail, auf Tageskopf → Tag | wie Tag |
| **Stundendetail** | Bottom Sheet (rollbar): Art der Änderung als Titel, Fach, Klasse, Raum, Zeit, Lehrkraft, Original (vertretene Lehrkraft / Raum vorher / Termin vorher), alle Anlässe | nur `Data` |

**Kürzel statt Langnamen im Stundendetail.** Das Pflichtenheft erwartet zu T-A2.1 „Mathematik, Klasse 7a,
R204"; `EntryDto` trägt laut `openapi.yaml` nur `subjectShort`, `roomShort`, `teacherShort`. Die App zeigt
deshalb „Ma". `Dtos.kt` kennt bereits die optionalen Felder `subjectName`, `roomName`, `teacherName`, und
das Detail zeigt `subjectName ?: subjectShort` — liefert der Server sie eines Tages (additive
Vertragsänderung), steht der Langname ohne weitere Änderung da. Zuständigkeit und Termin: ANLEITUNG.md,
Abschnitt 3, offener Punkt 9. **Bis dahin im Testprotokoll vermerken, dass Kürzel gezeigt werden.**

**Auf einen bestimmten Tag stellen** (T-A1.1 und T-A2.1 verlangen den 21.09.2026) — drei Wege:

1. **Auf das Datum im Kopf tippen** → Kalender → Tag wählen → „Anzeigen". Der kurze Weg, auch über Monate.
2. **Wischen** über der Liste: nach links der nächste Tag, nach rechts der vorige (ab ca. 80 dp Strecke).
3. **Pfeile** links und rechts neben „Heute"; oder „Woche" → „Nächste Woche" → Tipp auf den Tageskopf.

Farblogik (`ui/EntryKindUi.kt`, dieselbe Tabelle wie `frontend/src/lib/entry-kind.ts`): `SUBSTITUTED` orange
durchgezogen + „V" + „Vertretung" (nur Raum anders: „Raumwechsel"); `NEEDS_SUBSTITUTION` orange gestrichelt
+ „!" + „noch nicht vertreten"; `GIVEN_AWAY` abgeblendet, kursiv „abgegeben an SCZ"; `CANCELLED` grau
durchgestrichen + „✕" + „entfällt" (Ursprung einer Verlegung: Servertext „verlegt → Mi 1."); `MOVED` orange
+ „V" + „verlegt von Di 1."; `ADDED` orange + „+" + „Zusatzstunde". Farbe ist nie der einzige Träger.

Die App schreibt nichts. Keine Planung, keine Auswahl anderer Lehrkräfte.

## Architektur

```
MainActivity (einzige Activity)  ──setContent──▶  App.kt: when (screen) + BackHandler + Detail-Sheet
TimetableViewModel   screen: StateFlow<Screen>   uiState: StateFlow<UiState>   settingsState: StateFlow<SettingsUiState>
   └─ TimetableRepository (Schnittstelle; HttpTimetableRepository)
         ├─ TimetableApi (Retrofit + kotlinx-serialization; health, login, logout, me, myWeek, timegrid)
         ├─ AppSettings (Schnittstelle; Settings = DataStore: serverUrl, token, displayName)
         └─ OfflineCache (filesDir/offline/week-<montag>.json, max. 4 Wochen; timegrid.json)
```

- `UiState = Loading | Data(week, timegrid, loadedAt, offline, changedToday) | NoServerConfigured | LoginRequired | Error(message)`.
  Offline ist `Data(offline = true)` mit Banner; ohne Cache `Error`. 401 löscht das Token → `LoginRequired`.
  404 → `Error(NotATeacher)`. Fehlertexte kommen aus `strings.xml`, Servertexte (`detail`) werden durchgereicht.
- `Screen = Settings | Day(date) | Week(monday) | Detail(entry, from)`; `back()`: Detail → from, Woche → Tag,
  Einstellungen → Tag (nur angemeldet), Tag → System.
- Abruf bei `ON_START`, beim Ziehen, nach Anmeldung und beim Wechsel über eine Wochengrenze. Kein Push.
- **Es läuft höchstens ein Abruf.** Jeder bekommt eine Nummer; ein neuer bricht den alten ab, und nur die
  Antwort mit der höchsten Nummer darf Zustand schreiben. Sonst gewänne beim schnellen Blättern die zuletzt
  *fertige* Antwort statt der zuletzt *angeforderten* — die Tagesansicht zeigte dann zur falschen Woche
  „Unterrichtsfrei". Abmelden, Anmelden und Adresswechsel zählen die Nummer ebenfalls hoch und warten den
  laufenden Abruf ab, damit seine Antwort den gerade gelöschten Offline-Stand nicht wieder hinschreibt.
- **Der Offline-Stand verschwindet mit der Sitzung:** beim Abmelden, bei 401 und beim Wechsel der
  Serveradresse; eine Anmeldung fängt ebenfalls mit leerem Stand an. Sonst sähe die nächste Lehrkraft am
  selben Gerät die Wochen der vorigen (Zugriffsschutz, A-1).
- Das **Stundenraster** kommt bei jedem Abruf frisch mit; ändert die Planung die Unterrichtszeiten, zeigt
  das Ziehen zum Aktualisieren die neuen. Nur wenn der Server nichts liefert, gilt der gespeicherte Stand.
- Keine Netzaufrufe in Composables; das Token geht je Aufruf als `Authorization`-Header mit.
- **Keine Systemsicherung** (`android:allowBackup="false"` plus `res/xml/data_extraction_rules.xml` für
  Android 12+ und `res/xml/backup_rules.xml` für Android 11): im
  privaten Speicher liegen das Token und der Stundenplan. Die Annahme „das Gerät ist persönlich" deckt
  keine Kopie in einer Cloud-Sicherung und keine Übertragung auf ein neues Gerät.

```
app/src/main/java/de/wsp3_1/stundenplan/
  StundenplanApp.kt · AppContainer.kt · Defaults.kt · MainActivity.kt
  data/Dtos.kt              HealthDto, ProblemDetail, LoginRequest/-Response, MeDto, EntryKind, EntryOriginal,
                            EntryDto, DayDto, WeekDto, TimeslotDto, TimegridDto — Felder exakt wie openapi.yaml
  data/TimetableApi.kt · ApiFactory.kt · Settings.kt · OfflineCache.kt · WeekResult.kt
  data/TimetableRepository.kt · HealthResult.kt · PlanDates.kt
  ui/UiState.kt             UiState, Screen, SettingsUiState, CheckState, LoginError, Message
  ui/TimetableViewModel.kt · App.kt · Theme.kt
  ui/EntryKindUi.kt         kind -> Tone, gestrichelt, Badge, Label, durchgestrichen, abgeblendet
  ui/WeekView.kt            Woche -> Tag, Raster -> Uhrzeit, Positionen, "Heute geaendert"
  ui/SettingsScreen.kt · DayScreen.kt · WeekScreen.kt · EntryDetailSheet.kt · EntryCard.kt (+ Banner)
app/src/test/java/de/wsp3_1/stundenplan/
  ApiFactoryTest · EntryKindUiTest · WeekViewTest · OfflineCacheTest
  TimetableRepositoryTest   MockWebServer: 200/401/404/500, Server aus, Login 200/401, Logout, Raster, Health
  TimetableViewModelTest    der Zustandsautomat, Navigation, Zurueck-Taste, Anmeldung, Abmelden, Adresse
  SampleWeek.kt (Leitszenario-Fixture, Fakes) · SampleJson.kt (echte Serverantworten vom 05.09.)
```

## Bewusste Auslassungen

Kein Navigation-Compose, kein Hilt/Koin, kein Room (JSON-Dateien), kein WorkManager, kein Push oder
Firebase, keine Instrumented-/Espresso-Tests, kein `material-icons-extended`, kein `buildConfig`,
kein `EncryptedSharedPreferences` (Token im DataStore; Gerät ist persönlich — Annahme laut android.md).

**Eine 401 beim Stundenraster meldet bewusst nicht ab.** `loadTimegrid()` läuft nur direkt hinter einer
200-Antwort von `GET /api/me/timetable/week`, mit demselben Token, Millisekunden später. Eine 401 dort
heißt: das Token ist genau in diesem Fenster abgelaufen. Die App zeigt dann die eben geholte, richtige
Woche mit den gespeicherten Uhrzeiten, und der nächste Abruf (Start, Ziehen, Tageswechsel) bekommt die
401 auf die Woche und führt zur Anmeldung. Ein eigener Fehlerpfad für das Raster würde die Lehrkraft aus
einem Bildschirm mit korrekten Daten werfen. Gemessen 05.09. gegen den laufenden Stack: `GET /api/timegrid`
steht auf `.authenticated()` (`backend/.../auth/api/SecurityConfig.java` Z. 78) — jedes angemeldete Konto
bekommt 200, ein ungültiges Token 401; eine Rollenprüfung, die Lehrkräfte dort aussperren könnte, gibt es
nicht.

## Versionen

AGP 8.13.2 · Gradle 8.13 · Kotlin 2.3.20 · Compose-BOM **2026.06.01** (Compose 1.11.4; 2026.08.00 verlangt
AGP ≥ 9.1 und compileSdk 37) · material-icons-core 1.7.8 (explizit, die Icons sind eingefroren) ·
Retrofit 3.0.0 · OkHttp 4.12.0 · kotlinx-serialization 1.11.0 · DataStore 1.2.1 · compileSdk/targetSdk 36 · minSdk 30 · JDK 17.

App selbst: `applicationId de.wsp3_1.stundenplan`, `versionCode 2`, `versionName "0.2"` (Gerüst war 1/„0.1").
`isMinifyEnabled = false` in **beiden** Build-Typen — bewusst, damit im Release keine ProGuard-Überraschung
auftaucht, die auf dem Emulator nicht zu sehen war.

Upgrade auf AGP 9 später nur komplett: `agp = "9.4.0"`, Wrapper `9.6`, `kotlin = "2.4.10"` **und**
`android.builtInKotlin=false` in `gradle.properties` — alle vier zusammen.
