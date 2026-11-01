package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchRoundTests {
    private static final BoardCase CASE = BoardCase.GATHER_MATERIALS;
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
                id, CASE, assignee, BoardRules.priorityOf(CASE), 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());
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
    void choose_FourFreeSpiritsTenTickets_EverySpiritBusy() {
        List<BoardTicket> tickets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tickets.add(ticket("gather:" + i, Optional.empty()));
        }
        Board board = board(tickets);
        DispatchRound round = new DispatchRound(board, 0);
        List<DispatchRound.Candidate> spirits = spirits(4);

        run(board, round, spirits, "");

        assertEquals(
                Map.of(new UUID(0, 1), 1, new UUID(0, 2), 1, new UUID(0, 3), 1, new UUID(0, 4), 1),
                board.assignedCounts(),
                "4 свободных духа при 10 заданиях заняты все по одному (INC-83: 4 из 5 стояли при 10 заданиях); "
                        + "проверь выбор свободного духа и счёт взятых в раунде");
        String why = round.why(tickets.get(9), spirits);
        assertTrue(
                why.contains("spirit-1 — держит gather:") && why.contains("spirit-4 — держит gather:"),
                "Причина ожидания называет каждого духа и чем он занят, а не «годные духи заняты»: " + why);
    }

    @Test
    void choose_OwnTicketNotIssued_OwnerTakesOtherWork() {
        List<DispatchRound.Candidate> spirits = spirits(4);
        List<BoardTicket> tickets = new ArrayList<>();
        tickets.add(ticket("own:spirit-1", Optional.of(spirits.get(0).id())));
        for (int i = 0; i < 9; i++) {
            tickets.add(ticket("gather:" + i, Optional.empty()));
        }
        Board board = board(tickets);
        DispatchRound round = new DispatchRound(board, 0);

        run(board, round, spirits, "own:spirit-1");

        assertEquals(
                4,
                board.assignedCounts().size(),
                "Своё задание духа не выдано (ждёт передачу) — дух не простаивает «в ожидании своего», а берёт "
                        + "другое по силам; иначе он стоит, пока висят задания");
    }

    @Test
    void refusal_WetSpiritOnCleanup_IsRefusedUntilItDries() {
        UUID id = new UUID(0, 7);
        DispatchRound.Candidate wet =
                new DispatchRound.Candidate(id, "spirit-wet", null, List.of(), FED, SpiritView.free(), true);
        Board board = new Board(() -> 0);
        BoardTicket cleanup = new BoardTicket(
                "demolish:x",
                BoardCase.DISMANTLE_SHELTER,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(cleanup);
        DispatchRound round = new DispatchRound(board, 0);

        assertTrue(
                round.refusal(cleanup, wet).isPresent(),
                "Дух в воде не дойдёт до стоянки уборки (observe17: s1 в затопленной шахте, «не дошёл до»): "
                        + "уборку надо отдать сухому духу, а не ближайшему по координате");
        DispatchRound.Candidate dry =
                new DispatchRound.Candidate(id, "spirit-dry", null, List.of(), FED, SpiritView.free(), false);
        assertTrue(round.refusal(cleanup, dry).isEmpty(), "Сухой дух уборку берёт: иначе висящее останется навсегда");
    }

    @Test
    void tooLowForCleanup_DeepSpirit_IsRefusedOnlyForCleanup() {
        assertTrue(
                DispatchRound.tooLowForCleanup(BoardCase.DISMANTLE_SHELTER, 55, 69),
                "Дух в затопленной шахте (y=55) не дойдёт до стоянки уборки на y=69: observe17 — 53 «не дошёл до»");
        assertTrue(
                !DispatchRound.tooLowForCleanup(BoardCase.DISMANTLE_SHELTER, 65, 69),
                "Дух на четыре блока ниже стоянки ещё может подойти: порог не должен отсекать соседнюю клетку");
        assertTrue(
                !DispatchRound.tooLowForCleanup(BoardCase.GATHER_MATERIALS, 55, 69),
                "Порог касается только уборки висящего, а не обычных заданий");
    }

    @Test
    void busyWith_NoCurrentScript_NamesPlanStepInsteadOfTask() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("goto", Map.of("x", 1, "y", 64, "z", 2)), new ScriptCall("chop_tree", Map.of())));

        assertEquals(
                "goto",
                DispatchRound.busyWith(null, plan, DispatchRound.BusyMode.WORK),
                "Между шагами скрипт ещё не стартовал: причина ожидания обязана называть шаг плана (goto), "
                        + "а не обезличенное «задача» — иначе видно только «занят: задача»");
        assertEquals(
                "тактика держит (goto)",
                DispatchRound.busyWith(null, plan, DispatchRound.BusyMode.TACTICS),
                "При управлении тактикой причина называет тот же шаг плана: «тактика держит (goto)»");
    }

    @Test
    void why_BusyAndPausedSpirits_NamesEachConcretely() {
        BoardTicket failed = ticket("failed", Optional.empty());
        BoardTicket waiting = ticket("waiting", Optional.empty());
        Board board = board(List.of(failed));
        UUID paused = new UUID(0, 1);
        board.claim("failed", paused, 0);
        board.fail("failed");
        board.submit(waiting);
        List<DispatchRound.Candidate> spirits = List.of(
                new DispatchRound.Candidate(paused, "spirit-1", null, List.of(), FED, SpiritView.free()),
                new DispatchRound.Candidate(
                        new UUID(0, 2), "spirit-2", "chop_tree", List.of(), FED, SpiritView.free()));
        DispatchRound round = new DispatchRound(board, 0);
        round.await(new UUID(0, 3), "несёт ингредиенты для house:2");

        String why = round.why(waiting, spirits);

        assertEquals(
                "spirit-1 — на паузе кейса GATHER_MATERIALS после 1 провалов; spirit-2 — занят: chop_tree",
                why,
                "Причина ожидания по каждому духу: пауза кейса с числом провалов и текущее занятие");
    }
}
