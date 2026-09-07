# SOLVER-PLAN — Timefold-Modell, Constraints, Übersetzer, Betrieb, Tests

Stand 05.09.2026, Vollausbau (`08-Build/plattform`, Branch `vollausbau`). Gilt für die Bauphase **backend2**
(Module `solver`, `planning`, `rules`-Anteil `RuleResolver`). Quellen: `backbone/solver.md`, `backbone/testen.md`,
Entwurfsdoku Kap. 5 (Solver-Modell, Constraint-Tabelle) und Kap. 6 (Sequenz „Vorschlag berechnen"), Pflichtenheft
W-8/W-9, T-W8.1, T-W9.1, `api-contract/openapi.yaml` v1 (`/api/draft/solve`, `DraftDto`). Wo dieses Dokument von
einer Quelle abweicht, steht es in Abschnitt 12 mit Grund.

Was die Startschablone schon hat (`backend/src/main/java/de/wsp3_1/solver/`): `SolverLesson`, `SolverTimetable`,
`TimeslotFact`, `RoomFact`, `TimetableConstraintProvider` mit **H1 `roomConflict`** und **M1 `unassignedLesson`**,
`TimetableConstraintProviderTest` (ConstraintVerifier-Muster). Timefold 2.6.0, Spring Boot 4.1.1, Java 21 — gemessen
(`scripts/ci-backend-test.sh` grün, STARTSTAND.md Abschnitt 10). Dieses Dokument baut darauf auf, nichts wird umbenannt.

## NACHTRAG 05.09. (gemessen — gilt vor dem übrigen Text)

Dieses Dokument ist der **Plan** vom Vortag. Gebaut und gemessen wurde danach; wo sich beides
widerspricht, gilt der Nachtrag. Der vollständige Wortlaut mit Messwerten steht in `docs/VERTRAG.md`,
Modul `solver` (Tabelle SV-1 bis SV-8) und Modul `planning` (PL-1 bis PL-9). Kurz:

| # | im Text unten | tatsächlich |
|---|---|---|
| SV-1 | `SolveInput.timeslots` nur ohne Pause, `rooms` nur aktiv; `RoomFact(id, roomType)` | **alle** Zeitfenster und **alle** Räume; `RoomFact(id, roomType, active)`; den Wertebereich bildet der Übersetzer (Abschnitt 6) |
| SV-2 | „Timefold verlangt nicht, dass ein Startwert im Wertebereich liegt" | Doch. Eine **festgelegte** Stunde auf einer Pause oder in einem inaktiven Raum nimmt ihren Wert zusätzlich in den Wertebereich; eine **nicht festgelegte** wird offen übergeben |
| SV-3 | `SolverManager<SolverTimetable, Long>`, `withFinalBestSolutionConsumer`, `SolverConfigOverride<…>.withTerminationConfig(…)` | `SolverManager<SolverTimetable>` (ein Typparameter), `withFinalBestSolutionEventConsumer(event -> event.solution())`, `new SolverConfigOverride().withTerminationSpentLimit(Duration)` |
| SV-4 | `spent-limit` in `application.properties` sei ein Sicherheitsnetz und deckle bei 60 s | Falsch: das Override **ersetzt** das Basis-Limit. Die Obergrenze ist allein die Validierung von `PUT /api/config` — seit 05.09. `maximum: 600` |
| SV-8 | „Final-Consumer läuft vor `NOT_SOLVING`" | gilt beim Ablauf der Rechenzeit, **nicht** nach `terminateEarly`. `SolverRunner` führt die laufenden Aufträge deshalb selbst (`pending`) |

Dazu zwei Ergänzungen aus der Prüfung vom 05.09., die noch nicht in `VERTRAG.md` standen:

- **H6 `movableLessonInBreak` und H7 `movableLessonInInactiveRoom`** (Abschnitt 4). Die Nebenwirkung von
  SV-2 ist, dass der zusätzliche Wert im Wertebereich **aller** Stunden steht — der Solver dürfte dort
  auch andere hinsetzen. Auf die Pausenregel R3 war kein Verlass (sie darf weich stehen), und H5 prüft
  nur die Raumart, nicht `active`. Zwei harte Regeln schließen es jetzt aus; sie treffen nur
  **nicht festgelegte** Stunden. Damit sind es 20 Constraints, nicht 18.
- **Festgelegt und offen zugleich** gibt es im Solver nicht. Timefold lehnt es ab („has a variable pinned
  to null, even though unassigned values are not allowed"), weil der Raum keine leeren Werte erlaubt.
  `SolveService.buildInput` lässt solche Stunden weg — sie bleiben unverändert stehen. Zwei Wege führen
  dorthin: eine von Hand festgelegte offene Stunde und, bei der Kann-Option `classId`, jede offene Stunde
  einer anderen Klasse.
- **Zwei gleichzeitige Starts**: `requireNotSolving` allein reicht nicht, weil zwischen Prüfung und Start
  die Transaktion des Aufrufers liegt. `SolverRunner.reserve(draftId)` merkt den Lauf vor; der zweite
  Aufruf bekommt 409 `solving` statt eines 500 (Abschnitt 7 und 8).

## 1. Modulgrenze und Dateien

Paket `de.wsp3_1.solver` — **kein** Import aus `jakarta.persistence`, `..persistence..`, `org.springframework.data`
(ArchUnit-Regel 1). Erlaubte Abhängigkeit: nur `shared`. Der Solver bekommt Records und gibt Records zurück; alles
mit Datenbank liegt in `planning`.

| Datei (Paket `solver`) | Zustand | Inhalt |
|---|---|---|
| `TimeslotFact` | da | `record TimeslotFact(Long id, Weekday day, int position, boolean isBreak)` |
| `RoomFact` | da | `record RoomFact(Long id, String roomType)` |
| `RuleFact` | **neu** | `record RuleFact(RuleType type, RuleScope scope, Long ownerId, int value, Severity severity, int weight)` — eine Zeile je Regel **und** Objekt: `scope=CLASS` → `ownerId` = Klassen-ID (Jahrgangszeile schon aufgelöst), `scope=TEACHER` → `ownerId` = Lehrkraft-ID, `NO_LESSONS_IN_BREAKS` → `scope=null, ownerId=null` |
| `BlockedTimeFact` | **neu** | `record BlockedTimeFact(Long teacherId, Long timeslotId)` |
| `PreferredTimeFact` | **neu** | `record PreferredTimeFact(Long teacherId, Long timeslotId)` |
| `SolverLesson` | da, unverändert | Planungsentität (Abschnitt 2) |
| `SolverTimetable` | ändern | drei weitere `@ProblemFactCollectionProperty`-Listen: `rules`, `blockedTimes`, `preferredTimes` (Abschnitt 2) |
| `TimetableConstraintProvider` | ändern | H1–H5, M1, R1–R3 als Zwillinge, S1, S2 (Abschnitt 4) |
| `SolveInput` | **neu** | Eingabe eines Laufs als Records (Abschnitt 6) |
| `SolveResult` | **neu** | Ausgabe eines Laufs: `record SolveResult(List<Placement> placements, String score)`, `record Placement(Long lessonId, Long timeslotId, Long roomId)` |
| `SolverTranslator` | **neu** | statische Methoden `toSolution(SolveInput)` und `toResult(SolverTimetable)` (Abschnitt 6) |
| `SolverRunner` | **neu** | `@Service`, dünne Hülle um `SolverManager<SolverTimetable, Long>`: `start`, `cancel`, `isSolving`, `solveBlocking` (Abschnitt 8) |

Die Enums `RuleType {MAX_LESSONS_PER_DAY, MAX_LESSONS_PER_WEEK, NO_LESSONS_IN_BREAKS}`, `RuleScope {CLASS, TEACHER}`
und `Severity {HARD, SOFT}` liegen in **`de.wsp3_1.shared`** neben `Weekday` (Entscheidung in `docs/VERTRAG.md`,
Abschnitt „Abweichungen"): `solver` darf laut ArchUnit-Tabelle der Schablone nur `shared` benutzen, und `rules`
braucht dieselben Enums. Konfliktarten (`ConflictType`) gehören nicht zum Solver.

Alles Übrige — `SolveService` (Vorbedingungen, Start, Abbruch, Ergebnis speichern), `DraftService`, `ConflictChecker`,
`Assignment` — liegt in `planning.domain` (Abschnitt 6–8). `RuleResolver` liegt in `rules.domain` (Abschnitt 9).

## 2. Planungsmodell

```java
@PlanningEntity
public class SolverLesson {
    @PlanningId private Long id;                      // = lesson.id
    private Long classId; private Long subjectId; private Long teacherId; private String requiredRoomType;
    @PlanningPin private boolean pinned;              // festgelegt (W-9): Zeit UND Raum bleiben
    @PlanningVariable(allowsUnassigned = true) private TimeslotFact timeslot;   // null = offen
    @PlanningVariable private RoomFact room;          // immer belegt; bei timeslot == null bedeutungslos
}

@PlanningSolution
public class SolverTimetable {
    @ProblemFactCollectionProperty @ValueRangeProvider private List<TimeslotFact> timeslots;   // NUR isBreak == false
    @ProblemFactCollectionProperty @ValueRangeProvider private List<RoomFact> rooms;           // nur aktive Räume
    @ProblemFactCollectionProperty private List<RuleFact> rules;                               // nur aktive Regeln, je Objekt
    @ProblemFactCollectionProperty private List<BlockedTimeFact> blockedTimes;
    @ProblemFactCollectionProperty private List<PreferredTimeFact> preferredTimes;
    @PlanningEntityCollectionProperty private List<SolverLesson> lessons;
    @PlanningScore private HardMediumSoftScore score;
}
```

Fest: Klasse, Fach, Lehrkraft (aus `ClassSubject`, E-18), Raumanforderung. Variabel: Zeitfenster und Raum.

**Wertebereiche.** `timeslots` enthält nur Zeitfenster mit `isBreak = false` — der Solver belegt Pausen nie,
unabhängig davon, ob die Pausenregel hart, weich oder inaktiv ist (Entwurfsdoku Kap. 5, Tabelle Solver-Modell).
R3 trifft im Solver deshalb nur festgelegte Stunden, die von Hand in eine Pause gesetzt wurden. `rooms` enthält nur
Räume mit `active = true`. Gibt es keine Zeitfenster oder keinen aktiven Raum, startet kein Lauf (Vorbedingung,
Abschnitt 7).

**Score.** `HardMediumSoftScore`: **hard** = feste Konflikte und harte Regeln, **medium** = Stunde ohne Zeitfenster
(„offen"), **soft** = weiche Regeln und Wünsche. Der Solver lässt lieber eine Stunde offen (−1 medium), als einen
harten Konflikt zu bauen (−1 hard) — das ist die Ausweisung „belegt oder offen" aus T-W8.1. Zugesichert wird kein
vollständiger und kein optimaler Plan (Pflichtenheft, Abgrenzung).

**Gleichheit.** Alle Facts sind Records; `Joiners.equal(SolverLesson::getTimeslot)` vergleicht per `equals`, das bei
Records die Felder vergleicht. Der Übersetzer darf deshalb Fact-Instanzen frei anlegen, muss aber je ID **einen**
Wert erzeugen (Map `timeslotId → TimeslotFact`), damit Wertebereich und Zuweisung dieselben Objekte teilen.

## 3. Gewichte

| Ebene | Constraint | Strafe/Belohnung je Treffer |
|---|---|---|
| hard | H1–H5 | 1 |
| hard | R1/R2 `…Hard` | `(Anzahl − value) × weight` (Überschuss, nicht 0/1) |
| hard | R3 `noLessonsInBreaksHard` | `weight` je Stunde in einer Pause |
| medium | M1 | 1 je offener Stunde |
| soft | R1/R2 `…Soft` | `(Anzahl − value) × weight` |
| soft | R3 `noLessonsInBreaksSoft` | `weight` |
| soft | S1 `teacherPreferredTime` | **+1** (Belohnung) je Stunde in einem bevorzugten Zeitfenster |
| soft | S2 `subjectSpreadOverWeek` | 1 je Paar gleiches Fach derselben Klasse am selben Tag |

`weight` kommt aus `rule.weight` (Standard 1) und ist die einzige Stellschraube der Planung für die Feinjustierung
(T-210). Keine `ConstraintWeightOverrides` (E-21). Feste Constraints haben kein Gewicht — wer S1/S2 verschieben
will, ändert den Code (bewusst: „Regelwerk = Daten" gilt für die drei Regeltypen, nicht für die Wünsche).

## 4. Constraints — vollständiger Code

Namen sind stabil (Entwurfsdoku Tab. „Constraints des TimetableConstraintProvider"); Tests, Doku und
`docs/ABNAHME-CHECKLISTE.md` verweisen darauf. Reihenfolge in `defineConstraints` wie unten.

```java
package de.wsp3_1.solver;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;

public class TimetableConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[] {
                roomConflict(f), teacherConflict(f), classConflict(f), teacherBlockedTime(f), roomTypeMismatch(f),
                unassignedLesson(f),
                maxLessonsPerDayClassHard(f), maxLessonsPerDayClassSoft(f),
                maxLessonsPerDayTeacherHard(f), maxLessonsPerDayTeacherSoft(f),
                maxLessonsPerWeekClassHard(f), maxLessonsPerWeekClassSoft(f),
                maxLessonsPerWeekTeacherHard(f), maxLessonsPerWeekTeacherSoft(f),
                noLessonsInBreaksHard(f), noLessonsInBreaksSoft(f),
                teacherPreferredTime(f), subjectSpreadOverWeek(f)
        };
    }

    // ---------------------------------------------------------------- feste harte Konflikte (E-07, E-20)

    /** H1: zwei Stunden im selben Zeitfenster im selben Raum. (schon in der Schablone) */
    public Constraint roomConflict(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getTimeslot), Joiners.equal(SolverLesson::getRoom))
                .penalize(HardMediumSoftScore.ONE_HARD).asConstraint("roomConflict");
    }

    /** H2: dieselbe Lehrkraft zweimal im selben Zeitfenster. */
    public Constraint teacherConflict(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getTimeslot), Joiners.equal(SolverLesson::getTeacherId))
                .penalize(HardMediumSoftScore.ONE_HARD).asConstraint("teacherConflict");
    }

    /** H3: dieselbe Klasse zweimal im selben Zeitfenster. */
    public Constraint classConflict(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getTimeslot), Joiners.equal(SolverLesson::getClassId))
                .penalize(HardMediumSoftScore.ONE_HARD).asConstraint("classConflict");
    }

    /** H4: Stunde in einem gesperrten Zeitfenster der Lehrkraft (T-W1.2). */
    public Constraint teacherBlockedTime(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .join(BlockedTimeFact.class,
                        Joiners.equal(SolverLesson::getTeacherId, BlockedTimeFact::teacherId),
                        Joiners.equal(l -> l.getTimeslot().id(), BlockedTimeFact::timeslotId))
                .penalize(HardMediumSoftScore.ONE_HARD).asConstraint("teacherBlockedTime");
    }

    /** H5: Raumart passt nicht zur Raumanforderung des Fachs (exakter Textvergleich). */
    public Constraint roomTypeMismatch(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .filter(l -> l.getRequiredRoomType() != null
                        && !l.getRequiredRoomType().equals(l.getRoom().roomType()))
                .penalize(HardMediumSoftScore.ONE_HARD).asConstraint("roomTypeMismatch");
    }

    // ---------------------------------------------------------------- offene Stunde

    /** M1: Stunde ohne Zeitfenster. (schon in der Schablone) */
    public Constraint unassignedLesson(ConstraintFactory f) {
        return f.forEachIncludingUnassigned(SolverLesson.class)
                .filter(l -> l.getTimeslot() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM).asConstraint("unassignedLesson");
    }

    // ---------------------------------------------------------------- Regelwerk: Zwillinge hart/weich (E-21)

    /** R1 Klasse, hart: mehr Stunden je Tag als die Regel erlaubt; Strafe = Überschuss × Gewicht. */
    public Constraint maxLessonsPerDayClassHard(ConstraintFactory f) {
        return perDay(f, RuleScope.CLASS, Severity.HARD, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, day, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayClassHard");
    }
    public Constraint maxLessonsPerDayClassSoft(ConstraintFactory f) {
        return perDay(f, RuleScope.CLASS, Severity.SOFT, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, day, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayClassSoft");
    }
    public Constraint maxLessonsPerDayTeacherHard(ConstraintFactory f) {
        return perDay(f, RuleScope.TEACHER, Severity.HARD, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, day, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayTeacherHard");
    }
    public Constraint maxLessonsPerDayTeacherSoft(ConstraintFactory f) {
        return perDay(f, RuleScope.TEACHER, Severity.SOFT, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, day, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayTeacherSoft");
    }

    /** R2 wie R1 ohne Tag. */
    public Constraint maxLessonsPerWeekClassHard(ConstraintFactory f) {
        return perWeek(f, RuleScope.CLASS, Severity.HARD, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekClassHard");
    }
    public Constraint maxLessonsPerWeekClassSoft(ConstraintFactory f) {
        return perWeek(f, RuleScope.CLASS, Severity.SOFT, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekClassSoft");
    }
    public Constraint maxLessonsPerWeekTeacherHard(ConstraintFactory f) {
        return perWeek(f, RuleScope.TEACHER, Severity.HARD, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekTeacherHard");
    }
    public Constraint maxLessonsPerWeekTeacherSoft(ConstraintFactory f) {
        return perWeek(f, RuleScope.TEACHER, Severity.SOFT, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, n) -> (n - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekTeacherSoft");
    }

    /** R3: Stunde in einer Pause — trifft im Solver nur festgelegte Stunden (Pausen sind kein Wertebereich). */
    public Constraint noLessonsInBreaksHard(ConstraintFactory f) {
        return inBreaks(f, Severity.HARD)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, l) -> rule.weight()).asConstraint("noLessonsInBreaksHard");
    }
    public Constraint noLessonsInBreaksSoft(ConstraintFactory f) {
        return inBreaks(f, Severity.SOFT)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, l) -> rule.weight()).asConstraint("noLessonsInBreaksSoft");
    }

    // ---------------------------------------------------------------- weiche Wünsche (Soll: Güte des Vorschlags)

    /** S1: Belohnung für eine Stunde in einem bevorzugten Zeitfenster der Lehrkraft. */
    public Constraint teacherPreferredTime(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .join(PreferredTimeFact.class,
                        Joiners.equal(SolverLesson::getTeacherId, PreferredTimeFact::teacherId),
                        Joiners.equal(l -> l.getTimeslot().id(), PreferredTimeFact::timeslotId))
                .reward(HardMediumSoftScore.ONE_SOFT).asConstraint("teacherPreferredTime");
    }

    /** S2: dasselbe Fach einer Klasse zweimal am selben Tag (Fächerverteilung über die Woche). */
    public Constraint subjectSpreadOverWeek(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getClassId), Joiners.equal(SolverLesson::getSubjectId),
                        Joiners.equal(l -> l.getTimeslot().day()))
                .penalize(HardMediumSoftScore.ONE_SOFT).asConstraint("subjectSpreadOverWeek");
    }

    // ---------------------------------------------------------------- gemeinsame Ströme der Regel-Zwillinge

    /** (Regel, Tag, Anzahl) je Objekt und Tag, nur Gruppen über dem Wert. */
    private static TriConstraintStream<RuleFact, de.wsp3_1.shared.Weekday, Integer> perDay(
            ConstraintFactory f, RuleScope scope, Severity severity, java.util.function.Function<SolverLesson, Long> owner) {
        return f.forEach(RuleFact.class)
                .filter(r -> r.type() == RuleType.MAX_LESSONS_PER_DAY && r.scope() == scope && r.severity() == severity)
                .join(SolverLesson.class, Joiners.equal(RuleFact::ownerId, owner))
                .groupBy((r, l) -> r, (r, l) -> l.getTimeslot().day(), ConstraintCollectors.countBi())
                .filter((r, day, n) -> n > r.value());
    }

    /** (Regel, Anzahl) je Objekt, nur über dem Wert. */
    private static BiConstraintStream<RuleFact, Integer> perWeek(
            ConstraintFactory f, RuleScope scope, Severity severity, java.util.function.Function<SolverLesson, Long> owner) {
        return f.forEach(RuleFact.class)
                .filter(r -> r.type() == RuleType.MAX_LESSONS_PER_WEEK && r.scope() == scope && r.severity() == severity)
                .join(SolverLesson.class, Joiners.equal(RuleFact::ownerId, owner))
                .groupBy((r, l) -> r, ConstraintCollectors.countBi())
                .filter((r, n) -> n > r.value());
    }

    /** (Pausenregel, Stunde in einer Pause). */
    private static BiConstraintStream<RuleFact, SolverLesson> inBreaks(ConstraintFactory f, Severity severity) {
        return f.forEach(RuleFact.class)
                .filter(r -> r.type() == RuleType.NO_LESSONS_IN_BREAKS && r.severity() == severity)
                .join(SolverLesson.class, Joiners.filtering((r, l) -> l.getTimeslot().isBreak()));
    }
}
```

Hinweise für den Bauagenten:
- `forEach(SolverLesson.class)` liefert bei `allowsUnassigned = true` nur Stunden **mit** Zeitfenster; `l.getTimeslot()`
  ist in allen Strömen außer M1 deshalb nie `null`. M1 nutzt `forEachIncludingUnassigned` (Lernnotiz 13, in 2.6.0 Community).
- `Joiners.equal(RuleFact::ownerId, owner)` mit `owner = SolverLesson::getClassId` bzw. `getTeacherId`: die Regel
  wird je Objekt geliefert (Abschnitt 9), deshalb reicht ein Gleichheits-Join. Eine Regel ohne Objekt (Pausenregel) hat
  `ownerId = null` und wird nur in `inBreaks` benutzt.
- Wer die Generics-Signaturen von `perDay`/`perWeek` nicht sauber bekommt: die vier Methoden je Familie ausschreiben
  (je 6 Zeilen). Lesbarkeit vor Kürze.
- Kein `ConstraintWeightOverrides`, kein `@ConstraintWeight`, kein `SolutionManager.analyze()` (Enterprise, Lernnotiz 12).

## 5. Festlegung (W-9), Handaufbau (W-3), Teilberechnung (Kann)

- `pinned = true` → `@PlanningPin` sperrt die **ganze Entität** (Zeit und Raum). Die Stunde wird weiter bewertet und
  kann Konflikte erzeugen; das ist gewollt (Vorbedingung in Abschnitt 7 verhindert Widersprüche nur zwischen
  festgelegten Stunden). Handaufbau und Verschieben setzen `pinned = true` (`DraftService`); der Solver läuft dabei nicht.
- Neuberechnung (T-W9.1): Entwurf laden → `SolveInput` (alle Stunden, `pinned` aus `lesson.pinned`) → lösen → nur
  Stunden mit `pinned = false` werden geschrieben (Abschnitt 6).
- **Kann `classId`** (E-40, Vertrag `StartSolveInput.classId`): `SolveService.start(classId)` setzt im `SolveInput`
  bei jeder Stunde einer **anderen** Klasse `pinned = true` — nur im Eingaberecord, nicht in der Datenbank. Der Solver
  bewegt dann nur die Stunden der gewählten Klasse; `storeResult` schreibt ohnehin nur ungepinnte Stunden. Fünf Zeilen,
  kein neues Modell. Unbekannte `classId` → 404.

## 6. Übersetzer DB ↔ Solver

> **Achtung:** SV-1 und SV-2 im Nachtrag ganz oben gehen diesem Abschnitt vor.

**Eingabe** (`solver.SolveInput`, alles Records, kein JPA):

```java
public record SolveInput(List<LessonInput> lessons, List<TimeslotFact> timeslots, List<RoomFact> rooms,
                         List<RuleFact> rules, List<BlockedTimeFact> blockedTimes, List<PreferredTimeFact> preferredTimes) {
    public record LessonInput(Long id, Long classId, Long subjectId, Long teacherId, String requiredRoomType,
                              boolean pinned, Long timeslotId, Long roomId) {}
}
```

**`planning.domain.SolveService.buildInput(draft, classId)`** (läuft in der Transaktion des Requests):

| Feld | Quelle | Regel |
|---|---|---|
| `lessons` | `lesson` des Entwurfs | `classId/subjectId/teacherId/requiredRoomType` aus `lesson.classSubject` (Klasse, Fach, Lehrkraft, `subject.requiredRoomType`); `pinned = lesson.pinned || (classId != null && !lesson.classSubject.schoolClass.id.equals(classId))`; `timeslotId/roomId` wie gespeichert (beide oder keiner). Stunden **inaktiver** Klassen bleiben drin (sie haben Zeit und Raum belegt) |
| `timeslots` | `timeslot` | nur `isBreak = false`, sortiert nach Tag und Position |
| `rooms` | `room` | nur `active = true` |
| `rules` | `RuleResolver.resolve(...)` (Abschnitt 9) | nur aktive Regeln, je Klasse bzw. Lehrkraft eine `RuleFact` |
| `blockedTimes` | `teacher_blocked_timeslot` | aller Lehrkräfte, die in `lessons` vorkommen |
| `preferredTimes` | `teacher_preferred_timeslot` | analog |

**`SolverTranslator.toSolution(SolveInput)`** (solver): baut je `TimeslotFact` und `RoomFact` **eine** Instanz
(`Map<Long, TimeslotFact>` aus `input.timeslots()`, `Map<Long, RoomFact>` aus `input.rooms()`), dann je
`LessonInput` eine `SolverLesson`; `timeslot = map.get(timeslotId)` (null bleibt null), `room = roomMap.get(roomId)`.
Ist ein gespeichertes Zeitfenster eine Pause (Handaufbau in die Pause) oder ein Raum inzwischen inaktiv, fehlt er im
Wertebereich: dann wird die Fact-Instanz trotzdem angelegt und der Stunde zugewiesen (sie ist festgelegt oder wird
vom Solver ohnehin neu belegt). Timefold verlangt nicht, dass ein Startwert im Wertebereich liegt. **Tag-1-Prüfung**:
falls die Version das doch verlangt (Meldung beim Start), Zeitfenster/Räume der festgelegten Stunden zusätzlich in
den Wertebereich aufnehmen — Lernnotiz schreiben.

**`SolverTranslator.toResult(SolverTimetable)`**: je `SolverLesson` ein `Placement(id, timeslot == null ? null :
timeslot.id(), timeslot == null ? null : room.id())` — die Invariante „belegt oder offen" entsteht **hier**, nicht erst in
der Datenbank. `score = solution.getScore().toString()` („0hard/0medium/-2soft").

**`planning.domain.DraftService.storeResult(draftId, SolveResult)`** (`@Transactional`, wird vom Solver-Thread
über den Spring-Proxy aufgerufen — deshalb Aufruf immer über die injizierte Bean, nie `this`):
1. Entwurf laden; existiert er nicht mehr → nichts tun, Warnung loggen (kann nur passieren, wenn jemand die
   409-Sperre umgeht).
2. Je `Placement`: Stunde laden; **`pinned == true` → überspringen**; sonst `timeslot`/`room` setzen (beide oder beide null).
3. `timetable.solveLastScore = score`, `solveError = null`.
4. Konflikte werden **nicht** gespeichert (E-31); `GET /api/draft` berechnet sie beim nächsten Lesen.

**`DraftService.markFailed(draftId, Throwable)`**: `solveError = "Berechnung abgebrochen: " + ex.getMessage()`
(auf 500 Zeichen gekürzt), Stunden unverändert. `GET /api/draft` zeigt dann `status: FAILED`, `solve.error` gesetzt.

**`DraftService.syncRequiredLessons(draft)`** (vor jedem Lauf): je aktive Klasse und je `ClassSubject`:
`fehlend = weeklyHours − Anzahl Stunden dieses ClassSubject im Entwurf`; für jede fehlende eine `Lesson(pinned=false,
timeslot=null, room=null)` anlegen. Überzählige Zeilen bleiben (Warnung im `DraftDto`). Keine Entities löschen.

## 7. Vorbedingungen (`SolveService.start(classId)`)

Reihenfolge und exakte Antworten (alle `ProblemDetail`, Slugs aus `openapi.yaml`):

| # | Prüfung | Antwort |
|---|---|---|
| 1 | kein Entwurf | 404 `not-found`, detail „Kein Entwurf vorhanden." |
| 2 | `solverRunner.isSolving(draftId)` | 409 `solving`, detail „Der Entwurf wird gerade berechnet. Bitte warten oder die Berechnung abbrechen." |
| 3 | Grunddaten: (a) eine aktive Klasse hat keine `ClassSubject`-Zeile, (b) kein Zeitfenster mit `isBreak = false`, (c) kein aktiver Raum | 409 `master-data-incomplete`, `missing[]` je Punkt: „Klasse 9c hat kein Fach", „Kein Stundenraster angelegt", „Kein aktiver Raum" |
| 4 | Kann: `classId` gesetzt, Klasse unbekannt oder inaktiv | 404 `not-found` |
| 5 | `syncRequiredLessons` | — |
| 6 | Widerspruch festgelegter Stunden: `ConflictChecker.check(nur Assignments mit pinned = true)` liefert einen Konflikt mit `severity = HARD` | 409 `pinned-contradiction`, `conflicts[]` = genau diese Konflikte, detail „Festgelegte Stunden stehen im Konflikt. Bitte auflösen oder Festlegung aufheben." |
| 7 | Start: `solveStartedAt = now`, `solveLimitSeconds = SystemConfig.maxSolveSeconds`, `solveError = null`, `solveLastScore` bleibt; `solverRunner.start(...)` | 202, kein Body |

Punkt 6 nutzt denselben `ConflictChecker` wie `GET /api/draft` — Doppelbelegung, Sperrzeit, Raumart **und** harte
Regeln zählen (eine festgelegte Stunde in einer Pause bei harter Pausenregel ist ein Widerspruch: der Solver könnte
ihn nie auflösen). Wochenstunden-Vorbedingung („Fach ohne Wochenstunden") gibt es nicht — die Datenbank erzwingt
`weekly_hours >= 1` und `teacher_id NOT NULL`.

Mit `classId` gilt Punkt 6 für alle Stunden, die im `SolveInput` als `pinned` gelten (also auch die anderen Klassen):
Widersprüche dort blockieren den Lauf genauso, weil der Solver sie nicht anfassen darf.

## 8. Betrieb: Start, Status, Abbruch, Rechenzeit, Fehler

> **Achtung:** SV-3, SV-4 und SV-8 im Nachtrag ganz oben gehen diesem Abschnitt vor.

```java
@Service
public class SolverRunner {
    private final SolverManager<SolverTimetable, Long> solverManager;   // Bean des Timefold-Spring-Boot-Starters

    /** Startet asynchron und kehrt sofort zurück (HTTP 202). Problem-ID = Entwurfs-ID. */
    public void start(Long draftId, SolveInput input, int seconds,
                      Consumer<SolveResult> onFinished, Consumer<Throwable> onError) {
        solverManager.solveBuilder()
                .withProblemId(draftId)
                .withProblem(SolverTranslator.toSolution(input))          // im Request-Thread gebaut, in der Transaktion
                .withFinalBestSolutionConsumer(sol -> onFinished.accept(SolverTranslator.toResult(sol)))
                .withExceptionHandler((id, ex) -> onError.accept(ex))
                .withConfigOverride(new SolverConfigOverride<SolverTimetable>()
                        .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(seconds))))
                .run();
    }

    public boolean isSolving(Long draftId) {
        return solverManager.getSolverStatus(draftId) != SolverStatus.NOT_SOLVING;
    }

    /** Vorzeitig beenden: Timefold liefert danach das bisher beste Ergebnis an den Final-Consumer (T-W8.1). */
    public void cancel(Long draftId) {
        solverManager.terminateEarly(draftId);
    }

    /** Blockierend — nur für den Beispieldatenbestand (sampledata), nie aus einem Controller mit Nutzerwartezeit > 15 s. */
    public SolveResult solveBlocking(Long problemId, SolveInput input, int seconds) throws Exception {
        SolverJob<SolverTimetable, Long> job = solverManager.solveBuilder()
                .withProblemId(problemId).withProblem(SolverTranslator.toSolution(input))
                .withConfigOverride(new SolverConfigOverride<SolverTimetable>()
                        .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(seconds))))
                .run();
        return SolverTranslator.toResult(job.getFinalBestSolution());
    }
}
```

Klassen aus Timefold 2.6.0 (Doku 2.6.0, Lernnotiz 13; Paketpfade beim ersten Kompilieren bestätigen und als
Lernnotiz festhalten): `ai.timefold.solver.core.api.solver.SolverManager`, `SolverJob`, `SolverStatus`,
`SolverConfigOverride`; `ai.timefold.solver.core.config.solver.termination.TerminationConfig`. Rückfall, falls
`withConfigOverride` in dieser Version anders heißt (O-7): eigener `SolverFactory.create(solverConfig.copyConfig()
.withTerminationSpentLimit(...))` je Lauf — dann `SolverRunner` intern umbauen, die Schnittstelle bleibt.

- **Status im `DraftDto`**: `SOLVING`, wenn `solverRunner.isSolving(draftId)`; sonst `FAILED`, wenn
  `timetable.solveError != null`; sonst `EDITING`. Kein eigener Zustandsautomat in der Datenbank.
  `solve = {startedAt, limitSeconds, lastScore, error}` aus den vier Spalten `solve_started_at`, `solve_limit_seconds`,
  `solve_last_score`, `solve_error` (V5 ergänzt die letzten zwei, siehe `docs/VERTRAG.md`).
- **Reihenfolge am Ende**: Timefold ruft den Final-Consumer im Solver-Thread auf, **bevor** der Status auf
  `NOT_SOLVING` geht. `storeResult` ist also fertig, wenn `GET /api/draft` wieder `EDITING` meldet, und keine
  Mutation kann dazwischenrutschen (alle Mutationen prüfen `isSolving` → 409). Beleg: Pinning-Integrationstest
  (Abschnitt 10.3) fragt nach dem Statuswechsel sofort den Entwurf ab.
- **Abbruch** `DELETE /api/draft/solve` → `cancel(draftId)`; antwortet 202, auch wenn nichts läuft.
- **Rechenzeit** je Lauf aus `SystemConfig.maxSolveSeconds` (planning → config). `timefold.solver.termination.spent-
  limit=60s` in `application.properties` bleibt als Sicherheitsnetz; wer `maxSolveSeconds` > 60 setzt, wird trotzdem
  bei 60 s beendet — Vertrag `ConfigDto.maxSolveSeconds` bekommt in `PUT /api/config` deshalb `maximum: 600` **nicht**;
  stattdessen setzt B2 die Property auf `600s` und beschreibt das in `application.properties`. `parallel-solver-count=1`
  bleibt: ein Lauf zur Zeit auf einer 4-GB-VM; ein zweiter Lauf wartet in der Warteschlange (Beispieldaten während
  eines Laufs) — die 409-Sperre je Entwurf verhindert zwei Läufe auf demselben Entwurf.
- **Fehler**: `withExceptionHandler` → `draftService.markFailed`. Typischer Fall: `SolverTimetable` ohne
  Wertebereich (leeres Raster) — wird von Vorbedingung 3 vorher abgefangen.
- **Neustart des Backends** während eines Laufs: kein Zwischenstand in der Datenbank; der Entwurf steht wie vor dem
  Start, `status` ist `EDITING` (kein Solver läuft), `solveStartedAt` bleibt als Spur. Kein Wiederanlauf.
- Kein Dauer-Solver, kein `ProblemChange`, kein Best-Solution-Event (Throttling wäre Enterprise, E-23), kein
  WebSocket. Web und App pollen `GET /api/draft` alle 2 s.

## 9. Regelauflösung (`rules.domain.RuleResolver`) — eine Quelle für Solver und ConflictChecker

```java
public record EffectiveRule(Long ruleId, RuleType type, RuleScope scope, Long ownerId,
                            int value, Severity severity, int weight, String label) {}

public final class RuleResolver {
    /** classes: (id, grade) aller aktiven Klassen; teacherIds: alle Lehrkräfte, die im Plan vorkommen. */
    public static List<EffectiveRule> resolve(List<Rule> activeRules, List<ClassRef> classes, List<Long> teacherIds) { ... }
    public record ClassRef(Long id, int grade) {}
}
```

Regeln: nur `active = true`. `scope = CLASS`: je Klasse und Typ die Zeile mit `grade == class.grade`, sonst die Zeile
mit `grade == null`; gibt es keine, keine `EffectiveRule` für dieses Paar. `scope = TEACHER`: je Lehrkraft die
schulweite Zeile (`grade` ist bei Lehrkräften immer leer). `NO_LESSONS_IN_BREAKS`: eine Zeile mit `scope = null,
ownerId = null`. `label` ist der deutsche Regelname („Maximale Stunden pro Tag") für Konflikttexte.
`SolveService` bildet daraus `RuleFact(type, scope, ownerId, value, severity, weight)`; der `ConflictChecker` nutzt
dieselbe Liste in seinem `CheckContext`. Solver und Checker sind damit zwei Sichten derselben aufgelösten Regeln
(Entwurfsdoku: „synchron zu halten"); Abweichung = Fehler, Abschnitt 10.4.

## 10. Tests

### 10.1 ConstraintVerifier (Ebene 2, `./gradlew test`, `TimetableConstraintProviderTest`)

Aufbau wie in der Schablone: `ConstraintVerifier.build(new TimetableConstraintProvider(), SolverTimetable.class,
SolverLesson.class)`; Konstanten `CLASS_7A=1, CLASS_9C=2, CLASS_8B=3; MATHEMATIK=10, DEUTSCH=11, PHYSIK=12;
MEYER=100, SCHULZ=101, WEBER=102; MO_1..MO_5, TU_1, FR_5 (= FR position 6), MO_BREAK (position 3, isBreak=true);
R101, R204 (roomType "Klassenraum"), P1 ("Fachraum Physik")`. Helfer `lesson(id, classId, subjectId, teacherId,
requiredRoomType, timeslot, room)`. Je Constraint ein Positiv- **und** ein Negativfall; Gewichte werden vom Verifier
absichtlich ignoriert — bei R1/R2 zählt `penalizesBy` den Überschuss.

| Test | given | erwartet |
|---|---|---|
| `tW3_1_roomDoubleBooked_penalizesExactlyOnce` (da) | 7a Ma MEY MO_1 R101; 9c Ma SCZ MO_1 R101; 7a Ma MEY TU_1 R101 | `roomConflict` 1 |
| `roomConflict_differentRooms_noPenalty` | zwei Stunden MO_1 in R101 und R204 | 0 |
| `teacherConflict_sameTeacherSameSlot_penalizesOnce` | 7a Ma MEY MO_1 R101; 8b Ma MEY MO_1 R204 | `teacherConflict` 1 |
| `teacherConflict_sameTeacherDifferentSlots_noPenalty` | MEY MO_1, MEY MO_2 | 0 |
| `classConflict_sameClassSameSlot_penalizesOnce` | 7a Ma MEY MO_1 R101; 7a De WEB MO_1 R204 | `classConflict` 1 |
| `classConflict_differentClasses_noPenalty` | 7a MO_1, 8b MO_1 (verschiedene Räume, Lehrkräfte) | 0 |
| `tW1_2_teacherBlockedTime_schulzFriday5_penalizesOnce` | 9c Ma SCZ FR_5 R101 + `BlockedTimeFact(SCHULZ, FR_5.id)` | `teacherBlockedTime` 1 |
| `teacherBlockedTime_otherTeacherBlocked_noPenalty` | 9c Ma SCZ FR_5 + `BlockedTimeFact(MEYER, FR_5.id)` | 0 |
| `roomTypeMismatch_physicsInClassroom_penalizesOnce` | 7a Ph MEY MO_4 R101 (`requiredRoomType="Fachraum Physik"`) | `roomTypeMismatch` 1 |
| `roomTypeMismatch_physicsInP1_noPenalty` | dieselbe Stunde in P1 | 0 |
| `roomTypeMismatch_noRequirement_noPenalty` | 7a Ma (requiredRoomType null) in P1 | 0 |
| `unassignedLesson_withoutTimeslot_penalizesOnce` (da) | Stunde ohne Zeitfenster | `unassignedLesson` 1 |
| `unassignedLesson_withTimeslot_penalizesNothing` (da) | | 0 |
| `tW2_1_maxLessonsPerDayClassSoft_valueThree_fourLessons_penalizesOvershoot` | `RuleFact(MAX_LESSONS_PER_DAY, CLASS, CLASS_7A, 3, SOFT, 1)` + 7a MO_1, MO_2, MO_4, MO_5 (Räume/Lehrkräfte verschieden) | `maxLessonsPerDayClassSoft` 1 (= 4 − 3) |
| `maxLessonsPerDayClassSoft_valueThree_threeLessons_noPenalty` | dieselbe Regel, drei Stunden | 0 |
| `maxLessonsPerDayClassHard_ruleIsSoft_noPenalty` | SOFT-Regel, vier Stunden | `maxLessonsPerDayClassHard` 0 |
| `maxLessonsPerDayClassHard_ruleIsHard_penalizesOvershoot` | HARD-Regel, vier Stunden | 1 |
| `maxLessonsPerDayClassSoft_otherClassRule_noPenalty` | Regel für CLASS_8B, vier Stunden der 7a | 0 |
| `maxLessonsPerDayTeacherSoft_valueTwo_threeLessons_penalizesOvershoot` | `RuleFact(…, TEACHER, MEYER, 2, SOFT, 1)` + MEY MO_1, MO_2, MO_4 (Klassen verschieden) | 1 |
| `maxLessonsPerDayTeacherHard_ruleIsSoft_noPenalty` | | 0 |
| `maxLessonsPerWeekClassSoft_valueFour_sixLessons_penalizesOvershoot` | `RuleFact(MAX_LESSONS_PER_WEEK, CLASS, CLASS_7A, 4, SOFT, 1)` + sechs 7a-Stunden über MO/TU | 2 |
| `maxLessonsPerWeekClassHard_ruleIsSoft_noPenalty` | | 0 |
| `maxLessonsPerWeekTeacherSoft_valueFour_fiveLessons_penalizesOvershoot` | Regel für MEYER, fünf MEY-Stunden | 1 |
| `maxLessonsPerWeekTeacherHard_ruleIsHard_penalizesOvershoot` | HARD | 1 |
| `noLessonsInBreaksHard_pinnedLessonInBreak_penalizesOnce` | `RuleFact(NO_LESSONS_IN_BREAKS, null, null, 1, HARD, 1)` + 7a Ma MO_BREAK R101 (pinned) | `noLessonsInBreaksHard` 1 |
| `noLessonsInBreaksSoft_ruleIsHard_noPenalty` | HARD-Regel | `noLessonsInBreaksSoft` 0 |
| `noLessonsInBreaksSoft_ruleIsSoft_penalizesOnce` | SOFT-Regel | 1 |
| `teacherPreferredTime_lessonInPreferredSlot_rewardsOnce` | 7a Ma MEY MO_1 + `PreferredTimeFact(MEYER, MO_1.id)` | `teacherPreferredTime` rewardsWith 1 |
| `teacherPreferredTime_otherSlot_noReward` | Fact für MO_2 | 0 |
| `subjectSpreadOverWeek_sameSubjectTwiceOnMonday_penalizesOnce` | 7a Ma MEY MO_1 R101, 7a Ma MEY MO_2 R101 | `subjectSpreadOverWeek` 1 |
| `subjectSpreadOverWeek_sameSubjectDifferentDays_noPenalty` | MO_1, TU_1 | 0 |

### 10.2 Übersetzer (Ebene 1, ohne Spring: `SolverTranslatorTest`)

- `toSolution_buildsOneFactInstancePerId`: zwei Stunden mit `timeslotId = 1` → `getTimeslot()` beider Stunden `equals`;
  Wertebereich enthält jede Zeitfenster-ID genau einmal.
- `toSolution_openLessonHasNullTimeslot`: `timeslotId = null` → `timeslot == null`, `room == null`.
- `toResult_timeslotNullMeansRoomNull`: Lösung mit `timeslot = null` und `room = R101` → `Placement(id, null, null)`.
- `toResult_keepsIds`: Lösung mit MO_1/R204 → `Placement(id, 1, 2)`.

### 10.3 Integration (Ebene 3, `./gradlew integrationTest`, `SolveServiceIT`, Basis `IntegrationTestBase`)

Testdaten: `SampleDataLoader.loadSmall()` (Pflichtenheft-Testdaten inkl. aktivem Plan Mo/Di) bzw. `loadFull()`.
Rechenzeit im Test über `SystemConfig.maxSolveSeconds` auf **5** (SMALL) bzw. **10** (FULL) setzen — der Test wartet
mit `Awaitility`-freier Schleife (`Thread.sleep(500)`, höchstens Limit + 10 s) auf `status != SOLVING`.

| Test | Ablauf | erwartet |
|---|---|---|
| `tW8_1_fullSample_terminatesByLimit_noHardConflict_everyLessonAssignedOrOpen_activeEmptyUntilAccept` | FULL laden; aktiven Plan leeren (EMPTY + accept); `createDraft(EMPTY)`; `start(null)`; warten | Lauf endet ≤ Limit + 10 s; je Stunde `(timeslot == null) == (room == null)`; `ConflictChecker` liefert **keinen** Konflikt der Typen `ROOM_/TEACHER_/CLASS_DOUBLE_BOOKED`, `TEACHER_BLOCKED_TIME`, `ROOM_TYPE_MISMATCH`; `GET /api/timetable` weiter leer; nach `accept` gleicht der aktive Plan dem Entwurf Stunde für Stunde |
| `tW9_1_pinnedLessonSurvivesResolve_unpinRemovesFlag` | wie oben bis zum ersten Ergebnis; eine ungepinnte belegte Stunde per `moveLesson` auf ein freies Zeitfenster derselben Klasse → `pinned = true`; zwei weitere ungepinnte Stunden merken; `start(null)`; warten; `setPinned(false)` | die verschobene Stunde liegt unverändert dort; keine Doppelbelegung; nach `setPinned(false)` ist `pinned = false` |
| `allPinned_outputEqualsInput` | SMALL: `createDraft(COPY_ACTIVE)` (alle sieben Stunden konfliktfrei → `pinned = true`); Limit 2 s; `start(null)` | nach dem Lauf identische `timeslot_id/room_id` je Stunde; die offene 9c-Stunde (sync) ist belegt oder offen |
| `start_classWithoutSubject_returnsMasterDataIncomplete` | SMALL; Klasse „10d" ohne Fächer anlegen; `start(null)` | `ConflictException` Slug `master-data-incomplete`, `missing` enthält „Klasse 10d hat kein Fach" |
| `start_pinnedContradiction_refusesRun` | SMALL; Entwurf mit zwei festgelegten 7a-Stunden in MO_1 | Slug `pinned-contradiction`, `conflicts[0].type = CLASS_DOUBLE_BOOKED` |
| `start_whileSolving_returnsSolving` | FULL, Limit 10; zweimal `start` direkt nacheinander | zweiter Aufruf Slug `solving`; `cancel` beendet; Status geht auf `EDITING` |
| `cancel_keepsBestSoFar` | FULL, Limit 30; nach 2 s `cancel` | Status ≤ 5 s später `EDITING`; mindestens eine Stunde belegt; `solveLastScore` gesetzt |
| `startWithClassId_onlyThatClassMoves` (Kann) | FULL nach einem Lauf; alle Stunden ungepinnt; `start(classId = 5a)` | Stunden aller anderen Klassen unverändert |

### 10.4 Synchronität Solver ↔ ConflictChecker

`ConflictCheckerTest` (planning, Ebene 1) nutzt **dieselben** Konstanten und Fälle wie 10.1 (T-W1.2, T-W2.1, T-W3.1,
T-W3.2, Raumart, Pause) und erwartet je Fall genau die Konfliktart, die der Verifier bestraft. Ein Fall, den nur eine
Seite meldet, ist ein Fehler — in der Doku als „synchron zu halten" benannt (Entwurfsdoku Kap. 5).

## 11. Was bewusst nicht gebaut wird

Blockung/Doppelstunde (Soll, wäre `RuleType.DOUBLE_LESSON` + Constraint R4 — Programmänderung, nicht vor R2),
K1 `classGaps` (Kann; Collector-Kombination ungeprüft), Vertretung über den Solver (E-16), Solver-Erklärung von
Konflikten (Enterprise), `ProblemChange`, mehrere parallele Läufe, Zwischenstände in der Datenbank, eine
Solver-Konfiguration je Bundesland (Regeln sind Daten, der Code kennt nur den Typkatalog).

## 12. Abweichungen von den Quellen (mit Grund)

| Quelle sagt | hier | Grund |
|---|---|---|
| `solver.md`: `RuleFact(type, scope, value, severity, weight)`, Regel-Join über Typ und Filter | `RuleFact` trägt `ownerId`; eine Zeile je Klasse bzw. Lehrkraft, vom `RuleResolver` aufgelöst | Jahrgangszeilen (E-47, „Jahrgang vor schulweit") lassen sich so mit einem Gleichheits-Join abbilden; die Auflösung steht an genau einer Stelle und gilt für Solver und ConflictChecker |
| `solver.md`: `withProblemFinder(id -> …)` | `withProblem(problem)` | Problem entsteht im Request-Thread innerhalb der Transaktion; der Finder liefe im Solver-Thread ohne Session (Lazy-Loading-Fehler) |
| Entwurfsdoku Kap. 3/5: `SolveService` im Modul `solver` | `SolveService` in `planning.domain`, `SolverRunner` in `solver` | `SolveService` braucht Repositories (Vorbedingungen, sync, storeResult); `solver` darf laut ArchUnit-Regel 1 keine Persistenz sehen |
| `solver.md`: `SolverTranslator` wandelt in `Assignment`-Records | eigene Records `SolveInput`/`SolveResult` im Modul `solver`; `Assignment` bleibt in `planning` | `substitution` braucht `Assignment` (Folgekonflikte) und darf nicht von `solver` abhängen; zwei kleine Records sind billiger als eine neue Modulabhängigkeit |
| `solver.md`: Pausen als Wertebereich möglich (R3 wirkt im Solver) | Pausen nicht im Wertebereich | Entwurfsdoku Kap. 5 (04.09.) und Seed „Pausenregel hart" (A8): Unterricht in der Pause ist nie erwünscht; R3 bleibt für Handaufbau in die Pause |
| `testen.md`: Artefakt `timefold-solver-test` | `ConstraintVerifier` aus `timefold-solver-core` (`…api.score.stream.test`) | gemessen B1 (STARTSTAND Abschnitt 2): das Artefakt gibt es in 2.6.0 nicht mehr |
| `entscheidungen.md` E-40: `substitutionsThisWeek` | nicht im Solver-Plan | Belastungshinweis am 04.09. gestrichen (Auftrag) — betrifft ohnehin `substitution`, nicht den Solver |

## 13. Geprüft — siehe Nachtrag oben (Stand 05.09., nachgetragen 05.09. abends)

Als dieses Dokument entstand, gab es auf dem Rechner kein Java; nichts darin war kompiliert. **Das ist erledigt.**
Gebaut, kompiliert und im Container gemessen wurde am 05.09.; jeder der vier offenen Punkte hat jetzt eine Antwort:

| offener Punkt | Ergebnis |
|---|---|
| Paketpfade `SolverConfigOverride`/`TerminationConfig` in 2.6.0 | anders als geplant — SV-3 im Nachtrag |
| `groupBy` mit drei Parametern auf `BiConstraintStream` | geht wie beschrieben (`TimetableConstraintProvider`, R1/R2) |
| Startwerte außerhalb des Wertebereichs | Timefold lehnt sie ab — SV-1/SV-2 im Nachtrag, dazu H6/H7 |
| Final-Consumer vor `NOT_SOLVING` | nur beim Zeitablauf, nicht nach `terminateEarly` — SV-8 im Nachtrag |

Belege: `docs/abnahme/solver.md`, `docs/abnahme/planning.md`, `backend/build/reports/tests/`. Was danach noch
auffällt, kommt als Zeile in `07-Konzept/workflows/lernnotizen.md`.
