# Nachtrag zum Pflichtenheft — was `testdurchlauf.tex` nachziehen muss

**Stand 05.09. · Ergebnis der Phase Korrektur · nichts davon ist hier eingearbeitet.**

Das Pflichtenheft liegt in einem anderen Repository
(`SoSe2026_WSP3_1/pflichtenheft/kapitel/testdurchlauf.tex`) und wird aus diesem Arbeitsbaum **nicht**
verändert. Diese Datei sammelt die Stellen, an denen der Testdurchlauf vom gebauten Produkt abweicht,
mit fertig formuliertem Ersatztext. Das Kapitel trägt seine eigene Pflegeregel („Bei jeder Änderung an
Bedienschritten, Bezeichnungen, Zugangsdaten, Adressen oder Ports wird dieses Kapitel im selben Merge
Request angepasst") — die Punkte unten sind genau solche Änderungen.

Jeder Punkt nennt: **Fundstelle → was das Produkt tut → Ersatztext.**

---

## P-1 (hoch) Handaufbau gibt es nur im Entwurf; W-4 bis W-7 brauchen ein Annehmen

**Fundstelle.** Abschnitt „Stundenplan erstellen und Konflikte erkennen (W-3, W-4)", Schritt 1:
„Den Regelplan des Montags und des Dienstags nach \autoref{tab:testdaten} eintragen."

**Was das Produkt tut.** Stunden von Hand baut man ausschließlich im **Entwurf**
(Seitenleiste *Planung → Entwurf*, `POST /api/draft/lessons`). Am aktiven Plan wird nicht direkt
gearbeitet — das ist die Entscheidung hinter W-8/W-9 („Erst *Annehmen* macht den Entwurf gültig").
Der Abschnitt *Leitszenario: Ausfall und Vertretung* arbeitet dagegen auf dem **aktiven Plan**
(`GET /api/week`, Vertretung hängt an der Stunde des geltenden Plans). Ohne einen Annehmen-Schritt
dazwischen findet die Testperson dort ein leeres Raster und kann W-5 bis W-7 nicht ausführen.

**Ersatztext.** Vor Schritt 1 des Abschnitts W-3/W-4 einfügen:

> Vor dem ersten Schritt: In der Seitenleiste \glqq Planung → Entwurf\grqq\ öffnen und
> \glqq Leer beginnen\grqq\ wählen. Alle folgenden Schritte dieses Abschnitts finden im Entwurf statt;
> der aktive Plan bleibt unberührt, bis der Entwurf angenommen wird.

Und als neuer letzter Schritt desselben Abschnitts (nach dem bisherigen Schritt 6, Sichtenwechsel):

> \item Im Entwurfsbanner \glqq Annehmen\grqq\ wählen und den Dialog bestätigen.
> \erwartet{Der Entwurf verschwindet; die Plansicht \glqq Aktiver Plan\grqq\ zeigt dieselben Stunden
> wie zuvor der Entwurf. Nennt der Dialog offene Stunden, ist das kein Fehler: sie wandern unverplant
> mit (Anwendungsfall W-8).}

## P-2 (hoch) Die Montagsstunde der 9c muss vor T-W4.1 weg

**Fundstelle.** Derselbe Abschnitt, Schritt 6:
„die Raumsicht R101 zeigt am Montag die 1.\ und 2.~Stunde belegt und ab der 3.~Stunde frei (Testfall T-W4.1)."

**Was das Produkt tut.** Schritt 2 plant die 9c am Montag in der 1. Stunde in R101 ein, Schritt 3
verschiebt sie auf **Montag, 5. Stunde — weiterhin in R101**. Gelöscht wird in Schritt 5 nur die
Freitagsstunde. Damit ist R101 am Montag in der 1., 2. **und 5.** Stunde belegt; die erwartete Aussage
„ab der 3. Stunde frei" trifft nicht zu. Der Testfall schlägt fehl, obwohl die Software richtig rechnet.

**Ersatztext.** Als neuen Schritt zwischen dem bisherigen Schritt 5 und Schritt 6 einfügen:

> \item Die in Schritt~3 verschobene Montagsstunde der Klasse 9c (5.~Stunde, R101) löschen.
> \erwartet{Die Stunde ist aus allen Sichten verschwunden; R101 ist am Montag nur noch in der 1.\ und
> 2.~Stunde belegt.}

Alternative ohne neuen Schritt: in Schritt 6 die Erwartung auf
„… zeigt am Montag die 1., 2.\ und 5.~Stunde belegt und die 3., 4.\ und 6.~Stunde frei" ändern.
Der zusätzliche Löschschritt ist die klarere Variante, weil er den Ausgangszustand für das
Leitszenario wiederherstellt.

## P-3 (mittel) Ort der Beispieldaten benennen

**Fundstelle.** Abschnitt „Beispieldatenbestand und Bedienbarkeit im vollen Umfang", Schritt 1:
„Über die Systemverwaltung den Beispieldatenbestand \glqq Vollständige Schule\grqq\ … laden."

**Was das Produkt tut.** Der Weg heißt in der Oberfläche *Einstellungen → Beispieldaten →
„Vollständige Schule laden"* (Seitenleiste, Gruppe *System*). Sind schon Daten vorhanden, fragt das
System „Vorhandene Daten ersetzen?" — erst *Ersetzen* lädt. Der Beispielbestand bringt einen **fertig
berechneten aktiven Plan** mit; die im Durchlauf zuvor erfassten Testdaten werden dabei ersetzt.
„Systemverwaltung" gibt es als Begriff nicht.

**Ersatztext.**

> \item In der Seitenleiste \glqq Einstellungen\grqq\ öffnen, den Bereich \glqq Beispieldaten\grqq\
> wählen und \glqq Vollständige Schule laden\grqq\ betätigen; die Rückfrage \glqq Vorhandene Daten
> ersetzen?\grqq\ mit \glqq Ersetzen\grqq\ bestätigen.
> \erwartet{Die Grunddatenlisten enthalten anschließend 12 Klassen, 24 Lehrkräfte und 18 Räume, und
> der aktive Plan ist gefüllt. Die zuvor erfassten Testdaten sind ersetzt — das ist beabsichtigt und
> beendet den Teil des Durchlaufs, der auf ihnen aufbaut.}

## P-4 (mittel) „Auswahlliste der Lehrkräfte" beim Einplanen gibt es nicht

**Fundstelle.** Nicht in `testdurchlauf.tex`, sondern in `produktfunktionen.tex`, Testfall **T-W1.3**,
Durchführung Schritt 5: „Eine neue Stunde einplanen und die Auswahlliste der Lehrkräfte öffnen." —
Erwartetes Ergebnis: „In Schritt~5 wird \glqq Meyer\grqq\ nicht mehr zur Auswahl angeboten."

**Was das Produkt tut.** Beim Einplanen einer Stunde wird **keine Lehrkraft gewählt**: sie folgt aus
der Fachzuordnung der Klasse (Entscheidung E-18, `POST /api/draft/lessons` nimmt `classId`,
`subjectId`, `timeslotId`, `roomId` — kein `teacherId`). Eine Auswahlliste von Lehrkräften gibt es an
zwei anderen Stellen: bei der **Fachzuordnung der Klasse** (Grunddaten → Klassen) und bei den
**Vertretungsoptionen** (`GET /api/substitution/options`). Eine deaktivierte Lehrkraft verschwindet
dort, nicht beim Einplanen.

**Ersatztext** für Schritt 5 und das erwartete Ergebnis:

> \item Die Klasse 7a öffnen und für ein Fach die zuständige Lehrkraft neu auswählen.
>
> … In Schritt~5 wird \glqq Meyer\grqq\ in der Auswahl der zuständigen Lehrkraft nicht mehr angeboten;
> die bereits bestehenden Zuordnungen und Stunden bleiben unverändert.

## P-5 (mittel) Kennwörter in der Abgabetabelle

**Fundstelle.** Abschnitt „Angaben zur Abgabe", Tabelle `tab:abgabeangaben`, Zeilen *Verwaltungskonto*
und *Zugang für Lehrkräfte*: „Kennwort wird zur Softwareabgabe eingetragen".

**Warum das jetzt schon klemmt.** Der Durchlauf beginnt mit `docker compose up -d` und der Anmeldung
als `admin`. Das Kennwort steht in `deployment/.env` (`ADMIN_INITIAL_PASSWORD`), und diese Datei ist
absichtlich **nicht** im Repository. Eine Testperson, die nur das Repository und dieses Kapitel hat,
kommt nicht hinein. Der Stack startet außerdem klaglos mit dem Platzhalter `bitte-aendern` aus
`.env.example` — das Backend schreibt seit dem 05.09. dazu eine Warnung ins Log
(`AdminBootstrap`), verweigert den Start aber nicht.

**Zu tun (Pflegeregel des Kapitels).** Vor der Abgabe beide Kennwörter eintragen und dieselben Werte
auf der Instanz setzen. Bis dahin genügt eine Fußnote:

> Bis zur Softwareabgabe gilt das in \texttt{deployment/.env} gesetzte Kennwort; es wird den
> Betreuenden zusammen mit der Abgabe mitgeteilt.

## P-6 ~~(mittel) Neu laden führt zurück auf die Anmeldemaske~~ — **zurückgezogen am 05.09.**

Dieser Punkt beschrieb das Verhalten **vor** F-5a und ist seit dem 05.09. falsch: Das Token liegt im
`localStorage`, gilt 30 Tage (`app.auth.token-ttl` / `AUTH_TOKEN_TTL`), und Abmelden geht über die
Schaltfläche im Benutzermenü. Neu laden, ein neuer Tab und ein Lesezeichen führen **nicht** mehr zur
Anmeldemaske. Wer den alten Ersatztext ins Pflichtenheft übernimmt, schreibt eine falsche Aussage
hinein (Befund PFLICHTENHEFT-7 des Audits vom 05.09.).

**Wenn im Testdurchlauf überhaupt ein Satz dazu stehen soll**, dann dieser:

> Die Anmeldung bleibt über das Neuladen der Seite hinaus bestehen. Beendet wird sie über
> \glqq Abmelden\grqq\ im Benutzermenü oben rechts.

## P-7 (klein) Anzahl der Container beim Start

**Fundstelle.** Abschnitt „Installation und Start", Schritt 2, Prüfkriterium: „Alle Container erreichen
den Zustand \glqq running\grqq".

**Was das Produkt tut.** Seit dem 05.09. startet `docker compose up -d` **drei** Dienste: `db`,
`backend`, `web`. Die Prism-Attrappe `mock` ist nach `docker-compose.dev.yml` gewandert und gehört
nicht mehr zum Auslieferungsstand (sie ist eine API-Attrappe, kein Teil des Produkts). Das
Prüfkriterium selbst bleibt richtig; wer eine Zahl nennen will, nennt drei.

## P-8 (hoch) Klassenkonten und Schülerzugang stehen gegen die Abgrenzung

**Fundstelle.** `zielbestimmungen.tex` Z. 220 (Abgrenzungskriterien): „Kein Eltern- und kein
Schülerzugang … Schülerinnen und Schüler erhalten keine eigenen Zugänge." Dazu Z. 140:
Kennwörter werden „nirgends im Klartext angezeigt oder protokolliert."

**Was das Produkt tut.** Der ausgelieferte Stand hat eine dritte Rolle `STUDENT` (Klassenkonto), den
Endpunkt `POST /api/users/class-accounts`, die Sicht `GET /api/me/class-week` und die Route
`/klassenplan` (Entscheidung E-50, Migration `V7__student_class_account.sql`; in der Startschablone
steht die Rolle bereits in `V1`). Ein Klassenkonto ist **kein persönlicher** Zugang: ein Konto je
Klasse, nur die eigene Woche, Lehrkräfte nur als Kürzel, keine Abwesenheitsgründe. Beim Anlegen zeigt
die Oberfläche die erzeugten Kennwörter **einmalig im Klartext** — anders sind sie nicht verteilbar.

**Was zu entscheiden ist (Rückfrage R5 an adesso, Interview 08.09.).** Entweder

1. das Pflichtenheft zieht nach: Abgrenzung Z. 220 streichen oder auf „keine personenbezogenen
   Schülerkonten, nur Klassenkonten" ändern, die Rolle bei den Produktdaten (Benutzerkonto) ergänzen
   und die einmalige Klartextanzeige der Erstkennwörter dort begründen; **oder**
2. die Funktion fällt: Rolle `STUDENT` aus der Schablone (`V1`) entfernen, im Vollausbau hinter einem
   Schalter lassen und `/klassenplan` ausbauen.

Bis zur Antwort bleibt der Stand wie gebaut. Ohne diesen Punkt widerspricht ein Abgabedokument dem
ausgelieferten Produkt (Befund PFLICHTENHEFT-2 des Audits vom 05.09.).

## P-9 (mittel) Der Startvorgang braucht einen `.env`-Schritt

**Fundstelle.** Abschnitt „Installation und Start", Schritt 1 und 2 („klonen", „`docker compose up -d`")
sowie die Tabelle der Abgabeangaben („weitere Schritte sind nicht erforderlich").

**Was das Produkt tut.** `deployment/.env` steht in `.gitignore` und liegt deshalb in keinem Klon.
`docker-compose.yml` verlangt `ADMIN_INITIAL_PASSWORD` ohne Vorgabewert; ohne die Datei bricht der
Start ab, bevor der erste Container läuft:
`required variable ADMIN_INITIAL_PASSWORD is missing a value: ADMIN_INITIAL_PASSWORD fehlt in deployment/.env`.
Das ist Absicht (kein Kennwort im Repository) — es fehlt nur der Schritt in der Anleitung.

**Ersatztext.** Schritt 1 des Abschnitts ergänzen:

> \item Vorlage kopieren und Kennwort setzen:
> \begin{verbatim}
> cd deployment && cp -n .env.example .env
> \end{verbatim}
> In der Datei \texttt{.env} bei \texttt{ADMIN\_INITIAL\_PASSWORD} das Kennwort aus der Tabelle der
> Abgabeangaben eintragen. Bleibt der Beispielwert \texttt{bitte-aendern} stehen, verweigert der Server
> im Betrieb den Start mit einer Meldung.

Und in der Tabelle der Abgabeangaben statt „weitere Schritte sind nicht erforderlich": „vor dem Start
einmal `cp -n .env.example .env` und das Kennwort eintragen (siehe Schritt 1)".

## P-10 (mittel) Sollkriterium „Blockungen" ist nicht gebaut

**Fundstelle.** `zielbestimmungen.tex` Z. 181 (Sollkriterien): „Blockungen: Doppelstunden können als
Regeltyp abgebildet werden"; dazu `produktdaten.tex` Z. 31, die „Blockung" als Beispiel-Regeltyp nennt,
und das Glossar.

**Was das Produkt tut.** Das Regelwerk kennt genau drei Typen (`MAX_LESSONS_PER_DAY`,
`MAX_LESSONS_PER_WEEK`, `NO_LESSONS_IN_BREAKS`; CHECK-Bedingung in `V1`), der Solver hat keinen
Doppelstunden-Constraint, und keine Bauphase baut einen (VERTRAG, „Was keine Phase baut").

**Was zu entscheiden ist.** Entweder eine Karte `DOUBLE_LESSON` (CHECK erweitern, `RuleFact`, weiche
Bedingung „aufeinanderfolgende Stunden", Regelwerk-Oberfläche) — oder die Abwahl im Pflichtenheft
begründen und „Blockung" in `produktdaten.tex` Z. 31 streichen. Ein Sollkriterium ohne Karte und ohne
begründete Abwahl fällt bei der Abnahme auf (Befund PFLICHTENHEFT-5 des Audits vom 05.09.).

---

## Was zusätzlich zur Abgabe fehlt (kein Textproblem)

- **`app/stundenplan.apk`** — die Datei liegt seit dem 05.09. (16:58) an dem in `tab:abgabeangaben`
  genannten Pfad (33.380.315 Byte, 64 Unit-Tests grün, Lint ohne Befund). Sie trägt aber die
  **Debug-Signatur** und ist auf keinem Gerät und in keinem Emulator ausgeführt worden. Damit sind die
  Schritte I-4, I-5, K-1, K-2 und die App-Anteile von T-A1.1, T-A2.1, T-A3.1 weiterhin **offen**; vor
  der Abgabe neu signieren (Karte T-608). Stand: `android-starter/README.md`.
- **Commit der Abgabe** — der Vollausbau ist nicht committet; ein frischer Klon enthält weder
  `deployment/` noch die Anwendung. Schritt 1 und 2 des Durchlaufs sind für Betreuende erst
  ausführbar, wenn der Stand im GitLab liegt.
