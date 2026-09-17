package de.wsp3_1.shared.domain;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DatabaseCleaner {

    private static final List<String> LEEREN = List.of(
            "exception_event", "changelog_entry", "lesson_exception", "change_event",
            "lesson", "timetable",
            "class_subject", "teacher_subject", "teacher_preferred_timeslot", "teacher_blocked_timeslot",
            "teacher", "subject", "room_blocked_timeslot", "room", "school_class", "timeslot", "holiday");

    private static final List<String> ZAEHLER = List.of(
            "teacher", "subject", "room", "school_class", "timeslot", "class_subject",
            "timetable", "lesson", "change_event", "lesson_exception", "changelog_entry", "holiday");

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clear() {
        jdbc.update("DELETE FROM user_account WHERE role <> 'PLANNING'");
        Set<String> vorhanden = new HashSet<>(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()",
                String.class));
        for (String tabelle : LEEREN) {
            if (vorhanden.contains(tabelle)) {
                jdbc.update("DELETE FROM " + tabelle);
            }
        }
        for (String tabelle : ZAEHLER) {
            jdbc.update("ALTER SEQUENCE IF EXISTS " + tabelle + "_id_seq RESTART");
        }
    }
}