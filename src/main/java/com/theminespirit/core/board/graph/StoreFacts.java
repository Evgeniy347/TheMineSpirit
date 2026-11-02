package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StoreFacts {
    public static final String MINIMUM_ITEM = "store_minimum_need";
    public static final String CARRY_ITEM = "carry_need";
    public static final String SMELT_LOAD_ITEM = "smelt_load_need";
    public static final String SMELT_TAKE_ITEM = "smelt_take_need";
    public static final String SPACE_ITEM = "space_need";
    public static final String SORT_ITEM = "sort_need";
    public static final String SETTINGS = "data/theminespirit/board/graph.d/store.json";
    private static final String STORE = "store";
    private static final String MINIMUM_FIELD = "minimum";
    private static final String RESERVE_FIELD = "reservePercent";
    private static final int CARRY_ITEMS = 8;
    private static final int SORT_TYPES = 8;
    private static final int MINIMUM_RESERVE = 2;

    public record Minimums(Map<Integer, Map<String, Integer>> stages, int reservePercent) {
        public Minimums {
            stages = Map.copyOf(stages);
            if (reservePercent < 0 || reservePercent > 100) {
                throw new IllegalArgumentException("Резерв склада " + reservePercent
                        + "% вне [0, 100]: запас сверх минимума не бывает отрицательным или больше целого");
            }
        }

        public static Minimums of(Map<String, Integer> minimum, int reservePercent) {
            Objects.requireNonNull(minimum, "Минимум склада не может быть null: пустой — пустая карта");
            return new Minimums(Map.of(1, Map.copyOf(minimum)), reservePercent);
        }

        public Map<String, Integer> targets(int stage) {
            Map<String, Integer> target = new LinkedHashMap<>();
            for (int step = 1; step <= stage; step++) {
                target.putAll(stages.getOrDefault(step, Map.of()));
            }
            return target;
        }
    }

    private StoreFacts() {}

    public static Minimums loadDefault() {
        return parse(JsonResource.readClasspath(SETTINGS), SETTINGS);
    }

    public static Minimums parse(String json, String origin) {
        JsonNode store = JsonResource.parse(json, origin).path(STORE);
        Map<Integer, Map<String, Integer>> stages = new LinkedHashMap<>();
        JsonNode minimum = store.path(MINIMUM_FIELD);
        minimum.properties().forEach(entry -> {
            Map<String, Integer> items = new LinkedHashMap<>();
            entry.getValue()
                    .properties()
                    .forEach(item -> items.put(item.getKey(), item.getValue().asInt()));
            stages.put(Integer.parseInt(entry.getKey()), Map.copyOf(items));
        });
        return new Minimums(stages, store.path(RESERVE_FIELD).asInt());
    }

    public static GraphFacts.StageReqs stageReqs(
            SupplyGraph graph,
            Minimums settings,
            int stage,
            List<GraphFacts.Chest> chests,
            Map<String, GraphFacts.Spirit> spirits) {
        Objects.requireNonNull(graph, "Граф не может быть null: по нему считают группы и инструменты");
        Objects.requireNonNull(settings, "Настройки склада не могут быть null: без них нет минимума");
        Map<String, Integer> target = new LinkedHashMap<>();
        settings.targets(stage)
                .forEach((node, count) -> target.put(node, withReserve(graph, node, count, settings.reservePercent())));
        Map<String, Integer> counts = counts(chests, spirits);
        Map<String, Integer> actual = new LinkedHashMap<>();
        target.keySet().forEach(node -> actual.put(node, actual(graph, node, counts, spirits)));
        return new GraphFacts.StageReqs(target, actual);
    }

    public static boolean carry(List<GraphFacts.Chest> chests, Map<String, GraphFacts.Spirit> spirits, int carried) {
        if (freeSlots(chests) <= 0) {
            return false;
        }
        boolean surplus = spirits.values().stream()
                .mapToInt(spirit -> spirit.inventory().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .anyMatch(total -> total >= carried);
        return surplus || relocate(chests);
    }

    public static boolean relocate(List<GraphFacts.Chest> chests) {
        return chests.size() >= 2
                && chests.stream().anyMatch(chest -> !chest.contents().isEmpty())
                && chests.stream().anyMatch(chest -> chest.freeSlots() > 0);
    }

    public static boolean mixed(List<GraphFacts.Chest> chests) {
        return chests.stream().anyMatch(chest -> chest.contents().size() > SORT_TYPES);
    }

    public static boolean space(List<GraphFacts.Chest> chests, int minFreePercent) {
        int total = chests.stream().mapToInt(GraphFacts.Chest::totalSlots).sum();
        if (total <= 0) {
            return false;
        }
        return freeSlots(chests) * 100 < minFreePercent * total;
    }

    public static boolean smeltLoad(GraphFacts.Station station) {
        return !station.busy() && station.output() == null && (station.input() != null || station.fuel() != null);
    }

    public static boolean smeltTake(GraphFacts.Station station) {
        return station.output() != null;
    }

    public static int carriedDefault() {
        return CARRY_ITEMS;
    }

    private static int freeSlots(List<GraphFacts.Chest> chests) {
        return chests.stream().mapToInt(GraphFacts.Chest::freeSlots).sum();
    }

    private static int withReserve(SupplyGraph graph, String node, int minimum, int reservePercent) {
        if (graph.isTool(node) || minimum < MINIMUM_RESERVE) {
            return minimum;
        }
        return minimum + (int) Math.ceil(minimum * reservePercent / 100.0);
    }

    private static Map<String, Integer> counts(List<GraphFacts.Chest> chests, Map<String, GraphFacts.Spirit> spirits) {
        Map<String, Integer> counts = new HashMap<>();
        for (GraphFacts.Chest chest : chests) {
            chest.contents().forEach((item, count) -> counts.merge(GraphNames.graphItem(item), count, Integer::sum));
        }
        for (GraphFacts.Spirit spirit : spirits.values()) {
            spirit.inventory().forEach((item, count) -> counts.merge(GraphNames.graphItem(item), count, Integer::sum));
        }
        return counts;
    }

    private static int actual(
            SupplyGraph graph, String node, Map<String, Integer> counts, Map<String, GraphFacts.Spirit> spirits) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream()
                    .mapToInt(member -> counts.getOrDefault(member, 0))
                    .sum();
        }
        if (graph.isTool(node)) {
            return toolCount(graph, node, spirits);
        }
        return counts.getOrDefault(node, 0);
    }

    private static int toolCount(SupplyGraph graph, String node, Map<String, GraphFacts.Spirit> spirits) {
        int split = node.indexOf('_');
        if (split <= 0) {
            return 0;
        }
        String tier = node.substring(0, split);
        String kind = node.substring(split + 1);
        int rank = graph.rank(tier);
        if (rank < 0) {
            return 0;
        }
        int count = 0;
        for (GraphFacts.Spirit spirit : spirits.values()) {
            for (GraphFacts.Tool tool : spirit.tools()) {
                if (kind.equals(tool.kind()) && tool.tier() >= rank) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }
}
