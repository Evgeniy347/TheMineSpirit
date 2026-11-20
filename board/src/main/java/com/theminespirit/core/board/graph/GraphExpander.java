package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class GraphExpander {
    private final SupplyGraph graph;
    private final GraphState state;
    private final GraphReach reach;
    private final GraphFlow flow;
    private final Set<String> expanded;
    private final Set<String> chances;
    private final Map<String, GraphPlan.Notice> notices;

    GraphExpander(
            SupplyGraph graph,
            GraphState state,
            GraphReach reach,
            GraphFlow flow,
            Set<String> expanded,
            Set<String> chances,
            Map<String, GraphPlan.Notice> notices) {
        this.graph = graph;
        this.state = state;
        this.reach = reach;
        this.flow = flow;
        this.expanded = expanded;
        this.chances = chances;
        this.notices = notices;
    }

    void push(@Nullable String from, String node, double amount) {
        if (amount == 0) return;
        if (from == null) {
            flow.root(node, amount);
        } else {
            flow.link(from, node, amount);
        }
        expand(node);
    }

    private void expand(String node) {
        if (!expanded.add(node)) return;
        double decay = graph.decay();
        if (node.startsWith(GraphPlanner.TOOL)) {
            String kind = node.substring(GraphPlanner.TOOL.length(), node.indexOf(GraphPlanner.AT));
            String spirit = node.substring(node.indexOf(GraphPlanner.AT) + 1);
            if (ChestTools.covered(graph, state).contains(kind + GraphPlanner.AT + spirit)) return;
            for (String tier : ToolTiers.wanted(graph, state, reach, kind, state.tier(spirit, kind))) {
                boolean aboveKit = graph.rank(tier) > graph.rank(graph.kit().tier(kind, state.stage()));
                for (SupplyGraph.Way way : reach.producers(tier + "_" + kind)) {
                    String key = way.id() + GraphPlanner.AT + spirit;
                    flow.link(node, key, way.weight() * BuildingShare.luck(graph, state, aboveKit, way));
                    if (aboveKit) {
                        chances.add(key);
                    } else {
                        expandWay(key, way);
                    }
                }
            }
        } else if (node.startsWith(GraphPlanner.BUILDING)) {
            String id = node.substring(GraphPlanner.BUILDING.length());
            SupplyGraph.Building building = graph.buildings().get(id);
            Map<String, Integer> needs = GraphLack.materials(graph, state, id);
            if (building != null
                    && GraphLack.stocked(graph, state, needs)
                    && (building.tool() == null || state.bestTier(building.tool()) >= 0)) {
                flow.local(node, graph.buildReadyBoost());
            }
            for (Map.Entry<String, Integer> input : needs.entrySet()) {
                if (GraphLack.count(graph, state, input.getKey()) < input.getValue()) {
                    push(node, input.getKey(), decay);
                }
            }
            if (building != null) {
                for (String required : building.requires()) {
                    if (state.built(required) != Built.BUILT) {
                        requireBuilding(node, required, decay, "нужна постройке " + id);
                    }
                }
                if (building.tool() != null && state.bestTier(building.tool()) < 0) {
                    pushTool(node, building.tool(), decay);
                }
            }
        } else if (node.startsWith(GraphPlanner.EVENT) || node.startsWith(GraphPlanner.EXPLORE)) {
            return;
        } else if (graph.isGroup(node)) {
            for (String member : graph.members(node)) {
                if (reach.sourced(member)) {
                    push(node, member, 1.0);
                }
            }
        } else {
            List<SupplyGraph.Way> producers = reach.producers(node);
            for (SupplyGraph.Way way : producers) {
                if (reach.available(way)) {
                    flow.link(node, way.id(), way.weight());
                    expandWay(way.id(), way);
                }
            }
            List<String> missing = producers.stream()
                    .map(SupplyGraph.Way::world)
                    .filter(id -> id != null && !state.world().contains(id) && !id.startsWith("more:"))
                    .distinct()
                    .toList();
            if (!missing.isEmpty() && !reach.sourced(node)) {
                notices.putIfAbsent(
                        node,
                        new GraphPlan.Notice(
                                node,
                                "нет источника " + node + " в округе (нет " + missing
                                        + "): разведать или сменить материал/породу постройки"));
                missing.forEach(resource -> flow.link(node, GraphPlanner.EXPLORE + resource, decay));
            }
        }
    }

    void expandWay(String key, SupplyGraph.Way way) {
        if (!expanded.add(key)) {
            return;
        }
        double decay = graph.decay();
        for (Map.Entry<String, Integer> input : way.consumes().entrySet()) {
            if (GraphLack.count(graph, state, input.getKey()) < input.getValue()) {
                push(key, input.getKey(), decay);
            }
        }
        for (Map.Entry<String, Integer> input : way.needs().entrySet()) {
            if (GraphLack.count(graph, state, input.getKey()) < input.getValue()) {
                push(key, input.getKey(), decay);
            }
        }
        if (way.tool() != null && state.bestTier(way.tool()) < graph.rank(way.tier() == null ? "wooden" : way.tier())) {
            pushTool(key, way.tool(), decay);
        }
        if (way.building() != null && state.built(way.building()) != Built.BUILT) {
            requireBuilding(key, way.building(), decay, "нужна пути " + way.id());
        }
    }

    private void pushTool(@Nullable String from, String kind, double amount) {
        for (String spirit : state.kits().keySet()) {
            push(from, GraphPlanner.TOOL + kind + GraphPlanner.AT + spirit, amount / Math.max(1, state.spirits()));
        }
    }

    void requireBuilding(@Nullable String from, String id, double amount, String why) {
        SupplyGraph.Building building = graph.buildings().get(id);
        if (building == null || state.built(id) == Built.ABSENT || building.stage() > state.stage()) {
            notices.putIfAbsent(
                    id,
                    new GraphPlan.Notice(
                            id,
                            "нужна постройка " + id + " (" + why + "), её нет в плане"
                                    + (building != null && building.stage() > state.stage()
                                            ? " этапа " + state.stage()
                                            : "")));
            return;
        }
        if (StageCatalog.watcherOwned(building.boardCase()) || GraphAudit.settles(state, building)) return;
        push(from, GraphPlanner.BUILDING + id, amount);
    }
}
