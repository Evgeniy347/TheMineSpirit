package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.Pair;
import static com.theminespirit.core.board.graph.AllocationCases.boardTicket;
import static com.theminespirit.core.board.graph.AllocationCases.candidates;
import static com.theminespirit.core.board.graph.AllocationCases.community;
import static com.theminespirit.core.board.graph.AllocationCases.dispatch;
import static com.theminespirit.core.board.graph.AllocationCases.facts;
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
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolLossRelease;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphDispatchChainTests {
    private static final Map<String, Integer> NO_CHEST = Map.of();

    private static GraphWorld withoutAxes(int spirits, Map<String, Integer> chest) {
        GraphWorld world = community(spirits);
        List<GraphFacts.Spirit> facts = new ArrayList<>();
        for (int index = 1; index <= spirits; index++) {
            String name = "spirit-" + index;
            world.kits.get(name).remove("axe");
            facts.add(spirit(name, AllocationCases.tools("pickaxe", 1)));
        }
        world.facts = facts(facts, chest);
        chest.forEach((item, count) -> world.stock.put(shortName(item), count));
        return world;
    }

    private static String shortName(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }

    private static void feed(Board board, List<GraphTickets.Ticket> works) {
        works.stream().map(AllocationCases::boardTicket).forEach(board::submit);
    }

    private static BoardTicket resolved(BoardTicket ticket, GraphWorld world) {
        Map<String, Integer> items = new LinkedHashMap<>();
        ticket.requiredItems().forEach((item, count) -> {
            if (!GraphWorld.GRAPH.isGroup(item)) {
                items.merge(item, count, Integer::sum);
                return;
            }
            List<String> members = GraphWorld.GRAPH.members(item);
            String member = members.stream()
                    .filter(candidate -> world.stock.getOrDefault(candidate, 0) > 0)
                    .findFirst()
                    .orElse(members.get(0));
            items.merge(member, count, Integer::sum);
        });
        return new BoardTicket(
                ticket.id(),
                ticket.boardCase(),
                ticket.assignee(),
                ticket.priority(),
                ticket.createdAt(),
                ticket.level(),
                ticket.cause(),
                ticket.dependsOn(),
                Map.copyOf(items),
                ticket.modifiers());
    }

    private static List<String> chopPairs(List<Pair> pairs) {
        return pairs.stream()
                .filter(pair -> pair.ticket().contains("chop_"))
                .map(Pair::ticket)
                .toList();
    }

    private static int runTicks(Board board, GraphWorld world, List<String> stock) {
        List<DispatchRound.Candidate> spirits = candidates(world);
        int assigned = 0;
        for (int tick = 0; tick < 1000; tick++) {
            for (DispatchRound.Candidate candidate : spirits) {
                ToolLossRelease.apply(board, candidate.id(), List.of(), false, ticket -> null, stock);
            }
            for (BoardTicket ticket : DispatchRound.order(board.claimable(new StockView(world.stock), 0))) {
                Optional<DispatchRound.Candidate> chosen = new DispatchRound(board, 0).choose(ticket, spirits);
                if (chosen.isPresent()
                        && ticket.id().contains("chop_")
                        && board.claim(ticket.id(), chosen.orElseThrow().id(), 0) == BoardOutcome.CLAIMED) {
                    assigned++;
                }
            }
        }
        return assigned;
    }

    @Test
    void case01_AxesInChest_ChopGivenToOneSpiritOthersGetOtherWork() {
        GraphWorld world = withoutAxes(5, Map.of("minecraft:stone_axe", 5));
        world.with("oak_log", 0).with("cobblestone", 0);
        GraphTickets.Result result = tickets(world);

        assertFalse(
                result.post().stream()
                        .anyMatch(ticket ->
                                ticket.id().contains("_axe") && ticket.id().contains("craft")),
                "Топоры уже лежат в сундуке — новый топор не крафтится: " + result.post());
        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().contains("gear_equip_best")),
                "Топор берут из сундука событием снаряжения: " + result.post());

        List<GraphTickets.Ticket> works = new ArrayList<>(only(result, "chop_oak_log"));
        works.addAll(only(result, "mine_cobble"));
        works.addAll(only(result, "dig_dirt"));
        List<Pair> pairs = dispatch(world, works);

        assertEquals(
                1,
                chopPairs(pairs).size(),
                "Одна заявка на рубку — её берёт ровно один дух, а не все пятеро: " + pairs);
        assertTrue(
                pairs.stream().anyMatch(pair -> !pair.ticket().contains("chop_")),
                "Остальные духи получают другую работу, а не дублируют рубку: " + pairs);
        assertEquals(
                pairs.size(),
                pairs.stream().map(Pair::spirit).distinct().count(),
                "Один дух за раунд берёт не больше одной работы: " + pairs);
    }

    @Test
    void case02_NeedClosed_ChopRemovedFromAll() {
        GraphWorld world = withoutAxes(5, Map.of("minecraft:stone_axe", 5));
        world.with("oak_log", 64);
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().noneMatch(ticket -> "chop_oak_log".equals(ticket.node())),
                "Брёвен достаточно — рубка не ставится никому: " + result.post());
        assertTrue(chopPairs(dispatch(world, result.post())).isEmpty(), "Закрытая нужда не выдаётся ни одному духу");
    }

    @Test
    void case03_NoAxeAnywhere_SpiritWaitsInsteadOfCarousel() {
        GraphWorld world = withoutAxes(5, NO_CHEST);
        world.with("oak_log", 0);
        List<GraphTickets.Ticket> works = only(tickets(world), "chop_oak_log");
        Board board = new Board(() -> 0);
        feed(board, works);

        assertEquals(0, runTicks(board, world, List.of()), "Без топора рубка не выдаётся ни разу");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf("graph:chop_oak_log").orElseThrow(),
                "Заявка ждёт топор, а не крутится по духам");
    }

    @Test
    void case03b_AxeInChest_ChopAssignedOnceAndKept() {
        GraphWorld world = withoutAxes(5, Map.of("minecraft:stone_axe", 5));
        world.with("oak_log", 0);
        Board board = new Board(() -> 0);
        feed(board, only(tickets(world), "chop_oak_log"));

        assertEquals(
                1,
                runTicks(board, world, List.of("minecraft:stone_axe")),
                "Топор в сундуке: рубка выдана один раз и держится, а не возвращается каждый тик");
    }

    @Test
    void case04_ReopenedDone_DoesNotSpawnTicketForEverySpirit() {
        GraphWorld world = withoutAxes(5, Map.of("minecraft:stone_axe", 5));
        world.with("oak_log", 0);
        BoardTicket first = boardTicket(only(tickets(world), "chop_oak_log").get(0));
        BoardTicket second = new BoardTicket(
                first.id() + ":2",
                first.boardCase(),
                first.assignee(),
                first.priority(),
                first.createdAt(),
                first.level(),
                first.cause(),
                first.dependsOn(),
                first.requiredItems(),
                first.modifiers());
        Board board = new Board(() -> 0);
        board.submit(first);
        board.submit(second);
        board.claim(first.id(), id("spirit-1"), 0);
        board.complete(first.id());
        board.claim(second.id(), id("spirit-2"), 0);
        board.complete(second.id());

        assertTrue(board.reopen(first.id()), "Закрытая работа открывается заново по новой нехватке");
        assertTrue(board.reopen(second.id()), "Вторая часть открывается вместе с первой");
        assertEquals(BoardOutcome.ACCEPTED, board.submit(first), "Новая нехватка ставит ту же заявку, а не дубль");
        assertEquals(BoardOutcome.ACCEPTED, board.submit(second), "Вторая часть тоже возвращается в очередь");

        List<Pair> pairs = dispatch(board, world);

        assertEquals(2, chopPairs(pairs).size(), "Переоткрытие даёт две части, а не билет каждому из пяти: " + pairs);
        assertEquals(
                pairs.size(),
                pairs.stream().map(Pair::spirit).distinct().count(),
                "Один дух не берёт обе части одновременно: " + pairs);
        assertEquals(
                2,
                board.all().stream()
                        .filter(ticket -> ticket.id().startsWith("graph:chop_oak_log"))
                        .count(),
                "На доске живут только две части рубки, без копий на остальных духов: " + board.all());
    }

    @Test
    void case05_OneAxeForTwoSpirits_TakeFromChestAndCraftShortfall() {
        GraphWorld world = withoutAxes(2, Map.of("minecraft:stone_axe", 1));
        world.with("oak_log", 0).with("cobblestone", 0);
        GraphTickets.Result result = tickets(world);
        long craftShortfall = result.post().stream()
                .filter(ticket -> ticket.id().contains("_axe") && ticket.id().contains("craft"))
                .count();
        Board board = new Board(() -> 0);
        only(result, "chop_oak_log").stream().map(AllocationCases::boardTicket).forEach(board::submit);
        result.post().stream()
                .filter(ticket -> ticket.id().contains("_axe") && ticket.id().contains("craft"))
                .map(AllocationCases::boardTicket)
                .map(ticket -> resolved(ticket, world))
                .forEach(board::submit);
        List<Pair> pairs = dispatch(board, world);

        assertEquals(1, chopPairs(pairs).size(), "Один топор из сундука закрывает рубку одного духа: " + pairs);
        assertEquals(
                1,
                craftShortfall,
                "Недостаёт ровно один топор на двоих — крафтится один, а не по топору на каждого: " + result.post());
        assertTrue(
                pairs.stream().anyMatch(pair -> pair.ticket().contains("_axe")),
                "Недостающий топор крафтит второй дух, а не простаивает: " + pairs);
        assertEquals(2, pairs.stream().map(Pair::spirit).distinct().count(), "Работу делят оба духа: " + pairs);
    }

    @Test
    void chopPlan_NoAxeInHand_AcquiresAxeBeforeTrees() {
        List<ScriptCall> calls = new ArrayList<>();
        collect(
                TicketPlans.plan(
                        BoardCase.GATHER_MATERIALS,
                        TicketTarget.at(new BlockCoord(0, 64, 0)).withSupply("jungle_log", 8)),
                calls);
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && String.valueOf(call.params().get("item")).endsWith("_axe")),
                "Перед рубкой дух берёт топор со склада: иначе выданная рубка возвращается доске каждый тик: " + calls);
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }
}
