package de.wsp3_1.changelog.domain;

import de.wsp3_1.changelog.domain.ChangelogViews.ItemView;
import de.wsp3_1.changelog.domain.ChangelogViews.NewEntry;
import de.wsp3_1.changelog.domain.ChangelogViews.PageView;
import de.wsp3_1.changelog.domain.ChangelogViews.Query;
import de.wsp3_1.changelog.persistence.ChangelogEntry;
import de.wsp3_1.changelog.persistence.ChangelogEntryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Der Aenderungsverlauf (W-7): schreiben und lesen, mehr nicht. Es gibt bewusst kein Aendern und kein
 * Loeschen — weder hier noch am Endpunkt (T-W7.1).
 *
 * <p><b>Warum zwei Zugriffsarten?</b> Geschrieben wird ueber die Entity {@link ChangelogEntry}: dort
 * stehen Spaltennamen und Typen an einer Stelle. Gelesen wird mit {@link JdbcTemplate}, weil die Antwort
 * die Klassenbezeichnung aus {@code school_class} und den Benutzernamen aus {@code user_account}
 * braucht — beides gehoert anderen Modulen, die {@code changelog} laut Modultabelle
 * ({@code docs/VERTRAG.md} Abschnitt 4) nicht benutzen darf. Eine Abfrage ueber die Tabellengrenze ist
 * derselbe Weg, den {@code sampledata} und {@code planning} fuer fremde Tabellen gehen.</p>
 */
@Service
public class ChangelogService {

    /** Grenzen aus dem Vertrag ({@code GET /api/changelog}, Parameter {@code page} und {@code size}). */
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 500;

    private final ChangelogEntryRepository entries;
    private final JdbcTemplate jdbc;

    public ChangelogService(ChangelogEntryRepository entries, JdbcTemplate jdbc) {
        this.entries = entries;
        this.jdbc = jdbc;
    }

    /** Breite der Belegungsspalten aus der Migration V4 — laenger nimmt die Datenbank nicht an. */
    private static final int ASSIGNMENT_LENGTH = 255;

    /** Haelt eine Aenderung fest. Der Zeitstempel entsteht hier, nicht beim Aufrufer. */
    @Transactional
    public void record(NewEntry entry) {
        entries.save(new ChangelogEntry(Instant.now(), entry.action(), entry.date(), entry.position(),
                cut(entry.oldAssignment()), cut(entry.newAssignment()),
                entry.reasons() == null ? "" : entry.reasons(),
                entry.userId(), entry.lessonId(), entry.classId(), entry.teacherId()));
    }

    /**
     * Kuerzt eine zu lange Belegung auf die Spaltenbreite. „Meyer / R101" bleibt weit darunter; die
     * Grenze faengt nur ab, dass ein sehr langer Name den ganzen Vorgang scheitern liesse.
     *
     * <p>Die Anlaesse werden <b>nicht</b> gekuerzt: sie stehen seit Migration V6 in einer Spalte ohne
     * Laengengrenze. Vorher fielen ab fuenf Anlaessen ganze spaetere Anlaesse weg, obwohl der Vertrag
     * alle verlangt.</p>
     */
    private static String cut(String text) {
        return text == null || text.length() <= ASSIGNMENT_LENGTH ? text
                : text.substring(0, ASSIGNMENT_LENGTH - 1) + "…";
    }

    /** Eine Seite des Verlaufs, neueste zuerst. */
    @Transactional(readOnly = true)
    public PageView find(Query query) {
        List<String> conditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (query.from() != null) {
            conditions.add("e.date >= ?");
            values.add(Date.valueOf(query.from()));
        }
        if (query.to() != null) {
            conditions.add("e.date <= ?");
            values.add(Date.valueOf(query.to()));
        }
        if (query.classId() != null) {
            conditions.add("e.class_id = ?");
            values.add(query.classId());
        }
        if (query.teacherId() != null) {
            conditions.add("e.teacher_id = ?");
            values.add(query.teacherId());
        }
        if (query.lessonId() != null) {
            conditions.add("e.lesson_id = ?");
            values.add(query.lessonId());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM changelog_entry e" + where, Long.class, values.toArray());

        List<Object> pageValues = new ArrayList<>(values);
        pageValues.add(query.size());
        pageValues.add((long) query.page() * query.size());
        // Zweites Sortierfeld id: zwei Eintraege koennen denselben Zeitstempel tragen, und ohne feste
        // Reihenfolge liefert dieselbe Seite bei zwei Aufrufen verschiedene Zeilen.
        List<ItemView> items = jdbc.query("""
                SELECT e.id, e.timestamp, e.action, e.date, e.position, c.name AS class_short,
                       e.old_assignment, e.new_assignment, e.reasons, u.username
                FROM changelog_entry e
                JOIN user_account u ON u.id = e.user_id
                LEFT JOIN school_class c ON c.id = e.class_id
                """ + where + """
                 ORDER BY e.timestamp DESC, e.id DESC
                 LIMIT ? OFFSET ?
                """, (rs, row) -> new ItemView(
                        rs.getLong("id"),
                        rs.getTimestamp("timestamp").toInstant(),
                        ChangelogAction.valueOf(rs.getString("action")),
                        rs.getDate("date") == null ? null : rs.getDate("date").toLocalDate(),
                        rs.getObject("position") == null ? null : rs.getInt("position"),
                        rs.getString("class_short"),
                        rs.getString("old_assignment"),
                        rs.getString("new_assignment"),
                        rs.getString("reasons"),
                        rs.getString("username")),
                pageValues.toArray());

        return new PageView(items, query.page(), query.size(), total == null ? 0 : total);
    }
}
