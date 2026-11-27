package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.api.BoardCase;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record GraphCosts(
        int defaultTicks,
        Map<String, Integer> caseTicks,
        Map<String, Integer> wayTicks,
        Map<String, Integer> wayUses,
        int ticksPerBlock,
        int travelScaleTicks,
        Map<String, Integer> toolDurability,
        Set<String> stations,
        GraphChoice choice,
        GraphLife life) {
    public GraphCosts {
        caseTicks = Map.copyOf(caseTicks);
        wayTicks = Map.copyOf(wayTicks);
        wayUses = Map.copyOf(wayUses);
        toolDurability = Map.copyOf(toolDurability);
        stations = Set.copyOf(stations);
        if (choice == null) {
            throw new IllegalArgumentException(
                    "Поправки выбора не могут быть null: нейтральные задаёт GraphChoice.NEUTRAL");
        }
        if (life == null) {
            throw new IllegalArgumentException(
                    "Формула жизни не может быть null: значения по умолчанию — GraphLife.DEFAULTS");
        }
        if (defaultTicks < 1 || travelScaleTicks < 1) {
            throw new IllegalArgumentException("Длительность по умолчанию " + defaultTicks + " и шкала дороги "
                    + travelScaleTicks + " обязаны быть ≥ 1: задание без времени и деление на ноль ломают метрику");
        }
    }

    public int ticks(String wayId, BoardCase boardCase) {
        return wayTicks.getOrDefault(wayId, caseTicks.getOrDefault(boardCase.name(), defaultTicks));
    }

    public int uses(String wayId, int yield) {
        return wayUses.getOrDefault(wayId, Math.max(1, yield));
    }

    public int travel(int distanceBlocks) {
        return 2 * distanceBlocks * ticksPerBlock;
    }

    public double travelFactor(int travelTicks) {
        return (double) travelScaleTicks / (travelScaleTicks + Math.max(0, travelTicks));
    }

    public int durability(String tier) {
        return toolDurability.getOrDefault(tier, Integer.MAX_VALUE);
    }

    static GraphCosts parse(JsonNode root) {
        JsonNode costs = root.path("costs");
        GraphLife life = GraphLife.parse(root.path("life"));
        if (costs.isMissingNode()) {
            return new GraphCosts(1, Map.of(), Map.of(), Map.of(), 0, 1, Map.of(), Set.of(), GraphChoice.NEUTRAL, life);
        }
        Map<String, Integer> wayTicks = new LinkedHashMap<>();
        Map<String, Integer> wayUses = new LinkedHashMap<>();
        for (JsonNode way : root.path("ways")) {
            if (way.has("ticks")) {
                wayTicks.put(way.path("id").asText(), way.path("ticks").asInt());
            }
            if (way.has("uses")) {
                wayUses.put(way.path("id").asText(), way.path("uses").asInt());
            }
        }
        return new GraphCosts(
                costs.path("defaultTicks").asInt(100),
                GraphEvent.counts(costs.path("caseTicks")),
                wayTicks,
                wayUses,
                costs.path("ticksPerBlock").asInt(0),
                costs.path("travelScaleTicks").asInt(1200),
                GraphEvent.counts(costs.path("toolDurability")),
                stations(costs),
                GraphChoice.parse(root.path("choice")),
                life);
    }

    private static Set<String> stations(JsonNode costs) {
        Set<String> stations = new LinkedHashSet<>();
        costs.path("stations").forEach(node -> stations.add(node.asText()));
        return stations;
    }
}
