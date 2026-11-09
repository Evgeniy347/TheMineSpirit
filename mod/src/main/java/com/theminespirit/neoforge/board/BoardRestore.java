package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.neoforge.net.BuildFingerprint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BoardRestore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BoardRestore() {}

    static void restore(
            Board board,
            @Nullable BoardStateStore store,
            Map<String, TicketTarget> targets,
            BoardTicketDesk tickets,
            JournalService journal,
            BoardSources sources) {
        if (store != null) {
            BoardState restored = store.state();
            BoardState byGraph = restored.withoutLegacy(Set.of(GraphTickets.PREFIX));
            int dropped = restored.entries().size() - byGraph.entries().size();
            if (dropped > 0) {
                LOGGER.warn(
                        "[board] при загрузке снято {} заданий мимо графа: их ставят заново источники графа", dropped);
            }
            BoardState owned = byGraph.withoutActivities();
            int activities = byGraph.entries().size() - owned.entries().size();
            if (activities > 0) {
                LOGGER.info(
                        "[board] при загрузке снято {} занятий: духи потеряли задачи перезапуска, "
                                + "источники графа поставят их заново",
                        activities);
            }
            if (!owned.isEmpty()) {
                board.restore(owned);
            }
            cancelRestoredRescues(board);
            String build = BuildFingerprint.current();
            if (!build.equals(store.build())) {
                List<String> reset = tickets.resetForNewRules();
                if (!reset.isEmpty()) {
                    LOGGER.warn(
                            "[incident] сборка мода сменилась ({} → {}): сброшено {} заданий старых правил {},"
                                    + " источники поставят их заново",
                            store.build().isEmpty() ? "?" : store.build(),
                            build,
                            reset.size(),
                            reset);
                }
                store.markBuild(build);
                store.update(board.snapshot());
            }
            BoardTargetsNbt.restore(store, board, targets, sources);
        }
        journal.restore(board.ledger());
    }

    private static void cancelRestoredRescues(Board board) {
        board.all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.RESCUE_SPIRIT)
                .forEach(ticket -> board.cancel(ticket.id()));
    }
}
