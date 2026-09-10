package de.wsp3_1.changelog.api;

import de.wsp3_1.changelog.domain.ChangelogAction;
import de.wsp3_1.changelog.domain.ChangelogViews.ItemView;
import de.wsp3_1.changelog.domain.ChangelogViews.PageView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Die Datensaetze von {@code GET /api/changelog} — Feldnamen, Reihenfolge und Typen 1:1 wie die Schemata
 * {@code ChangelogItem} und {@code ChangelogPage} in {@code api-contract/openapi.yaml}.
 */
public final class ChangelogDtos {

    private ChangelogDtos() {
    }

    public record ChangelogItem(Long id, Instant timestamp, ChangelogAction action, LocalDate date,
                                Integer position, String classShort, String oldAssignment,
                                String newAssignment, String reasons, String username) {

        public static ChangelogItem of(ItemView view) {
            return new ChangelogItem(view.id(), view.timestamp(), view.action(), view.date(),
                    view.position(), view.classShort(), view.oldAssignment(), view.newAssignment(),
                    view.reasons(), view.username());
        }
    }

    public record ChangelogPage(List<ChangelogItem> items, int page, int size, long total) {

        public static ChangelogPage of(PageView view) {
            return new ChangelogPage(view.items().stream().map(ChangelogItem::of).toList(),
                    view.page(), view.size(), view.total());
        }
    }
}
