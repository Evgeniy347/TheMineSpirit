package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.ToolGate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

final class AllocationCases {
    static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    record Pair(String spirit, String ticket) {}

    static UUID id(String spirit) {
        return UUID.nameUUIDFromBytes(spirit.getBytes(StandardCharsets.UTF_8));
    }

    static GraphWorld community(int spirits) {
        GraphWorld world = GraphWorld.settled();
        for (int index = 2; index <= spirits; index++) {
            world.tools("spirit-" + index, 1);
        }
        return world;
    }

    static GraphPlan plan(GraphWorld world) {
        return GraphFacts.empty().equals(world.facts)
                ? world.plan()
                : GraphPlanner.plan(
                        world.graph,
                        world.state().withFacts(world.facts, world.graph.costs().life()));
    }

    static GraphTickets.Result tickets(GraphWorld world) {
        return GraphFacts.empty().equals(world.facts)
                ? GraphTickets.of(world.graph, world.state(), world.plan())
                : GraphTickets.of(
                        world.graph,
                        world.state().withFacts(world.facts, world.graph.costs().life()),
                        plan(world));
    }

    static List<DispatchRound.Candidate> candidates(GraphWorld world) {
        List<String> stockTools = world.stock.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
        List<DispatchRound.Candidate> spirits = new ArrayList<>();
        world.kits.forEach((spirit, kit) -> {
            List<String> items = new ArrayList<>();
            kit.forEach((kind, tier) ->
                    items.add("minecraft:" + GraphWorld.GRAPH.tiers().get(tier) + "_" + kind));
            spirits.add(new DispatchRound.Candidate(
                    id(spirit), spirit, null, items, FED, SpiritView.free(), false, stockTools));
        });
        return spirits;
    }

    static BoardTicket boardTicket(GraphTickets.Ticket ticket) {
        return ToolGate.withTool(
                new BoardTicket(
                        ticket.id(),
                        ticket.task().boardCase(),
                        ticket.task().spirit().map(AllocationCases::id),
                        ticket.task().priority(),
                        0,
                        1,
                        ticket.id(),
                        List.of(),
                        ticket.task().consumes()),
                ticket.task().makes());
    }

    static List<Pair> dispatch(GraphWorld world, List<GraphTickets.Ticket> known) {
        Board board = new Board(() -> 0);
        known.stream().map(AllocationCases::boardTicket).forEach(board::submit);
        return dispatch(board, world);
    }

    static List<Pair> dispatch(Board board, GraphWorld world) {
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
        return pairs;
    }

    static List<GraphTickets.Ticket> only(GraphTickets.Result result, String node) {
        return result.post().stream()
                .filter(ticket -> node.equals(ticket.node()))
                .toList();
    }

    static boolean crafts(GraphTickets.Result result, String suffix) {
        return result.post().stream().anyMatch(ticket -> ticket.id().contains(suffix));
    }

    static List<GraphFacts.Tool> tools(String kind, int tier) {
        return List.of(new GraphFacts.Tool("minecraft:stone_" + kind, kind, tier, 100, 100));
    }

    static GraphFacts.Spirit spirit(String name, List<GraphFacts.Tool> tools) {
        return new GraphFacts.Spirit(
                name,
                new GraphFacts.Position(0, 64, 0),
                20,
                Math.max(20, 1f),
                null,
                20,
                300,
                false,
                false,
                false,
                null,
                Map.of(),
                List.of(),
                tools,
                null);
    }

    static GraphFacts facts(List<GraphFacts.Spirit> spirits, Map<String, Integer> chest) {
        return new GraphFacts(
                spirits.stream().collect(Collectors.toMap(GraphFacts.Spirit::id, value -> value)),
                List.of(),
                List.of(new GraphFacts.Chest(new GraphFacts.Position(1, 64, 1), chest, 10, 27)),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of());
    }

    static GraphFacts noChestFacts(List<GraphFacts.Spirit> spirits) {
        return new GraphFacts(
                spirits.stream().collect(Collectors.toMap(GraphFacts.Spirit::id, value -> value)),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of());
    }
}
