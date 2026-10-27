package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record SupplyGraph(
        double decay,
        int urgentBoost,
        int buildReadyBoost,
        int maxPriority,
        List<String> tiers,
        Kit kit,
        Storage storage,
        Map<String, Item> items,
        Map<String, Building> buildings,
        List<Way> ways,
        List<Edge> edges,
        Set<String> unimplemented,
        Map<String, GraphEvent> events,
        Map<String, List<String>> groups,
        List<Trigger> triggers,
        Map<String, Integer> herdCaps,
        double buildStartShare) {
    public static final String RESOURCE = "data/theminespirit/board/graph.json";
    public static final String RECIPES = "data/theminespirit/board/graph-recipes.json";
    private static final String BASE = "base";
    private static final String STAGE = "stage";
    private static final String KIT = "kit";
    private static final String WAYS = "ways";
    private static final int HERD_CULL_BOOST = 70;

    public record Item(String id, int xmin, double k, int base, int stage, boolean perSpirit) {}

    public record Building(
            String id,
            BoardCase boardCase,
            int stage,
            int base,
            @Nullable String tool,
            Map<String, Integer> materials,
            List<String> requires,
            List<String> advice,
            boolean optional) {
        public Building {
            materials = Map.copyOf(materials);
            requires = List.copyOf(requires);
            advice = List.copyOf(advice);
        }
    }

    public record Kit(
            Map<String, String> tiers, Map<Integer, Map<String, String>> stageTiers, int base, int secondaryBase) {
        public Kit {
            tiers = Map.copyOf(tiers);
            stageTiers = Map.copyOf(stageTiers);
        }

        public List<String> kinds() {
            return tiers.keySet().stream().sorted().toList();
        }

        public String tier(String kind, int stage) {
            String tier = tiers.getOrDefault(kind, "wooden");
            for (int step = 1; step <= stage; step++) {
                tier = stageTiers.getOrDefault(step, Map.of()).getOrDefault(kind, tier);
            }
            return tier;
        }
    }

    public record Storage(int minFreePercent, String building, int base) {}

    public record Way(
            String id,
            BoardCase boardCase,
            String makes,
            int yield,
            double weight,
            Map<String, Integer> consumes,
            @Nullable String tool,
            @Nullable String tier,
            @Nullable String building,
            @Nullable String world,
            Map<String, Integer> needs) {
        public Way {
            consumes = Map.copyOf(consumes);
            needs = Map.copyOf(needs);
        }
    }

    public record Edge(String lack, String way, int weight) {}

    public record Trigger(String have, String build) {}

    public SupplyGraph {
        tiers = List.copyOf(tiers);
        items = Map.copyOf(items);
        buildings = Map.copyOf(buildings);
        ways = List.copyOf(ways);
        edges = List.copyOf(edges);
        unimplemented = Set.copyOf(unimplemented);
        events = Map.copyOf(events);
        groups = Map.copyOf(groups);
        triggers = List.copyOf(triggers);
        herdCaps = Map.copyOf(herdCaps);
    }

    public static SupplyGraph loadDefault() {
        return merge(JsonResource.readClasspath(RESOURCE), RESOURCE, JsonResource.readClasspath(RECIPES), RECIPES);
    }

    public static SupplyGraph merge(
            String manualJson, String manualOrigin, String generatedJson, String generatedOrigin) {
        ObjectNode merged = ((ObjectNode) JsonResource.parse(manualJson, manualOrigin)).deepCopy();
        JsonNode generated = JsonResource.parse(generatedJson, generatedOrigin);
        mergeObjects(merged, generated, "items");
        mergeObjects(merged, generated, "buildings");
        mergeObjects(merged, generated, "groups");
        mergeWays(merged, generated);
        return parse(merged, manualOrigin + " + " + generatedOrigin);
    }

    public static SupplyGraph parse(String json, String origin) {
        return parse(JsonResource.parse(json, origin), origin);
    }

    private static SupplyGraph parse(JsonNode root, String origin) {
        Map<String, Item> items = new LinkedHashMap<>();
        root.path("items")
                .properties()
                .forEach(entry -> items.put(
                        entry.getKey(),
                        new Item(
                                entry.getKey(),
                                entry.getValue().path("xmin").asInt(),
                                entry.getValue().path("k").asDouble(1.0),
                                entry.getValue().path(BASE).asInt(),
                                entry.getValue().path(STAGE).asInt(1),
                                entry.getValue().path("perSpirit").asBoolean(false))));
        Map<String, Building> buildings = buildings(root);
        List<Way> ways = ways(root);
        List<Edge> edges = new ArrayList<>();
        for (JsonNode node : root.path("edges")) {
            edges.add(new Edge(
                    node.path("lack").asText(),
                    node.path("way").asText(),
                    node.path("weight").asInt()));
        }
        List<String> tiers = new ArrayList<>();
        root.path("tiers").forEach(tier -> tiers.add(tier.asText()));
        Map<String, String> kitTiers = new LinkedHashMap<>();
        root.path(KIT)
                .path("tiers")
                .properties()
                .forEach(entry -> kitTiers.put(entry.getKey(), entry.getValue().asText()));
        JsonNode storage = root.path("storage");
        SupplyGraph graph = new SupplyGraph(
                root.path("decay").asDouble(0.5),
                root.path("urgentBoost").asInt(),
                root.path("buildReadyBoost").asInt(),
                root.path("maxPriority").asInt(100),
                tiers,
                new Kit(
                        kitTiers,
                        stageTiers(root.path(KIT)),
                        root.path(KIT).path(BASE).asInt(),
                        root.path(KIT)
                                .path("secondaryBase")
                                .asInt(root.path(KIT).path(BASE).asInt())),
                new Storage(
                        storage.path("minFreePercent").asInt(),
                        storage.path("building").asText(),
                        storage.path(BASE).asInt()),
                items,
                buildings,
                ways,
                edges,
                unimplemented(root),
                GraphEvent.parse(root),
                groups(root),
                triggers(root),
                GraphEvent.counts(root.path("herdCaps")),
                root.path("buildStartShare").asDouble(1.0));
        graph.validate(origin);
        return graph;
    }

    private static void mergeObjects(ObjectNode target, JsonNode generated, String field) {
        JsonNode source = generated.path(field);
        if (!source.isObject()) {
            return;
        }
        JsonNode existing = target.get(field);
        ObjectNode section = existing != null && existing.isObject() ? (ObjectNode) existing : target.putObject(field);
        source.properties().forEach(entry -> {
            if (!section.has(entry.getKey())) {
                section.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    private static void mergeWays(ObjectNode target, JsonNode generated) {
        JsonNode source = generated.path(WAYS);
        if (!source.isArray() || source.isEmpty()) {
            return;
        }
        Set<String> manualMakes = new HashSet<>();
        for (JsonNode way : target.path(WAYS)) {
            manualMakes.add(way.path("makes").asText());
        }
        JsonNode existing = target.get(WAYS);
        ArrayNode ways = existing != null && existing.isArray() ? (ArrayNode) existing : target.putArray(WAYS);
        for (JsonNode way : source) {
            if (!manualMakes.contains(way.path("makes").asText())) {
                ways.add(way.deepCopy());
            }
        }
    }

    private static Map<String, Building> buildings(JsonNode root) {
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
                            BoardCase.valueOf(entry.getValue().path("case").asText()),
                            entry.getValue().path(STAGE).asInt(1),
                            entry.getValue().path(BASE).asInt(),
                            text(entry.getValue(), "tool"),
                            materials,
                            texts(entry.getValue().path("requires")),
                            texts(entry.getValue().path("advice")),
                            entry.getValue().path("optional").asBoolean(false)));
        });
        return buildings;
    }

    private static List<Way> ways(JsonNode root) {
        List<Way> ways = new ArrayList<>();
        for (JsonNode node : root.path(WAYS)) {
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

    private static Map<Integer, Map<String, String>> stageTiers(JsonNode kit) {
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

    private static Map<String, List<String>> groups(JsonNode root) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        root.path("groups").properties().forEach(entry -> groups.put(entry.getKey(), texts(entry.getValue())));
        return groups;
    }

    private static List<Trigger> triggers(JsonNode root) {
        List<Trigger> triggers = new ArrayList<>();
        for (JsonNode node : root.path("triggers")) {
            triggers.add(
                    new Trigger(node.path("have").asText(), node.path("build").asText()));
        }
        return triggers;
    }

    public List<String> members(String node) {
        return groups.getOrDefault(node, List.of());
    }

    public int herdCullBoost() {
        return HERD_CULL_BOOST;
    }

    public boolean isGroup(String node) {
        return groups.containsKey(node);
    }

    private static Set<String> unimplemented(JsonNode root) {
        Set<String> ids = new HashSet<>();
        for (String section : List.of("items", "buildings")) {
            root.path(section).properties().forEach(entry -> {
                if (!entry.getValue().path("implemented").asBoolean(true)) {
                    ids.add(entry.getKey());
                }
            });
        }
        for (JsonNode node : root.path(WAYS)) {
            if (!node.path("implemented").asBoolean(true)) {
                ids.add(node.path("id").asText());
            }
        }
        return ids;
    }

    public boolean implemented(String id) {
        return !unimplemented.contains(id);
    }

    public int rank(String tier) {
        return tiers.indexOf(tier);
    }

    public Optional<Way> way(String id) {
        return ways.stream().filter(way -> way.id().equals(id)).findFirst();
    }

    public List<Way> producers(String node) {
        return ways.stream().filter(way -> way.makes().equals(node)).toList();
    }

    public boolean isTool(String node) {
        int split = node.indexOf('_');
        return split > 0
                && tiers.contains(node.substring(0, split))
                && kit.kinds().contains(node.substring(split + 1));
    }

    private void validate(String origin) {
        for (Way way : ways) {
            if (!items.containsKey(way.makes()) && !isTool(way.makes())) {
                throw new IllegalStateException("Файл '" + origin + "': путь " + way.id() + " делает '" + way.makes()
                        + "', которого нет среди предметов и инструментов графа");
            }
            for (String input : way.consumes().keySet()) {
                if (!items.containsKey(input) && !groups.containsKey(input) && !isTool(input)) {
                    throw new IllegalStateException("Файл '" + origin + "': путь " + way.id() + " расходует '" + input
                            + "', которого нет среди предметов графа");
                }
            }
            if (way.building() != null && !buildings.containsKey(way.building())) {
                throw new IllegalStateException("Файл '" + origin + "': путь " + way.id() + " требует постройку '"
                        + way.building() + "', которой нет в графе");
            }
        }
        for (Building building : buildings.values()) {
            for (String input : building.materials().keySet()) {
                if (!items.containsKey(input) && !groups.containsKey(input)) {
                    throw new IllegalStateException("Файл '" + origin + "': постройка " + building.id() + " расходует '"
                            + input + "', которого нет среди предметов графа");
                }
            }
        }
        for (Building building : buildings.values()) {
            for (String required : building.requires()) {
                if (!buildings.containsKey(required)) {
                    throw new IllegalStateException("Файл '" + origin + "': постройка " + building.id()
                            + " требует постройку '" + required + "', которой нет в графе");
                }
            }
        }
        for (Edge edge : edges) {
            Objects.requireNonNull(
                    way(edge.way()).orElse(null),
                    "Файл '" + origin + "': ребро от '" + edge.lack() + "' ведёт к неизвестному пути " + edge.way());
        }
    }

    private static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
