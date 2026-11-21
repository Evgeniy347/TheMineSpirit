package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.Pair;
import static com.theminespirit.core.board.graph.AllocationCases.boardTicket;
import static com.theminespirit.core.board.graph.AllocationCases.candidates;
import static com.theminespirit.core.board.graph.AllocationCases.community;
import static com.theminespirit.core.board.graph.AllocationCases.dispatch;
import static com.theminespirit.core.board.graph.AllocationCases.id;
import static com.theminespirit.core.board.graph.AllocationCases.only;
import static com.theminespirit.core.board.graph.AllocationCases.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.StockView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphAllocationGroupATests {
    @Test
    void case01_LogsNeeded_ChopGivenToOneSpirit() {
        GraphWorld world = community(3).with("oak_log", 0);
        GraphTickets.Result result = tickets(world);

        List<GraphTickets.Ticket> chop = only(result, "chop_oak_log");
        assertEquals(
                1,
                chop.size(),
                "Нужда во брёвнах — одна заявка на рубку дуба, а не по заявке на духа:  Иначе тест case01_LogsNeeded_ChopGivenToOneSpirit перестанет ловить эту поломку. При смене case01_LogsNeeded_ChopGivenToOneSpirit проверь это утверждение."
                        + result.post());
        assertEquals(
                1,
                dispatch(world, chop).size(),
                "Одну заявку на рубку берёт ровно один дух, а не все трое Иначе тест case01_LogsNeeded_ChopGivenToOneSpirit перестанет ловить эту поломку. При смене case01_LogsNeeded_ChopGivenToOneSpirit проверь это утверждение.");
    }

    @Test
    void case02_TwoNeedsTwoSpirits_DifferentWork() {
        GraphWorld world = community(2).with("oak_log", 0).with("cobblestone", 0);
        GraphTickets.Result result = tickets(world);

        List<GraphTickets.Ticket> works = new ArrayList<>(only(result, "chop_oak_log"));
        works.addAll(only(result, "mine_cobble"));
        List<Pair> pairs = dispatch(world, works);
        assertEquals(
                2,
                pairs.size(),
                "Две нужды и два духа — обе работы выданы:  Иначе тест case02_TwoNeedsTwoSpirits_DifferentWork перестанет ловить эту поломку. При смене case02_TwoNeedsTwoSpirits_DifferentWork проверь это утверждение."
                        + pairs);
        assertEquals(
                2,
                pairs.stream().map(Pair::ticket).distinct().count(),
                "Духи получили две разные работы, а не одну на двоих:  Иначе тест case02_TwoNeedsTwoSpirits_DifferentWork перестанет ловить эту поломку. При смене case02_TwoNeedsTwoSpirits_DifferentWork проверь это утверждение."
                        + pairs);
        assertEquals(
                2,
                pairs.stream().map(Pair::spirit).distinct().count(),
                "Работы у разных духов:  Иначе тест case02_TwoNeedsTwoSpirits_DifferentWork перестанет ловить эту поломку. При смене case02_TwoNeedsTwoSpirits_DifferentWork проверь это утверждение."
                        + pairs);
    }

    @Test
    void case03_AssignedWork_NotGivenToSecondSpirit() {
        GraphWorld world = community(2).with("oak_log", 0);
        Board board = new Board(() -> 0);
        GraphTickets.Ticket chop = only(tickets(world), "chop_oak_log").get(0);
        BoardTicket ticket = boardTicket(chop);
        board.submit(ticket);
        board.claim(ticket.id(), id("spirit-1"), 0);
        assertFalse(
                board.claimable(new StockView(world.stock), 0).stream()
                        .anyMatch(open -> open.id().equals(ticket.id())),
                "Занятая работа не попадает в очередь выдачи: второй дух её не получит Иначе тест case03_AssignedWork_NotGivenToSecondSpirit перестанет ловить эту поломку. При смене case03_AssignedWork_NotGivenToSecondSpirit проверь это утверждение.");
        assertEquals(
                BoardOutcome.TAKEN_BY_OTHER,
                board.claim(ticket.id(), id("spirit-2"), 0),
                "Прямая заявка второго духа на занятую работу отклонена с причиной: её держит spirit-1 Иначе тест case03_AssignedWork_NotGivenToSecondSpirit перестанет ловить эту поломку. При смене case03_AssignedWork_NotGivenToSecondSpirit проверь это утверждение.");
        assertTrue(
                dispatch(board, world).isEmpty(),
                "Повторный раунд не выдаёт занятую работу никому:  Иначе тест case03_AssignedWork_NotGivenToSecondSpirit перестанет ловить эту поломку. При смене case03_AssignedWork_NotGivenToSecondSpirit проверь это утверждение."
                        + ticket);
    }

    @Test
    void case04_ToolWork_GoesToAxeHolder() {
        GraphWorld world = community(2);
        world.tools("spirit-1", 1);
        world.tools("spirit-2", -1);
        GraphTickets.Ticket chop = only(tickets(world), "chop_oak_log").get(0);
        Board board = new Board(() -> 0);
        board.submit(boardTicket(chop));
        DispatchRound round = new DispatchRound(board, 0);
        List<DispatchRound.Candidate> spirits = candidates(world);
        Optional<DispatchRound.Candidate> chosen =
                round.choose(boardTicket(chop), List.of(spirits.get(1), spirits.get(0)));
        assertEquals(
                "spirit-1",
                chosen.orElseThrow().name(),
                "Рубку получает дух с топором, даже если второй в списке выдачи раньше:  Иначе тест case04_ToolWork_GoesToAxeHolder перестанет ловить эту поломку. При смене case04_ToolWork_GoesToAxeHolder проверь это утверждение."
                        + chosen);
    }

    @Test
    void case05_NoTool_GivenToBareHandsWhenToolHolderBusy() {
        GraphWorld world = community(2);
        world.tools("spirit-1", 1);
        world.tools("spirit-2", -1);
        GraphTickets.Ticket chop = only(tickets(world), "chop_oak_log").get(0);
        BoardTicket ticket = boardTicket(chop);
        Board board = new Board(() -> 0);
        board.submit(ticket);
        DispatchRound round = new DispatchRound(board, 0);
        List<DispatchRound.Candidate> spirits = candidates(world);
        assertTrue(
                round.refusal(ticket, spirits.get(1)).isEmpty(),
                "Дух без топора тоже берёт рубку и рубит руками: инструмент — предпочтение, не запрет (владелец 05.11) Иначе тест case05_NoTool_GivenToBareHandsWhenToolHolderBusy перестанет ловить эту поломку. При смене case05_NoTool_GivenToBareHandsWhenToolHolderBusy проверь это утверждение.");
        assertTrue(
                round.refusal(ticket, spirits.get(0)).isEmpty(),
                "Дух с топором берёт рубку быстрее: топор остаётся предпочтением Иначе тест case05_NoTool_GivenToBareHandsWhenToolHolderBusy перестанет ловить эту поломку. При смене case05_NoTool_GivenToBareHandsWhenToolHolderBusy проверь это утверждение.");
    }
}
