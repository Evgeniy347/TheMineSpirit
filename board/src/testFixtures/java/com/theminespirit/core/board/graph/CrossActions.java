package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.MoreActivityPlans;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class CrossActions {
    private CrossActions() {}

    static List<ScriptCall> calls(BoardCase boardCase) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(TicketPlans.plan(boardCase, target()), calls);
        return calls;
    }

    static List<ScriptCall> calls(BoardCase boardCase, TicketTarget target) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(TicketPlans.plan(boardCase, target), calls);
        return calls;
    }

    static List<ScriptCall> activityCalls(BoardCase boardCase) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(MoreActivityPlans.plan(boardCase, target()), calls);
        return calls;
    }

    static List<String> activityScripts(BoardCase boardCase) {
        return activityCalls(boardCase).stream().map(ScriptCall::script).toList();
    }

    static List<String> scripts(BoardCase boardCase, TicketTarget target) {
        return calls(boardCase, target).stream().map(ScriptCall::script).toList();
    }

    static List<String> scripts(BoardCase boardCase) {
        return calls(boardCase).stream().map(ScriptCall::script).toList();
    }

    static TicketTarget chopTarget() {
        return TicketTarget.at(CrossScene.STAND).withSupply("oak_log", 8);
    }

    static TicketTarget supplyTarget(String item) {
        return TicketTarget.at(CrossScene.STAND).withSupply(item, 8);
    }

    static TicketTarget target() {
        return TicketTarget.at(CrossScene.STAND)
                .withSpecies(Species.SHEEP)
                .withScheme("pen")
                .withRelocate(new BlockCoord(10, 60, -5), new BlockCoord(30, 60, 10))
                .withSupply("bread", 1)
                .withCraft("minecraft:iron_pickaxe", 1)
                .withTransfer(
                        UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                        UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
                        "bread",
                        1)
                .withCraterBlocks(8)
                .withToolCount(4);
    }

    static BoardTicket boardTicket(GraphTickets.Ticket ticket) {
        return boardTicket(ticket, Optional.empty());
    }

    static BoardTicket boardTicket(GraphTickets.Ticket ticket, Optional<UUID> assignee) {
        return ToolGate.withTool(
                new BoardTicket(
                        ticket.id(),
                        ticket.task().boardCase(),
                        assignee,
                        ticket.task().priority(),
                        0,
                        1,
                        ticket.id(),
                        List.of(),
                        ticket.task().consumes()),
                ticket.task().makes());
    }

    static List<AllocationCases.Pair> dispatch(GraphWorld world, List<GraphTickets.Ticket> known) {
        return AllocationCases.dispatch(world, known);
    }

    static List<AllocationCases.Pair> dispatch(GraphWorld world, GraphTickets.Result result) {
        return AllocationCases.dispatch(world, result.post());
    }

    static List<DispatchRound.Candidate> candidates(
            GraphWorld world, Map<String, EatNeed> eat, Map<String, SpiritView> views) {
        List<DispatchRound.Candidate> spirits = new ArrayList<>();
        world.kits.forEach((spirit, kit) -> {
            List<String> items = new ArrayList<>();
            kit.forEach((kind, tier) ->
                    items.add("minecraft:" + GraphWorld.GRAPH.tiers().get(tier) + "_" + kind));
            spirits.add(new DispatchRound.Candidate(
                    id(spirit),
                    spirit,
                    null,
                    items,
                    eat.getOrDefault(spirit, CrossScene.FED),
                    views.getOrDefault(spirit, SpiritView.free()),
                    false,
                    List.of(),
                    needs(eat.getOrDefault(spirit, CrossScene.FED))));
        });
        return spirits;
    }

    static DispatchRound.Candidate busy(DispatchRound.Candidate candidate, String busyWith) {
        return new DispatchRound.Candidate(
                candidate.id(),
                candidate.name(),
                busyWith,
                candidate.items(),
                candidate.eat(),
                candidate.view(),
                candidate.wet(),
                candidate.stockTools(),
                candidate.needs());
    }

    static Map<String, String> needs(EatNeed eat) {
        Map<String, String> needs = new LinkedHashMap<>();
        if (eat.needsFood()) {
            needs.put("hunger", VitalNeeds.byId("hunger").orElseThrow().blocked());
        }
        if (eat.maxHealth() > 0 && eat.health() / (double) eat.maxHealth() < 0.35) {
            needs.put("injury", VitalNeeds.byId("injury").orElseThrow().blocked());
        }
        return Map.copyOf(needs);
    }

    static UUID id(String spirit) {
        return UUID.nameUUIDFromBytes(spirit.getBytes(StandardCharsets.UTF_8));
    }

    static Board board(GraphWorld world, GraphTickets.Result result) {
        Board board = new Board(() -> 0);
        result.post().stream().map(CrossActions::boardTicket).forEach(board::submit);
        return board;
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
            return;
        }
        throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName() + " не разбирается сценой");
    }
}
