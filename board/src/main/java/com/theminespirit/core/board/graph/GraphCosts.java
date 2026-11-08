package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardCase;
import java.util.LinkedHashMap;
import java.util.Map;

public record GraphCosts(
        int defaultTicks,
        Map<String, Integer> caseTicks,
        Map<String, Integer> wayTicks,
        Map<String, Integer> wayUses,
        int ticksPerBlock,
        int travelScaleTicks,
        Map<String, Integer> toolDurability,
        Map<String, Integer> stationCapacity,
        GraphChoice choice,
        GraphLife life) {
    public static final GraphCosts NONE =
            new GraphCosts(1, Map.of(), Map.of(), Map.of(), 0, 1, Map.of(), Map.of(), GraphChoice.NEUTRAL);

    public GraphCosts(
            int defaultTicks,
            Map<String, Integer> caseTicks,
            Map<String, Integer> wayTicks,
            Map<String, Integer> wayUses,
            int ticksPerBlock,
            int travelScaleTicks,
            Map<String, Integer> toolDurability,
            Map<String, Integer> stationCapacity,
            GraphChoice choice) {
        this(
                defaultTicks,
                caseTicks,
                wayTicks,
                wayUses,
                ticksPerBlock,
                travelScaleTicks,
                toolDurability,
                stationCapacity,
                choice,
                GraphLife.NONE);
    }

    public GraphCosts {
        caseTicks = Map.copyOf(caseTicks);
        wayTicks = Map.copyOf(wayTicks);
        wayUses = Map.copyOf(wayUses);
        toolDurability = Map.copyOf(toolDurability);
        stationCapacity = Map.copyOf(stationCapacity);
        if (choice == null) {
            throw new IllegalArgumentException(
                    "Поправки выбора не могут быть null: нейтральные задаёт GraphChoice.NEUTRAL");
        }
        if (life == null) {
            throw new IllegalArgumentException("Коэффициенты жизни не могут быть null: их читают из graph.json → life");
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
        if (costs.isMissingNode()) {
            return NONE;
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
                GraphEvent.counts(costs.path("stationCapacity")),
                GraphChoice.parse(root.path("choice")),
                GraphLife.parse(root.path("life")));
    }
}
