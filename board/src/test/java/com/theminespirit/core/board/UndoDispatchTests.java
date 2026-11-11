package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UndoDispatchTests {
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    private static List<DispatchRound.Candidate> spirits(int count) {
        List<DispatchRound.Candidate> spirits = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            spirits.add(new DispatchRound.Candidate(
                    new UUID(0, i), "spirit-" + i, null, List.of(), FED, SpiritView.free()));
        }
        return spirits;
    }

    private static BoardTicket ticket(String id, Optional<UUID> assignee) {
        return new BoardTicket(
                id,
                BoardCase.GATHER_MATERIALS,
                assignee,
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static Board board(List<BoardTicket> tickets) {
        Board board = new Board(() -> 0);
        tickets.forEach(board::submit);
        return board;
    }

    private static void run(Board board, DispatchRound round, List<DispatchRound.Candidate> spirits, String declined) {
        for (BoardTicket ticket : DispatchRound.order(board.claimable(new StockView(Map.of()), 0))) {
            Optional<DispatchRound.Candidate> chosen = round.choose(ticket, spirits);
            if (chosen.isEmpty() || ticket.id().equals(declined)) {
                continue;
            }
            board.claim(ticket.id(), chosen.orElseThrow().id(), 0);
            round.take(chosen.orElseThrow().id());
        }
    }

    @Test
    void defaults_FromClasspath_UndoLimitsInTable() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(
                2,
                config.intervals().get("maxUndoSpirits"),
                "На откатах одновременно не больше двух духов: остальные нужны работам этапа (приёмка 11.11 — "
                        + "пять духов четыре часа висели на брошенных арендах)");
        assertEquals(
                1200,
                config.intervals().get("undoNoProgressTicks"),
                "Откат без продвижения минуту — провал с причиной и ключом: иначе дух стоит в одном скрипте, "
                        + "пока аренда не сменит ключ");
    }

    @Test
    void constructor_ZeroUndoSpirits_FailsNamingReason() {
        Board board = board(List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new DispatchRound(board, 0, 0),
                "Предел духов на откатах ноль обязан падать сразу, а не молча запрещать откаты");
        assertTrue(
                error.getMessage().contains("хотя бы одному духу"),
                "Предел ноль запретил бы откаты навсегда — брошенные аренды никто не уберёт: " + error.getMessage());
    }

    @Test
    void choose_FiveSpiritsAllUndoUnreachable_OnlyLimitOnUndoRestTakeStageWork() {
        List<DispatchRound.Candidate> spirits = spirits(5);
        List<BoardTicket> tickets = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            tickets.add(undoTicket("graph:event:undo_lease:undo:lease-" + i + "#0,70,0:1"));
        }
        for (int i = 1; i <= 3; i++) {
            tickets.add(ticket("graph:craft_stick:" + i, Optional.empty()));
        }
        Board board = board(tickets);
        DispatchRound round = new DispatchRound(board, 0, 2);
        List<DispatchRound.Candidate> tooled = new ArrayList<>();
        for (DispatchRound.Candidate spirit : spirits) {
            tooled.add(new DispatchRound.Candidate(
                    spirit.id(), spirit.name(), null, List.of("minecraft:stone_pickaxe"), FED, SpiritView.free()));
        }

        run(board, round, tooled, "");

        int undoAssigned = 0;
        int workAssigned = 0;
        for (BoardTicket ticket : board.all()) {
            if (board.statusOf(ticket.id()).orElse(null) != BoardStatus.ASSIGNED) {
                continue;
            }
            if (ticket.boardCase() == BoardCase.ACTIVITY_UNDO) {
                undoAssigned++;
            } else {
                workAssigned++;
            }
        }
        assertEquals(
                2,
                undoAssigned,
                "На откатах одновременно не больше maxUndoSpirits (2) духов: иначе пять духов стоят без прогресса "
                        + "на недостижимых арендах, а работы этапа ждут (приёмка 11.11)");
        assertEquals(
                3,
                workAssigned,
                "Остальные духи заняты работами этапа, а не простаивают: пять духов, две аренды отката и три "
                        + "работы — заняты все пять");
        assertTrue(
                round.why(tickets.get(2), tooled).contains("откатов уже 2"),
                "Причина ожидания третьего отката называет предел и работу этапа: "
                        + round.why(tickets.get(2), tooled));
    }

    private static BoardTicket undoTicket(String id) {
        return new BoardTicket(
                id,
                BoardCase.ACTIVITY_UNDO,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.ACTIVITY_UNDO),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
