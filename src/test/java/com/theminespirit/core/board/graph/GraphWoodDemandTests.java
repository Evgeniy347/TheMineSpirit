package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.Pair;
import static com.theminespirit.core.board.graph.AllocationCases.boardTicket;
import static com.theminespirit.core.board.graph.AllocationCases.community;
import static com.theminespirit.core.board.graph.AllocationCases.dispatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.GatherDemand;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphWoodDemandTests {
    private static final int SPIRITS = 5;
    private static final int NEED = 64;

    private static GraphWorld world() {
        GraphWorld world = community(SPIRITS).with("jungle_log", 0).built("orchard_jungle", Built.BUILT);
        world.materials.put("fence", Map.of("jungle_log", NEED));
        return world;
    }

    private static GraphTickets.Result demands(GraphWorld world) {
        GraphState state = world.state().withOrders(Map.of("jungle_log", NEED));
        GraphPlan plan = GraphPlanner.plan(world.graph, state);
        return GraphTickets.of(world.graph, state, plan);
    }

    @Test
    void case01_ThreeCausesSixtyFourLogs_OneRequestWithTotal() {
        List<GraphTickets.Ticket> logs = demands(world()).post().stream()
                .filter(ticket -> "jungle_log".equals(ticket.task().makes()))
                .toList();

        assertEquals(
                1,
                logs.size(),
                "Порог (40), счёт забора (64) и заказ игрока (64) на одно бревно — одна заявка: иначе рубку "
                        + "берут несколько духов сразу: " + demands(world()).post());
        assertEquals(
                NEED,
                logs.get(0).count(),
                "Общая заявка несёт количество от всех причин (64), а не остаток одной из них: " + logs.get(0));
    }

    @Test
    void case02_FiveSpiritsSixtyFourLogs_AtMostTwoLoggersOthersWork() {
        GraphWorld world = world().with("cobblestone", 0).with("dirt", 0);
        GraphTickets.Result result = demands(world);

        assertEquals(
                1,
                result.post().stream()
                        .filter(ticket -> "jungle_log".equals(ticket.task().makes()))
                        .count(),
                "Сквозь доску на 64 бревна идёт одна заявка: иначе рубку берут четверо из пяти: " + result.post());
        Board board = new Board(() -> 0);
        Set<String> wood = new HashSet<>();
        for (GraphTickets.Ticket ticket : result.post()) {
            if ("jungle_log".equals(ticket.task().makes())) {
                List<Integer> portions = GatherBatch.split(GatherBatch.round(ticket.count(), 4), 4, SPIRITS);
                for (int part = 0; part < portions.size(); part++) {
                    String id = part == 0 ? ticket.id() : ticket.id() + ":2";
                    wood.add(id);
                    board.submit(part(ticket, id));
                }
            } else {
                board.submit(boardTicket(ticket));
            }
        }

        List<Pair> pairs = dispatch(board, world);
        long loggers =
                pairs.stream().filter(pair -> wood.contains(pair.ticket())).count();
        long others = pairs.size() - loggers;

        assertTrue(loggers >= 1 && loggers <= 2, "На 64 бревна хватает 1–2 рубщиков: " + pairs);
        assertTrue(others >= 3, "Остальные духи заняты другой работой, а не дублируют рубку: " + pairs);
        assertEquals(
                pairs.size(),
                pairs.stream().map(Pair::spirit).distinct().count(),
                "Один дух за раунд берёт не больше одной работы: " + pairs);
    }

    private static BoardTicket part(GraphTickets.Ticket ticket, String id) {
        BoardTicket base = boardTicket(ticket);
        return new BoardTicket(
                id,
                base.boardCase(),
                base.assignee(),
                base.priority(),
                base.createdAt(),
                base.level(),
                base.cause(),
                base.dependsOn(),
                base.requiredItems(),
                base.modifiers());
    }

    @Test
    void case03_OneRequestPerWoodSpecies_StaysSingle() {
        GraphTickets.Result result = demands(world());
        List<String> items =
                result.post().stream().map(ticket -> ticket.task().makes()).toList();
        List<GatherDemand.Need> needs = new ArrayList<>();
        for (GraphTickets.Ticket ticket : result.post()) {
            needs.add(new GatherDemand.Need(
                    ticket.id(),
                    ticket.task().makes(),
                    ticket.id(),
                    ticket.count(),
                    ticket.task().priority(),
                    new BlockCoord(0, 64, 0)));
        }
        List<String> species = GatherDemand.onePerWood(GatherDemand.merge(needs)).stream()
                .filter(need -> !need.item().endsWith("_planks"))
                .map(need -> GatherDemand.wood(need.item()))
                .filter(Objects::nonNull)
                .toList();

        assertEquals(
                Set.copyOf(items).size(),
                items.size(),
                "Один предмет — одна заявка: разные пути одного бревна не ставят по заявке на духа: " + items);
        assertEquals(
                Set.copyOf(species).size(),
                species.size(),
                "На породу дерева живёт одна заявка, дуб и джунгль не мешают друг другу: " + species);
    }
}
