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
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphAllocationGroupAPart2Tests {
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
                "Нужды закрыты — на доске полезная расчистка:  Иначе тест case06_AllNeedsClosed_ClearingNotChopping перестанет ловить эту поломку. При смене case06_AllNeedsClosed_ClearingNotChopping проверь это утверждение."
                        + result.post());
        assertTrue(
                result.post().stream().noneMatch(ticket -> ticket.node().startsWith("chop_")),
                "Нужды закрыты — общей рубки на доске нет:  Иначе тест case06_AllNeedsClosed_ClearingNotChopping перестанет ловить эту поломку. При смене case06_AllNeedsClosed_ClearingNotChopping проверь это утверждение."
                        + result.post());
    }

    @Test
    void case07_LogsCollected_ChopRemoved() {
        GraphWorld empty = community(2).with("oak_log", 0);
        GraphWorld stocked = community(2).with("oak_log", 64);
        assertFalse(
                only(tickets(empty), "chop_oak_log").isEmpty(),
                "Брёвен нет — рубка стоит:  Иначе тест case07_LogsCollected_ChopRemoved перестанет ловить эту поломку. При смене case07_LogsCollected_ChopRemoved проверь это утверждение."
                        + tickets(empty).post());
        assertTrue(
                only(tickets(stocked), "chop_oak_log").isEmpty(),
                "Брёвна набраны — рубка снимается со всех:  Иначе тест case07_LogsCollected_ChopRemoved перестанет ловить эту поломку. При смене case07_LogsCollected_ChopRemoved проверь это утверждение."
                        + tickets(stocked).post());
    }

    @Test
    void case08_MoreSpiritsThanWorks_NoDuplicateWork() {
        GraphWorld world = community(5).with("oak_log", 0);
        GraphTickets.Result result = tickets(world);
        List<GraphTickets.Ticket> works = new ArrayList<>(only(result, "chop_oak_log"));
        works.addAll(only(result, "mine_cobble"));
        List<Pair> pairs = dispatch(world, works);
        assertEquals(
                2,
                pairs.size(),
                "Работ две — заняты двое, лишние не получают дублей:  Иначе тест case08_MoreSpiritsThanWorks_NoDuplicateWork перестанет ловить эту поломку. При смене case08_MoreSpiritsThanWorks_NoDuplicateWork проверь это утверждение."
                        + pairs);
        assertEquals(
                2,
                pairs.stream().map(Pair::ticket).distinct().count(),
                "Ни одна работа не выдана дважды:  Иначе тест case08_MoreSpiritsThanWorks_NoDuplicateWork перестанет ловить эту поломку. При смене case08_MoreSpiritsThanWorks_NoDuplicateWork проверь это утверждение."
                        + pairs);
        assertEquals(
                2,
                pairs.stream().map(Pair::spirit).distinct().count(),
                "Дубли духов тоже недопустимы Иначе тест case08_MoreSpiritsThanWorks_NoDuplicateWork перестанет ловить эту поломку. При смене case08_MoreSpiritsThanWorks_NoDuplicateWork проверь это утверждение.");
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
                "Застрявшему — только спасение:  Иначе тест case09_StuckSpirit_OnlyRescueNotOrdinaryWork перестанет ловить эту поломку. При смене case09_StuckSpirit_OnlyRescueNotOrdinaryWork проверь это утверждение."
                        + pairs);
        assertTrue(
                pairs.stream()
                        .noneMatch(pair -> "spirit-1".equals(pair.spirit())
                                && pair.ticket().startsWith("graph:chop")),
                "Обычную работу застрявший не получает:  Иначе тест case09_StuckSpirit_OnlyRescueNotOrdinaryWork перестанет ловить эту поломку. При смене case09_StuckSpirit_OnlyRescueNotOrdinaryWork проверь это утверждение."
                        + pairs);
        DispatchRound round = new DispatchRound(board, 0);
        assertTrue(
                round.refusal(boardTicket(chop), candidates(world).get(0))
                        .orElse("")
                        .contains("не двигается"),
                "Отказ обычной работы называет застревание, а не молчит Иначе тест case09_StuckSpirit_OnlyRescueNotOrdinaryWork перестанет ловить эту поломку. При смене case09_StuckSpirit_OnlyRescueNotOrdinaryWork проверь это утверждение.");
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
                "Серия провалов поставила кейс добычи на паузу Иначе тест case10_RepeatedFailure_SpiritGetsAnotherWork перестанет ловить эту поломку. При смене case10_RepeatedFailure_SpiritGetsAnotherWork проверь это утверждение.");
        assertTrue(
                pairs.stream().anyMatch(pair -> "building:clearing".equals(pair.ticket())),
                "После серии провалов дух получает другую работу (расчистку):  Иначе тест case10_RepeatedFailure_SpiritGetsAnotherWork перестанет ловить эту поломку. При смене case10_RepeatedFailure_SpiritGetsAnotherWork проверь это утверждение."
                        + pairs);
        assertTrue(
                pairs.stream().noneMatch(pair -> pair.ticket().startsWith("graph:chop")),
                "Проваленную рубку дух снова не получает:  Иначе тест case10_RepeatedFailure_SpiritGetsAnotherWork перестанет ловить эту поломку. При смене case10_RepeatedFailure_SpiritGetsAnotherWork проверь это утверждение."
                        + pairs);
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
                "Один дух за раунд берёт не больше одной работы:  Иначе тест case24_BusySpirit_NoSecondWorkInRound перестанет ловить эту поломку. При смене case24_BusySpirit_NoSecondWorkInRound проверь это утверждение."
                        + pairs);
    }
}
