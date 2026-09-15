package de.wsp3_1.sampledata.domain;

import de.wsp3_1.planning.domain.DraftService;
import de.wsp3_1.planning.domain.DraftSource;
import de.wsp3_1.planning.domain.SolveService;
import de.wsp3_1.shared.domain.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Legt Beispieldaten an: den vollen Bestand fuer {@code POST /api/sample-data} (12 Klassen,
 * 24 Lehrkraefte, 18 Raeume — E-46) und den kleinen Bestand des Pflichtenhefts (Meyer/Schulz/Weber,
 * 7a/8b/9c, R101/R204/P1) als Testfixture fuer die anderen Module.
 *
 * <p><b>Warum SQL statt Repositories?</b> Der Loader schreibt in fremde Tabellen (masterdata,
 * planning) und legt keine eigenen Entities an. Ueber {@link JdbcTemplate} bleibt das Modul frei von
 * den Entity-Klassen der anderen Module — es laesst sich damit unabhaengig von deren Bauzustand
 * uebersetzen und testen. Das Leeren lief ohnehin schon per SQL (docs/VERTRAG.md, Modul
 * {@code sampledata}). Alle Namen sind die Spaltennamen aus V1 und V3.</p>
 *
 * <p><b>Der gefuellte Plan (E-46, V-11).</b> {@link #loadFull()} legt nach den Grunddaten einen leeren
 * Entwurf an, laesst den Solver rechnen und nimmt das Ergebnis an — danach hat {@code GET /api/timetable}
 * Stunden (Checkliste D-1). Dafuer und nur dafuer benutzt {@code sampledata} das Modul {@code planning}
 * (Modultabelle im VERTRAG). Der kleine Bestand bekommt seinen aktiven Plan ueber
 * {@link #loadSmallWithActivePlan()} direkt per SQL — dafuer wird kein Solver gebraucht.</p>
 */
@Service
public class SampleDataLoader {

    /** Anzahl der geladenen Datensaetze; Feldnamen wie {@code SampleDataResult} im Vertrag. */
    public record Counts(int classes, int teachers, int rooms) {
    }

    // ---------------------------------------------------------------- Stundenraster (beide Bestaende)

    /**
     * Mo-Fr, sieben Positionen, Position 3 ist die grosse Pause nach der 2. Stunde (Pflichtenheft W-1).
     *
     * <p>Genau die Zeiten aus dem Vertrag ({@code GET /api/timegrid} in {@code openapi.yaml}), der
     * Checkliste (G-3, T-A1.1 nennt 08:00–08:45, 08:50–09:35, 09:55–10:40, 10:45–11:30) und der
     * Attrappe ({@code frontend/src/mock/store.ts}). Die Zahlen muessen ueberall gleich sein: T-A1.1
     * vergleicht die Zeiten der Lehrkraft-Wochensicht mit dieser Tabelle. Korrigiert 05.09. — vorher
     * lag hier ein anderes Raster ohne Wechselpausen.</p>
     */
    private static final String[] TAGE = {"MO", "TU", "WE", "TH", "FR"};
    private static final int PAUSE_POSITION = 3;
    private static final String[][] ZEITEN = {
            {"08:00", "08:45"},
            {"08:50", "09:35"},
            {"09:35", "09:55"},
            {"09:55", "10:40"},
            {"10:45", "11:30"},
            {"11:50", "12:35"},
            {"12:40", "13:25"},
    };

    // ---------------------------------------------------------------- Voller Bestand (12/24/18)

    /** Name, Kuerzel, geforderte Raumart (leer = jeder Raum), Farbe. */
    private static final String[][] FAECHER = {
            {"Mathematik", "Ma", null, "#1976D2"},
            {"Deutsch", "De", null, "#388E3C"},
            {"Englisch", "En", null, "#7B1FA2"},
            {"Physik", "Ph", "Fachraum Physik", "#F57C00"},
            {"Chemie", "Ch", "Fachraum Chemie", "#C2185B"},
            {"Biologie", "Bi", null, "#00897B"},
            {"Geschichte", "Ge", null, "#5D4037"},
            {"Geographie", "Ek", null, "#0097A7"},
            {"Sport", "Sp", "Sporthalle", "#D32F2F"},
            {"Musik", "Mu", "Musikraum", "#512DA8"},
            {"Kunst", "Ku", null, "#E64A19"},
            {"Religion", "Re", null, "#455A64"},
    };

    /** Wochenstunden je Fach, in derselben Reihenfolge wie {@link #FAECHER}. Summe 30 je Klasse. */
    private static final int[] WOCHENSTUNDEN = {4, 4, 4, 2, 2, 2, 2, 2, 3, 2, 2, 1};

    /**
     * Faecherpaare: eine Lehrkraft unterrichtet zwei Faecher. Sechs Paare mal vier Lehrkraefte = 24,
     * jede Lehrkraft bekommt drei der zwoelf Klassen. Hoechste Last damit 18 Wochenstunden bei einem
     * Deputat von 25 — so meldet der Bestand keine Warnungen.
     */
    private static final int[][] FACHPAARE = {
            {0, 3},   // Mathematik + Physik
            {1, 6},   // Deutsch + Geschichte
            {2, 7},   // Englisch + Geographie
            {4, 5},   // Chemie + Biologie
            {8, 10},  // Sport + Kunst
            {9, 11},  // Musik + Religion
    };

    /** Name und Kuerzel der 24 Lehrkraefte; die Reihenfolge bestimmt, welches Fachpaar sie haben. */
    private static final String[][] LEHRKRAEFTE = {
            {"Albrecht", "ALB"}, {"Baumann", "BAU"}, {"Clausen", "CLA"}, {"Dittmer", "DIT"},
            {"Engel", "ENG"}, {"Fricke", "FRI"}, {"Gerdes", "GER"}, {"Hansen", "HAN"},
            {"Iversen", "IVE"}, {"Jansen", "JAN"}, {"Koester", "KOE"}, {"Lorenzen", "LOR"},
            {"Meinke", "MEI"}, {"Nissen", "NIS"}, {"Ohlsen", "OHL"}, {"Petersen", "PET"},
            {"Quandt", "QUA"}, {"Rickers", "RIC"}, {"Struve", "STR"}, {"Thomsen", "THO"},
            {"Ulrich", "ULR"}, {"Voss", "VOS"}, {"Wieland", "WIE"}, {"Zander", "ZAN"},
    };

    private static final String[][] KLASSEN = {
            {"5a", "5"}, {"5b", "5"}, {"6a", "6"}, {"6b", "6"},
            {"7a", "7"}, {"7b", "7"}, {"8a", "8"}, {"8b", "8"},
            {"9a", "9"}, {"9b", "9"}, {"10a", "10"}, {"10b", "10"},
    };

    /** Zwoelf Klassenraeume und sechs Fachraeume; die Raumarten sind Freitext wie in {@link #FAECHER}. */
    private static final String[][] RAEUME = {
            {"R101", "Klassenraum"}, {"R102", "Klassenraum"}, {"R103", "Klassenraum"},
            {"R104", "Klassenraum"}, {"R105", "Klassenraum"}, {"R106", "Klassenraum"},
            {"R107", "Klassenraum"}, {"R108", "Klassenraum"}, {"R109", "Klassenraum"},
            {"R110", "Klassenraum"}, {"R111", "Klassenraum"}, {"R112", "Klassenraum"},
            {"P1", "Fachraum Physik"}, {"P2", "Fachraum Physik"},
            {"C1", "Fachraum Chemie"},
            {"SH1", "Sporthalle"}, {"SH2", "Sporthalle"},
            {"MU1", "Musikraum"},
    };

    private static final int DEPUTAT = 25;

    // ---------------------------------------------------------------- Leeren

    /**
     * Alles, was ein Beispielbestand fuellt — Kindtabellen zuerst. Bewusst {@code DELETE} statt
     * {@code TRUNCATE ... CASCADE}: {@code user_account.teacher_id} verweist auf {@code teacher},
     * und {@code CASCADE} wuerde {@code user_account} mitleeren — das Konto der Planung waere weg.
     */
    private static final List<String> LEEREN = List.of(
            "exception_event", "changelog_entry", "lesson_exception", "change_event",
            "lesson", "timetable",
            "class_subject", "teacher_subject", "teacher_preferred_timeslot", "teacher_blocked_timeslot",
            "teacher", "subject", "room_blocked_timeslot", "room", "school_class", "timeslot", "holiday");

    /** Tabellen mit {@code bigserial}: nach dem Leeren faengt die Nummerierung wieder bei 1 an. */
    private static final List<String> ZAEHLER = List.of(
            "teacher", "subject", "room", "school_class", "timeslot", "class_subject",
            "timetable", "lesson", "change_event", "lesson_exception", "changelog_entry", "holiday");

    private final JdbcTemplate jdbc;
    private final DraftService draftService;
    private final SolveService solveService;
    private final int planSolveSeconds;

    /**
     * @param planSolveSeconds Rechenzeit fuer den Beispielplan. Zehn Sekunden sind die gemessene
     *                         Vorgabe (VERTRAG, Modul solver: 0 harte Konflikte, 4 von 360 Stunden
     *                         offen). Die Pipeline stellt den Wert kleiner
     *                         ({@code application-ci.properties}): dort soll der Weg belegt werden,
     *                         nicht die Guete des Plans.
     */
    public SampleDataLoader(JdbcTemplate jdbc, DraftService draftService, SolveService solveService,
                            @Value("${app.sample-data.solve-seconds:10}") int planSolveSeconds) {
        this.jdbc = jdbc;
        this.draftService = draftService;
        this.solveService = solveService;
        this.planSolveSeconds = planSolveSeconds;
    }

    /** Sind schon Grunddaten da? Wenn ja, antwortet {@code POST /api/sample-data} ohne {@code replace} mit 409. */
    @Transactional(readOnly = true)
    public boolean hasMasterData() {
        Integer zeilen = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM teacher)
                     + (SELECT count(*) FROM subject)
                     + (SELECT count(*) FROM room)
                     + (SELECT count(*) FROM school_class)
                     + (SELECT count(*) FROM timeslot)
                """, Integer.class);
        return zeilen != null && zeilen > 0;
    }

    /**
     * Leert Grunddaten, Plaene, Ereignisse, Ausnahmen, Verlauf sowie die Konten der Lehrkraefte und der
     * Klassen. Regelwerk, Voreinstellungen, Systemkonfiguration und das Konto der Planung bleiben
     * stehen (E-46).
     */
    @Transactional
    public void clear() {
        // Konten der Lehrkraefte und der Klassen zuerst: sie verweisen auf teacher bzw. school_class
        // (E-50). Ohne diese Zeile scheitert das Leeren von school_class am Fremdschluessel.
        // Das Konto der Planung bleibt.
        jdbc.update("DELETE FROM user_account WHERE role <> 'PLANNING'");

        Set<String> vorhanden = new HashSet<>(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()",
                String.class));
        for (String tabelle : LEEREN) {
            // Alle Tabellen stehen seit V1 bis V5. Die Pruefung gegen information_schema bleibt,
            // damit das Leeren auch gegen eine aeltere Datenbank ohne holiday laeuft.
            if (vorhanden.contains(tabelle)) {
                jdbc.update("DELETE FROM " + tabelle);
            }
        }
        for (String tabelle : ZAEHLER) {
            jdbc.update("ALTER SEQUENCE IF EXISTS " + tabelle + "_id_seq RESTART");
        }
    }

    /**
     * Der volle Beispielbestand samt gefuelltem, angenommenem Plan: 12 Klassen, 24 Lehrkraefte,
     * 18 Raeume, 35 Zeitfenster, die Fachverteilung (30 Wochenstunden je Klasse) und ein aktiver Plan
     * mit 360 Stunden.
     *
     * <p>Erwartet eine geleerte Datenbank; {@code POST /api/sample-data} ruft vorher {@link #clear()}.
     * Der Aufruf dauert deshalb so lange, wie der Solver rechnet ({@code planSolveSeconds}) — die
     * Checkliste raeumt D-1 dafuer bis zu 30 Sekunden ein.</p>
     *
     * <p>{@link DraftService#accept(java.time.LocalDate)} schreibt einen Verlaufseintrag und braucht
     * dafuer das angemeldete Konto. Diese Methode laeuft deshalb nur innerhalb einer angemeldeten
     * Anfrage — im Betrieb {@code POST /api/sample-data}. Wer in einem Test nur die Grunddaten braucht,
     * ruft {@link #loadFullMasterData()}.</p>
     */
    @Transactional
    public Counts loadFull() {
        Counts counts = loadFullMasterData();
        entwurfRechnenUndAnnehmen();
        return counts;
    }

    /**
     * Nur die Grunddaten des vollen Bestands, ohne Plan. Deterministisch — zwei Laeufe ergeben dieselben
     * Daten. Eigener Einstieg fuer Tests, die selbst rechnen wollen (T-W8.1 auf dem vollen Bestand).
     */
    @Transactional
    public Counts loadFullMasterData() {
        stundenraster();
        raeume();
        long[] faecher = faecher();
        long[] lehrkraefte = lehrkraefte(faecher);
        klassenMitFaechern(faecher, lehrkraefte);
        return counts();
    }

    /**
     * Der Weg, den auch eine Person im Browser geht: Entwurf anlegen (W-1), rechnen lassen (W-8),
     * annehmen. Nichts davon ist eine Abkuerzung an der Fachlogik vorbei — dieselben Vorbedingungen,
     * dieselbe Konfliktpruefung, derselbe Verlaufseintrag.
     */
    private void entwurfRechnenUndAnnehmen() {
        draftService.create(DraftSource.EMPTY);
        solveService.solveBlocking(planSolveSeconds);
        draftService.accept(null);
    }

    /**
     * Leeren und neu laden in einer Transaktion. Bricht das Laden ab, bleibt der alte Bestand stehen —
     * getrennte Aufrufe wuerden im Fehlerfall eine leere Datenbank hinterlassen.
     */
    @Transactional
    public Counts loadFullReplacing() {
        clear();
        return loadFull();
    }

    /**
     * Einstieg fuer {@code POST /api/sample-data}: pruefen und laden in EINER Transaktion.
     *
     * <p><b>Warum nicht im Controller?</b> Dort waeren „sind schon Grunddaten da?" und „laden" zwei
     * Transaktionen. Legt jemand dazwischen eine Klasse an, sieht der Controller noch die leere
     * Datenbank, ruft aber {@link #loadFullReplacing()} — und {@link #clear()} nimmt die neue Klasse
     * mit, obwohl niemand „ersetzen" gesagt hat. Hier steht beides in einer Transaktion, und ohne
     * {@code ersetzen} wird ueberhaupt nicht geleert.</p>
     *
     * <p>Zwei gleichzeitige Aufrufe mit {@code ersetzen} brauchen keine zusaetzliche Sperre: beide
     * laufen vollstaendig oder gar nicht, und das eindeutige Kuerzel der Lehrkraefte
     * ({@code teacher.short_name UNIQUE} aus V1) laesst den zweiten scheitern — der
     * {@code ApiExceptionHandler} macht daraus 409 statt 500.</p>
     *
     * @param ersetzen {@code true} = vorhandenen Bestand vorher leeren (Rumpf {@code {"replace": true}})
     * @throws ConflictException {@code data-exists}, wenn ohne {@code ersetzen} schon Grunddaten da sind
     */
    @Transactional
    public Counts load(boolean ersetzen) {
        if (ersetzen) {
            return loadFullReplacing();
        }
        if (hasMasterData()) {
            throw new ConflictException("data-exists",
                    "Die Datenbank enthält bereits Grunddaten. Zum Ersetzen „replace“ setzen.");
        }
        return loadFull();
    }

    /**
     * Der kleine Bestand des Pflichtenhefts als Testfixture — ohne Plan.
     * Reihenfolge der Kennungen wie in den Vertragsbeispielen: Faecher Ma 1, De 2, Ph 3 ·
     * Raeume R101 1, R204 2, P1 3 · Lehrkraefte Meyer 1, Schulz 2, Weber 3 · Klassen 7a 1, 8b 2, 9c 3 ·
     * Zeitfenster Mo 1-7, Di 8-14, Mi 15-21, Do 22-28, Fr 29-35.
     */
    @Transactional
    public Counts loadSmall() {
        kleinerBestand();
        return counts();
    }

    /**
     * Wie {@link #loadSmall()}, zusaetzlich der angenommene Plan aus dem Testdurchlauf:
     * sieben Stunden am Montag und Dienstag (Checkliste P-1). Meyer hat damit montags vier und
     * dienstags eine Stunde; die Mathematikstunde der 9c bleibt offen.
     */
    @Transactional
    public Counts loadSmallWithActivePlan() {
        KleinerBestand bestand = kleinerBestand();
        long plan = einfuegen("INSERT INTO timetable (status, created_at, accepted_at) "
                + "VALUES ('ACTIVE', now(), now()) RETURNING id");
        // Klasse, Fach, Zeitfenster, Raum — genau die sieben Stunden aus dem Pflichtenheft-Durchlauf.
        stunde(plan, bestand, 0, 0, 1, 0);   // 7a Mathematik, Mo 1. Stunde, R101
        stunde(plan, bestand, 0, 1, 2, 0);   // 7a Deutsch,     Mo 2. Stunde, R101
        stunde(plan, bestand, 0, 2, 4, 2);   // 7a Physik,      Mo 3. Stunde, P1
        stunde(plan, bestand, 1, 0, 2, 1);   // 8b Mathematik,  Mo 2. Stunde, R204
        stunde(plan, bestand, 1, 1, 4, 1);   // 8b Deutsch,     Mo 3. Stunde, R204
        stunde(plan, bestand, 1, 0, 5, 1);   // 8b Mathematik,  Mo 4. Stunde, R204
        stunde(plan, bestand, 0, 0, 8, 0);   // 7a Mathematik,  Di 1. Stunde, R101
        return counts();
    }

    // ---------------------------------------------------------------- Bausteine

    /** Die 35 Zeitfenster; Rueckgabe {@code [Tag][Position - 1]} mit der Kennung des Zeitfensters. */
    private long[][] stundenraster() {
        long[][] ids = new long[TAGE.length][ZEITEN.length];
        for (int tag = 0; tag < TAGE.length; tag++) {
            for (int position = 1; position <= ZEITEN.length; position++) {
                ids[tag][position - 1] = einfuegen("""
                        INSERT INTO timeslot (day, position, start_time, end_time, is_break)
                        VALUES (?, ?, ?, ?, ?) RETURNING id
                        """,
                        TAGE[tag], position,
                        LocalTime.parse(ZEITEN[position - 1][0]), LocalTime.parse(ZEITEN[position - 1][1]),
                        position == PAUSE_POSITION);
            }
        }
        return ids;
    }

    private long[] faecher() {
        long[] ids = new long[FAECHER.length];
        for (int i = 0; i < FAECHER.length; i++) {
            ids[i] = einfuegen("""
                    INSERT INTO subject (name, short_name, required_room_type, color)
                    VALUES (?, ?, ?, ?) RETURNING id
                    """, FAECHER[i][0], FAECHER[i][1], FAECHER[i][2], FAECHER[i][3]);
        }
        return ids;
    }

    private long[] raeume() {
        long[] ids = new long[RAEUME.length];
        for (int i = 0; i < RAEUME.length; i++) {
            ids[i] = einfuegen("INSERT INTO room (name, room_type, active) VALUES (?, ?, true) RETURNING id",
                    RAEUME[i][0], RAEUME[i][1]);
        }
        return ids;
    }

    /** Die 24 Lehrkraefte samt ihren zwei Faechern (Fachpaar = Index geteilt durch vier). */
    private long[] lehrkraefte(long[] faecher) {
        long[] ids = new long[LEHRKRAEFTE.length];
        for (int i = 0; i < LEHRKRAEFTE.length; i++) {
            ids[i] = einfuegen("""
                    INSERT INTO teacher (name, short_name, weekly_hours_quota, active)
                    VALUES (?, ?, ?, true) RETURNING id
                    """, LEHRKRAEFTE[i][0], LEHRKRAEFTE[i][1], DEPUTAT);
            for (int fach : FACHPAARE[i / 4]) {
                jdbc.update("INSERT INTO teacher_subject (teacher_id, subject_id) VALUES (?, ?)",
                        ids[i], faecher[fach]);
            }
        }
        return ids;
    }

    /** Zwoelf Klassen mit allen zwoelf Faechern; die Lehrkraft folgt aus Fachpaar und Klassenblock. */
    private void klassenMitFaechern(long[] faecher, long[] lehrkraefte) {
        for (int k = 0; k < KLASSEN.length; k++) {
            long klasse = einfuegen("INSERT INTO school_class (name, grade, active) VALUES (?, ?, true) RETURNING id",
                    KLASSEN[k][0], Integer.parseInt(KLASSEN[k][1]));
            for (int paar = 0; paar < FACHPAARE.length; paar++) {
                // Vier Lehrkraefte je Fachpaar, jede fuer drei Klassen: 0-2, 3-5, 6-8, 9-11.
                long lehrkraft = lehrkraefte[paar * 4 + k / 3];
                for (int fach : FACHPAARE[paar]) {
                    jdbc.update("""
                            INSERT INTO class_subject (class_id, subject_id, teacher_id, weekly_hours)
                            VALUES (?, ?, ?, ?)
                            """, klasse, faecher[fach], lehrkraft, WOCHENSTUNDEN[fach]);
                }
            }
        }
    }

    /** Kennungen der Klasse-Fach-Zeilen und Raeume des kleinen Bestands. */
    private record KleinerBestand(long[] klassenFaecher7a, long[] klassenFaecher8b, long[] raeume, long[] zeitfenster) {
    }

    private KleinerBestand kleinerBestand() {
        long[][] raster = stundenraster();
        long[] zeitfenster = new long[TAGE.length * ZEITEN.length];
        for (int tag = 0; tag < TAGE.length; tag++) {
            System.arraycopy(raster[tag], 0, zeitfenster, tag * ZEITEN.length, ZEITEN.length);
        }

        long ma = einfuegen("INSERT INTO subject (name, short_name, required_room_type, color) "
                + "VALUES ('Mathematik', 'Ma', NULL, '#1976D2') RETURNING id");
        long de = einfuegen("INSERT INTO subject (name, short_name, required_room_type, color) "
                + "VALUES ('Deutsch', 'De', NULL, '#388E3C') RETURNING id");
        long ph = einfuegen("INSERT INTO subject (name, short_name, required_room_type, color) "
                + "VALUES ('Physik', 'Ph', 'Fachraum Physik', '#F57C00') RETURNING id");

        long r101 = einfuegen("INSERT INTO room (name, room_type, active) "
                + "VALUES ('R101', 'Klassenraum', true) RETURNING id");
        long r204 = einfuegen("INSERT INTO room (name, room_type, active) "
                + "VALUES ('R204', 'Klassenraum', true) RETURNING id");
        long p1 = einfuegen("INSERT INTO room (name, room_type, active) "
                + "VALUES ('P1', 'Fachraum Physik', true) RETURNING id");

        long meyer = lehrkraft("Meyer", "MEY", 25, new long[]{ma, ph});
        long schulz = lehrkraft("Schulz", "SCZ", 2, new long[]{ma});
        long weber = lehrkraft("Weber", "WEB", 25, new long[]{de});
        // Schulz ist freitags in der 5. und 6. Stunde gesperrt. Position 3 ist die Pause,
        // die 5. Stunde ist deshalb Position 6 (Kennung 34) und die 6. Stunde Position 7 (35).
        for (int position : new int[]{6, 7}) {
            jdbc.update("INSERT INTO teacher_blocked_timeslot (teacher_id, timeslot_id) VALUES (?, ?)",
                    schulz, raster[4][position - 1]);
        }

        long klasse7a = einfuegen("INSERT INTO school_class (name, grade, active) "
                + "VALUES ('7a', 7, true) RETURNING id");
        long klasse8b = einfuegen("INSERT INTO school_class (name, grade, active) "
                + "VALUES ('8b', 8, true) RETURNING id");
        long klasse9c = einfuegen("INSERT INTO school_class (name, grade, active) "
                + "VALUES ('9c', 9, true) RETURNING id");

        long[] faecher7a = {
                klasseFach(klasse7a, ma, meyer, 2),
                klasseFach(klasse7a, de, weber, 1),
                klasseFach(klasse7a, ph, meyer, 1)};
        long[] faecher8b = {
                klasseFach(klasse8b, ma, meyer, 2),
                klasseFach(klasse8b, de, weber, 1)};
        klasseFach(klasse9c, ma, schulz, 1);

        return new KleinerBestand(faecher7a, faecher8b, new long[]{r101, r204, p1}, zeitfenster);
    }

    private long lehrkraft(String name, String kuerzel, int deputat, long[] faecher) {
        long id = einfuegen("""
                INSERT INTO teacher (name, short_name, weekly_hours_quota, active)
                VALUES (?, ?, ?, true) RETURNING id
                """, name, kuerzel, deputat);
        for (long fach : faecher) {
            jdbc.update("INSERT INTO teacher_subject (teacher_id, subject_id) VALUES (?, ?)", id, fach);
        }
        return id;
    }

    private long klasseFach(long klasse, long fach, long lehrkraft, int wochenstunden) {
        return einfuegen("""
                INSERT INTO class_subject (class_id, subject_id, teacher_id, weekly_hours)
                VALUES (?, ?, ?, ?) RETURNING id
                """, klasse, fach, lehrkraft, wochenstunden);
    }

    /** Eine Stunde des kleinen Plans: Klasse 0 = 7a, 1 = 8b; Zeitfenster und Raum als laufende Nummer. */
    private void stunde(long plan, KleinerBestand bestand, int klasse, int fach, int zeitfensterNummer, int raum) {
        long klasseFach = klasse == 0 ? bestand.klassenFaecher7a()[fach] : bestand.klassenFaecher8b()[fach];
        jdbc.update("""
                INSERT INTO lesson (timetable_id, class_subject_id, pinned, timeslot_id, room_id)
                VALUES (?, ?, true, ?, ?)
                """, plan, klasseFach, bestand.zeitfenster()[zeitfensterNummer - 1], bestand.raeume()[raum]);
    }

    private Counts counts() {
        return new Counts(zaehle("school_class"), zaehle("teacher"), zaehle("room"));
    }

    private int zaehle(String tabelle) {
        Integer zeilen = jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Integer.class);
        return zeilen == null ? 0 : zeilen;
    }

    private long einfuegen(String sql, Object... werte) {
        Long id = jdbc.queryForObject(sql, Long.class, werte);
        if (id == null) {
            throw new IllegalStateException("Das Einfuegen hat keine Kennung geliefert: " + sql);
        }
        return id;
    }
}
