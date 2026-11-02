package com.theminespirit.core.board.graph;

import java.util.Map;
import java.util.Objects;

final class GraphOperations {
    private final SupplyGraph graph;
    private final GraphState state;
    private final GraphPlanner planner;

    private GraphOperations(SupplyGraph graph, GraphState state, GraphPlanner planner) {
        this.graph = graph;
        this.state = state;
        this.planner = planner;
    }

    static void run(SupplyGraph graph, GraphState state, GraphPlanner planner) {
        new GraphOperations(graph, state, planner).operational();
    }

    private void operational() {
        storeMinimums();
        transferCarry();
        smelt();
        storageSpace();
        sortChests();
        digOut();
    }

    private void storeMinimums() {
        GraphFacts.StageReqs reqs = state.facts().stageReqs();
        if (reqs.minimum().isEmpty()) {
            return;
        }
        boolean shortage = false;
        for (Map.Entry<String, Integer> need : reqs.minimum().entrySet()) {
            String node = need.getKey();
            int have = reqs.actual().getOrDefault(node, 0);
            if (have >= need.getValue()) {
                continue;
            }
            shortage = true;
            if (graph.items().containsKey(node) || graph.isGroup(node) || graph.isTool(node)) {
                planner.push(null, node, nodeWeight(node));
            } else {
                planner.notices.putIfAbsent(
                        node,
                        new GraphPlan.Notice(
                                node, "минимум склада требует «" + node + "», а граф не знает, как это добыть"));
            }
        }
        if (shortage) {
            planner.push(null, StoreFacts.MINIMUM_ITEM, nodeWeight(StoreFacts.MINIMUM_ITEM));
        }
    }

    private void transferCarry() {
        GraphFacts facts = state.facts();
        if (facts.chests().isEmpty()) {
            return;
        }
        if (StoreFacts.carry(facts.chests(), facts.spirits(), StoreFacts.carriedDefault())) {
            planner.push(null, StoreFacts.CARRY_ITEM, nodeWeight(StoreFacts.CARRY_ITEM));
        }
    }

    private void smelt() {
        for (GraphFacts.Station station : state.facts().stations().values()) {
            if (!"furnace".equals(station.type()) && !"smoker".equals(station.type())) {
                continue;
            }
            if (StoreFacts.smeltTake(station)) {
                planner.push(null, StoreFacts.SMELT_TAKE_ITEM, nodeWeight(StoreFacts.SMELT_TAKE_ITEM));
            }
            if (StoreFacts.smeltLoad(station)) {
                planner.push(null, StoreFacts.SMELT_LOAD_ITEM, nodeWeight(StoreFacts.SMELT_LOAD_ITEM));
            }
        }
    }

    private void storageSpace() {
        GraphFacts facts = state.facts();
        if (facts.chests().isEmpty()) {
            return;
        }
        if (StoreFacts.space(facts.chests(), graph.storage().minFreePercent())) {
            planner.push(null, StoreFacts.SPACE_ITEM, nodeWeight(StoreFacts.SPACE_ITEM));
        }
    }

    private void sortChests() {
        GraphFacts facts = state.facts();
        if (StoreFacts.mixed(facts.chests())) {
            planner.push(null, StoreFacts.SORT_ITEM, nodeWeight(StoreFacts.SORT_ITEM));
        }
    }

    private void digOut() {
        for (GraphFacts.Spirit spirit : state.facts().spirits().values()) {
            if (spirit.trapped()) {
                String node = DigFacts.node(spirit.trap());
                planner.push(null, node, nodeWeight(node));
            }
        }
    }

    private double nodeWeight(String node) {
        SupplyGraph.Item item = graph.items().get(node);
        if (item != null) {
            return Math.max(1, item.base());
        }
        if (graph.isGroup(node)) {
            return graph.members(node).stream()
                    .map(graph.items()::get)
                    .filter(Objects::nonNull)
                    .mapToInt(SupplyGraph.Item::base)
                    .max()
                    .orElse(40);
        }
        return 40;
    }
}
