package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.Pair;
import static com.theminespirit.core.board.graph.AllocationCases.boardTicket;
import static com.theminespirit.core.board.graph.AllocationCases.candidates;
import static com.theminespirit.core.board.graph.AllocationCases.community;
import static com.theminespirit.core.board.graph.AllocationCases.dispatch;
import static com.theminespirit.core.board.graph.AllocationCases.id;
import static com.theminespirit.core.board.graph.AllocationCases.only;
import static com.theminespirit.core.board.graph.AllocationCases.spirit;
import static com.theminespirit.core.board.graph.AllocationCases.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                "Нужда во брёвнах — одна заявка на рубку дуба, а не по заявке на духа: " + result.post());
        assertEquals(1, dispatch(world, chop).size(), "Одну заявку на рубку берёт ровно один дух, а не все трое");
    }

    @Test
    void case02_TwoNeedsTwoSpirits_DifferentWork() {
        GraphWorld world = community(2).with("oak_log", 0).with("cobblestone", 0);
        GraphTickets.Result result = tickets(world);

        List<GraphTickets.Ticket> works = new ArrayList<>(only(result, "chop_oak_log"));
        works.addAll(only(result, "mine_cobble"));
        List<Pair> pairs = dispatch(world, works);

        assertEquals(2, pairs.size(), "Две нужды и два духа — обе работы выданы: " + pairs);
        assertEquals(
                2,
                pairs.stream().map(Pair::ticket).distinct().count(),
                "Духи получили две разные работы, а не одну на двоих: " + pairs);
        assertEquals(2, pairs.stream().map(Pair::spirit).distinct().count(), "Работы у разных духов: " + pairs);
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
                "Занятая работа не попадает в очередь выдачи: второй дух её не получит");
        assertEquals(
                BoardOutcome.TAKEN_BY_OTHER,
                board.claim(ticket.id(), id("spirit-2"), 0),
                "Прямая заявка второго духа на занятую работу отклонена с причиной: её держит spirit-1");
        assertTrue(dispatch(board, world).isEmpty(), "Повторный раунд не выдаёт занятую работу никому: " + ticket);
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
                "Рубку получает дух с топором, даже если второй в списке выдачи раньше: " + chosen);
    }

    @Test
    void case05_NoTool_RefusedWhileToolHolderExists() {
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
                round.refusal(ticket, spirits.get(1)).orElse("").contains("нет инструмента _axe"),
                "Дух без топора не берёт рубку: причина называет инструмент");
        assertTrue(
                round.refusal(ticket, spirits.get(0)).isEmpty(),
                "Дух с топором берёт рубку: иначе работа висит на доске");
    }

    @Test
    void case06_AllNeedsClosed_ClearingNotChopping() {
        GraphWorld world = community(2)
                .with("oak_log", 64)
                .with("oak_planks", 64)
                .with("stick", 64)
                .with("cobblestone", 64)
                .with("dirt", 64)
                .with("coal", 64)
                .with("torch", 64);
        world.graph.buildings().values().forEach(building -> world.buildings.put(building.id(), Built.BUILT));
        world.buildings.put("clearing", Built.PLANNED);
        world.noWorld("tree:oak").noWorld("tree:jungle");
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().anyMatch(ticket -> "graph:building:clearing".equals(ticket.id())),
                "Нужды закрыты — на доске полезная расчистка: " + result.post());
        assertTrue(
                result.post().stream().noneMatch(ticket -> ticket.node().startsWith("chop_")),
                "Нужды закрыты — общей рубки на доске нет: " + result.post());
    }

    @Test
    void case07_LogsCollected_ChopRemoved() {
        GraphWorld empty = community(2).with("oak_log", 0);
        GraphWorld stocked = community(2).with("oak_log", 64);

        assertFalse(
                only(tickets(empty), "chop_oak_log").isEmpty(),
                "Брёвен нет — рубка стоит: " + tickets(empty).post());
        assertTrue(
                only(tickets(stocked), "chop_oak_log").isEmpty(),
                "Брёвна набраны — рубка снимается со всех: " + tickets(stocked).post());
    }

    @Test
    void case08_MoreSpiritsThanWorks_NoDuplicateWork() {
        GraphWorld world = community(5).with("oak_log", 0);
        GraphTickets.Result result = tickets(world);
        List<GraphTickets.Ticket> works = new ArrayList<>(only(result, "chop_oak_log"));
        works.addAll(only(result, "mine_cobble"));

        List<Pair> pairs = dispatch(world, works);

        assertEquals(2, pairs.size(), "Работ две — заняты двое, лишние не получают дублей: " + pairs);
        assertEquals(
                2, pairs.stream().map(Pair::ticket).distinct().count(), "Ни одна работа не выдана дважды: " + pairs);
        assertEquals(2, pairs.stream().map(Pair::spirit).distinct().count(), "Дубли духов тоже недопустимы");
    }

    @Test
    void case09_StuckSpirit_OnlyRescueNotOrdinaryWork() {
        GraphWorld world = community(2).with("oak_log", 0);
        GraphTickets.Ticket chop = only(tickets(world), "chop_oak_log").get(0);
        Board board = new Board(() -> 0);
        board.submit(boardTicket(chop));
        BoardTicket rescue = new BoardTicket(
                "rescue:" + id("spirit-1"),
                BoardCase.RESCUE_SPIRIT,
                Optional.of(id("spirit-1")),
                BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                0,
                1,
                "rescue:" + id("spirit-1"),
                List.of(),
                Map.of());
        board.submit(rescue);
        board.stuck().mark(id("spirit-1"), 140, "не двигается с тика 100");

        List<Pair> pairs = dispatch(board, world);

        assertTrue(
                pairs.stream()
                        .anyMatch(pair -> "spirit-1".equals(pair.spirit())
                                && pair.ticket().startsWith("rescue:")),
                "Застрявшему — только спасение: " + pairs);
        assertTrue(
                pairs.stream()
                        .noneMatch(pair -> "spirit-1".equals(pair.spirit())
                                && pair.ticket().startsWith("graph:chop")),
                "Обычную работу застрявший не получает: " + pairs);
        DispatchRound round = new DispatchRound(board, 0);
        assertTrue(
                round.refusal(boardTicket(chop), candidates(world).get(0))
                        .orElse("")
                        .contains("не двигается"),
                "Отказ обычной работы называет застревание, а не молчит");
    }

    @Test
    void case10_RepeatedFailure_SpiritGetsAnotherWork() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        BoardTicket chop = boardTicket(
                only(tickets(community(1).with("oak_log", 0)), "chop_oak_log").get(0));
        BoardTicket clearing = new BoardTicket(
                "building:clearing",
                BoardCase.CLEAR_GROUNDS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.CLEAR_GROUNDS),
                0,
                1,
                "building:clearing",
                List.of(),
                Map.of());
        board.submit(chop);
        board.claim(chop.id(), id("spirit-1"), now[0]);
        board.fail(chop.id(), new StockView(Map.of()), "провал движения");
        for (int attempt = 0; attempt < BoardTuning.caseFailLimit() - 1; attempt++) {
            now[0] += board.casePauseTicks(id("spirit-1"), BoardCase.GATHER_MATERIALS);
            BoardTicket repeat = new BoardTicket(
                    "gather:" + attempt,
                    BoardCase.GATHER_MATERIALS,
                    Optional.empty(),
                    BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                    now[0],
                    1,
                    "gather:" + attempt,
                    List.of(),
                    Map.of());
            board.submit(repeat);
            board.claim(repeat.id(), id("spirit-1"), now[0]);
            board.fail(repeat.id(), new StockView(Map.of()), "провал движения");
        }
        board.submit(clearing);

        List<Pair> pairs = dispatch(board, community(1));

        assertTrue(
                board.casePauseTicks(id("spirit-1"), BoardCase.GATHER_MATERIALS) > 0,
                "Серия провалов поставила кейс добычи на паузу");
        assertTrue(
                pairs.stream().anyMatch(pair -> "building:clearing".equals(pair.ticket())),
                "После серии провалов дух получает другую работу (расчистку): " + pairs);
        assertTrue(
                pairs.stream().noneMatch(pair -> pair.ticket().startsWith("graph:chop")),
                "Проваленную рубку дух снова не получает: " + pairs);
    }

    @Test
    void case24_BusySpirit_NoSecondWorkInRound() {
        GraphWorld world = community(2).with("oak_log", 0);
        GraphTickets.Result result = tickets(world);
        List<GraphTickets.Ticket> works = new ArrayList<>(only(result, "chop_oak_log"));
        works.addAll(only(result, "mine_cobble"));
        Board board = new Board(() -> 0);
        works.stream().map(AllocationCases::boardTicket).forEach(board::submit);
        List<DispatchRound.Candidate> spirits = candidates(world);
        DispatchRound round = new DispatchRound(board, 0);
        List<Pair> pairs = new ArrayList<>();
        for (BoardTicket ticket : DispatchRound.order(board.claimable(new StockView(world.stock), 0))) {
            Optional<DispatchRound.Candidate> chosen = round.choose(ticket, spirits);
            if (chosen.isEmpty()) {
                continue;
            }
            DispatchRound.Candidate spirit = chosen.orElseThrow();
            board.claim(ticket.id(), spirit.id(), 0);
            round.take(spirit.id());
            pairs.add(new Pair(spirit.name(), ticket.id()));
        }

        assertEquals(
                pairs.size(),
                pairs.stream().map(Pair::spirit).distinct().count(),
                "Один дух за раунд берёт не больше одной работы: " + pairs);
    }
}
