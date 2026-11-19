package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.inventory.WarehouseCategories;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class GraphStore {
    static final String MINIMUM = "store:minimum";
    static final String TRANSFER = "store:transfer";
    static final String SMELT = "store:smelt";
    static final String SPACE = "store:space";
    static final String SORT = "store:sort";
    static final String DELIVER = "store:carry:deliver";
    static final String MOVE = "store:carry:transfer";
    static final String SMELT_LOAD = "store:smelt:load";
    static final String SMELT_TAKE = "store:smelt:take";
    static final String CHESTS = "store:chests";
    static final String SORTED = "store:sorted";
    private static final String FURNACE = "furnace";
    private static final String CHEST_BUILDING = "chests";
    private static final double DEFAULT_BASE = 50;
    private static final WarehouseCategories CATEGORIES = WarehouseCategories.load();

    @FunctionalInterface
    interface Push {
        void push(@Nullable String from, String node, double amount);
    }

    private GraphStore() {}

    static void roots(SupplyGraph graph, GraphState state, Push push) {
        if (!enabled(graph, MINIMUM)) {
            return;
        }
        GraphFacts facts = state.facts();
        shortages(graph, facts)
                .forEach((item, actual) ->
                        push.push(null, item, base(graph, item) + (actual == 0 ? graph.urgentBoost() : 0)));
        Transfer transfer = transfer(facts);
        if (transfer.deliver() && enabled(graph, TRANSFER)) {
            push.push(null, DELIVER, base(graph, DELIVER));
        }
        if (transfer.move() && enabled(graph, TRANSFER)) {
            push.push(null, MOVE, base(graph, MOVE));
        }
        Smelt smelt = smelt(facts);
        if (smelt.load() && enabled(graph, SMELT)) {
            push.push(null, SMELT_LOAD, base(graph, SMELT_LOAD));
        }
        if (smelt.take() && enabled(graph, SMELT)) {
            push.push(null, SMELT_TAKE, base(graph, SMELT_TAKE));
        }
        if (state.storageFreePercent() < graph.storage().minFreePercent() && enabled(graph, SPACE)) {
            if (state.built(CHEST_BUILDING) == Built.BUILT) {
                push.push(null, CHESTS, base(graph, CHESTS));
            } else {
                push.push(null, "building:" + CHEST_BUILDING, base(graph, CHESTS));
            }
        }
        if (mixedChest(facts) && enabled(graph, SORT)) {
            push.push(null, SORTED, base(graph, SORTED));
        }
    }

    private static Map<String, Integer> shortages(SupplyGraph graph, GraphFacts facts) {
        Map<String, Integer> minimum = facts.stageReqs().minimum();
        Map<String, Integer> actual = facts.stageReqs().actual();
        Map<String, Integer> shortages = new LinkedHashMap<>();
        minimum.forEach((item, target) -> {
            if (!known(graph, item)) {
                return;
            }
            int have = actual.getOrDefault(item, 0);
            if (have < target) {
                shortages.put(item, have);
            }
        });
        return shortages;
    }

    private static boolean known(SupplyGraph graph, String node) {
        return graph.items().containsKey(node)
                || graph.isGroup(node)
                || graph.isTool(node)
                || !graph.producers(node).isEmpty();
    }

    private static double base(SupplyGraph graph, String node) {
        SupplyGraph.Item item = graph.items().get(node);
        if (item != null) {
            return item.base();
        }
        if (graph.isGroup(node)) {
            List<String> members = graph.members(node);
            return members.isEmpty() ? DEFAULT_BASE : base(graph, members.get(0));
        }
        return DEFAULT_BASE;
    }

    private static boolean enabled(SupplyGraph graph, String node) {
        return graph.items().containsKey(node);
    }

    private record Transfer(boolean deliver, boolean move) {}

    private static Transfer transfer(GraphFacts facts) {
        boolean carried = facts.spirits().values().stream()
                .anyMatch(spirit -> !spirit.inventory().isEmpty());
        boolean free = facts.chests().stream().anyMatch(chest -> chest.freeSlots() > 0);
        boolean move = false;
        List<GraphFacts.Chest> chests = facts.chests();
        for (int from = 0; from < chests.size(); from++) {
            if (chests.get(from).contents().isEmpty() || chests.get(from).freeSlots() > 0) {
                continue;
            }
            for (int to = 0; to < chests.size(); to++) {
                if (to != from && chests.get(to).freeSlots() > 0) {
                    move = true;
                    break;
                }
            }
        }
        return new Transfer(carried && free, move);
    }

    private record Smelt(boolean load, boolean take) {}

    private static Smelt smelt(GraphFacts facts) {
        boolean load = false;
        boolean take = false;
        for (GraphFacts.Station station : facts.stations().values()) {
            if (!FURNACE.equals(station.type())) {
                continue;
            }
            if (station.output() != null) {
                take = true;
            } else if (!station.busy()) {
                load = true;
            }
        }
        return new Smelt(load, take);
    }

    private static boolean mixedChest(GraphFacts facts) {
        for (GraphFacts.Chest chest : facts.chests()) {
            Set<String> found = new HashSet<>();
            for (String item : chest.contents().keySet()) {
                found.add(CATEGORIES.categoryOf(item));
                if (found.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
