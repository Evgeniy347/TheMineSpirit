package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.SupplyGraph.Building;
import com.theminespirit.core.board.graph.SupplyGraph.Trigger;
import com.theminespirit.core.board.graph.SupplyGraph.Way;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SupplyGraphParser {
    private SupplyGraphParser() {}

    static Map<String, Building> buildings(JsonNode root) {
        Map<String, Building> buildings = new LinkedHashMap<>();
        root.path("buildings").properties().forEach(entry -> {
            Map<String, Integer> materials = new LinkedHashMap<>();
            entry.getValue()
                    .path("materials")
                    .properties()
                    .forEach(
                            item -> materials.put(item.getKey(), item.getValue().asInt()));
            buildings.put(
                    entry.getKey(),
                    new Building(
                            entry.getKey(),
                            entry.getValue().path("name").asText(""),
                            BoardCase.valueOf(entry.getValue().path("case").asText()),
                            entry.getValue().path(SupplyGraph.STAGE).asInt(1),
                            entry.getValue().path(SupplyGraph.BASE).asInt(),
                            text(entry.getValue(), "tool"),
                            materials,
                            texts(entry.getValue().path("requires")),
                            texts(entry.getValue().path("advice")),
                            entry.getValue().path("optional").asBoolean(false)));
        });
        return buildings;
    }

    static List<Way> ways(JsonNode root) {
        List<Way> ways = new ArrayList<>();
        for (JsonNode node : root.path(SupplyGraph.WAYS)) {
            Map<String, Integer> consumes = new LinkedHashMap<>();
            node.path("consumes")
                    .properties()
                    .forEach(entry ->
                            consumes.put(entry.getKey(), entry.getValue().asInt()));
            ways.add(new Way(
                    node.path("id").asText(),
                    BoardCase.valueOf(node.path("case").asText()),
                    node.path("makes").asText(),
                    node.path("yield").asInt(1),
                    node.path("weight").asDouble(1.0),
                    consumes,
                    text(node, "tool"),
                    text(node, "tier"),
                    text(node, "building"),
                    text(node, "world"),
                    GraphEvent.counts(node.path("needs"))));
        }
        return ways;
    }

    static Map<Integer, Map<String, String>> stageTiers(JsonNode kit) {
        Map<Integer, Map<String, String>> stages = new LinkedHashMap<>();
        kit.path("stageTiers").properties().forEach(entry -> {
            Map<String, String> tiers = new LinkedHashMap<>();
            entry.getValue()
                    .properties()
                    .forEach(tier -> tiers.put(tier.getKey(), tier.getValue().asText()));
            stages.put(Integer.parseInt(entry.getKey()), tiers);
        });
        return stages;
    }

    static Map<String, List<String>> groups(JsonNode root) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        root.path("groups").properties().forEach(entry -> groups.put(entry.getKey(), texts(entry.getValue())));
        return groups;
    }

    static List<Trigger> triggers(JsonNode root) {
        List<Trigger> triggers = new ArrayList<>();
        for (JsonNode node : root.path("triggers")) {
            triggers.add(
                    new Trigger(node.path("have").asText(), node.path("build").asText()));
        }
        return triggers;
    }

    static Set<String> unimplemented(JsonNode root) {
        Set<String> ids = new HashSet<>();
        for (String section : List.of("items", "buildings")) {
            root.path(section).properties().forEach(entry -> {
                if (!entry.getValue().path("implemented").asBoolean(true)) {
                    ids.add(entry.getKey());
                }
            });
        }
        for (JsonNode node : root.path(SupplyGraph.WAYS)) {
            if (!node.path("implemented").asBoolean(true)) {
                ids.add(node.path("id").asText());
            }
        }
        return ids;
    }

    static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
