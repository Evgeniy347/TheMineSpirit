package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.inventory.api.JsonResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class RecipeImport {
    public static final String INGREDIENT_TAG = "#";
    public static final double REVERSE_WEIGHT = 0.1;
    static final String COALS = "#coals";
    static final String ALT_PREFIX = "#alt_";
    static final Map<String, String> STATIONS = Map.of(
            "smelting", "furnace",
            "blasting", "blast_furnace",
            "smoking", "smoker",
            "campfire_cooking", "campfire",
            "stonecutting", "stonecutter",
            "smithing_transform", "smithing_table");
    static final Set<String> FUELLED = Set.of("smelting", "blasting", "smoking");

    public record Result(
            Map<String, List<String>> groups,
            List<SupplyGraph.Way> ways,
            Map<String, SupplyGraph.Item> items,
            Map<String, SupplyGraph.Building> buildings,
            Map<String, Integer> parsed,
            Map<String, Integer> skipped) {
        public Result {
            groups = Map.copyOf(groups);
            ways = List.copyOf(ways);
            items = Map.copyOf(items);
            buildings = Map.copyOf(buildings);
            parsed = Map.copyOf(parsed);
            skipped = Map.copyOf(skipped);
        }
    }

    private RecipeImport() {}

    public static Result importRecipes(Map<String, String> recipes, Map<String, String> tagJson, SupplyGraph manual) {
        RecipeTags tags = new RecipeTags(tagJson);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, BoardCase> manualCases = manualCases(manual);
        Map<String, Integer> parsed = new TreeMap<>();
        Map<String, Integer> skipped = new TreeMap<>();
        List<SupplyGraph.Way> ways = new ArrayList<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(recipes).entrySet()) {
            JsonNode root = JsonResource.parse(entry.getValue(), "рецепт " + entry.getKey());
            String type = simple(root.path("type").asText());
            SupplyGraph.Way way = RecipeParsing.parse(entry.getKey(), type, root, tags, groups, manualCases);
            if (way == null) {
                if (STATIONS.containsKey(type) || type.startsWith("crafting_")) {
                    skipped.merge(type, 1, Integer::sum);
                } else {
                    skipped.merge(type.isBlank() ? "unknown" : type, 1, Integer::sum);
                }
                continue;
            }
            parsed.merge(type, 1, Integer::sum);
            ways.add(way);
        }
        ways = RecipeParsing.reverseWeights(ways);
        ways.sort(Comparator.comparing(SupplyGraph.Way::id));
        Map<String, List<String>> usedGroups = usedGroups(ways, groups);
        Map<String, SupplyGraph.Item> items = items(ways, usedGroups, manual);
        Map<String, SupplyGraph.Building> buildings = buildings(ways, manual);
        return new Result(usedGroups, ways, items, buildings, parsed, skipped);
    }

    public static List<SupplyGraph.Way> select(List<SupplyGraph.Way> ways, SupplyGraph manual, int max) {
        Set<String> manualMakes = new HashSet<>();
        for (SupplyGraph.Way way : manual.ways()) {
            manualMakes.add(way.makes());
        }
        Map<String, List<SupplyGraph.Way>> byMakes = new TreeMap<>();
        for (SupplyGraph.Way way : ways) {
            byMakes.computeIfAbsent(way.makes(), key -> new ArrayList<>()).add(way);
        }
        List<SupplyGraph.Way> selected = new ArrayList<>();
        byMakes.forEach((makes, candidates) -> {
            if (manualMakes.contains(makes)) {
                return;
            }
            candidates.stream()
                    .sorted(Comparator.comparingInt(
                                    (SupplyGraph.Way way) -> way.consumes().size())
                            .thenComparing(SupplyGraph.Way::id))
                    .limit(max)
                    .forEach(selected::add);
        });
        selected.sort(Comparator.comparing(SupplyGraph.Way::id));
        return selected;
    }

    private static Map<String, List<String>> usedGroups(List<SupplyGraph.Way> ways, Map<String, List<String>> groups) {
        Set<String> used = new LinkedHashSet<>();
        for (SupplyGraph.Way way : ways) {
            for (String input : way.consumes().keySet()) {
                if (input.startsWith(INGREDIENT_TAG) && groups.containsKey(input)) {
                    used.add(input);
                }
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String name : used) {
            result.put(name, groups.get(name));
        }
        return result;
    }

    private static Map<String, SupplyGraph.Item> items(
            List<SupplyGraph.Way> ways, Map<String, List<String>> groups, SupplyGraph manual) {
        Map<String, Integer> stages = new LinkedHashMap<>();
        manual.items().forEach((id, item) -> stages.put(id, item.stage()));
        Set<String> fresh = new LinkedHashSet<>();
        for (SupplyGraph.Way way : ways) {
            collect(way.makes(), manual, fresh);
            way.consumes().keySet().forEach(node -> collectNode(node, groups, manual, fresh));
        }
        groups.values().forEach(members -> members.forEach(member -> collect(member, manual, fresh)));
        fresh.forEach(id -> stages.put(id, Integer.MAX_VALUE));
        int rounds = fresh.size() + ways.size() + 1;
        boolean changed = true;
        while (changed && rounds-- > 0) {
            changed = false;
            for (SupplyGraph.Way way : ways) {
                if (!fresh.contains(way.makes())) {
                    continue;
                }
                int candidate = stage(way.consumes().keySet(), stages, groups, manual);
                if (candidate < stages.getOrDefault(way.makes(), Integer.MAX_VALUE)) {
                    stages.put(way.makes(), candidate);
                    changed = true;
                }
            }
        }
        Map<String, SupplyGraph.Item> items = new TreeMap<>();
        for (String id : fresh) {
            int stage = stages.getOrDefault(id, 1);
            stage = stage == Integer.MAX_VALUE ? 1 : stage;
            items.put(id, new SupplyGraph.Item(id, 0, 1.0, 20, stage, false));
        }
        return items;
    }

    static int stage(
            Set<String> inputs, Map<String, Integer> stages, Map<String, List<String>> groups, SupplyGraph manual) {
        int result = Integer.MAX_VALUE;
        for (String input : inputs) {
            result = Math.min(result, stageNode(input, stages, groups, manual));
        }
        return result;
    }

    private static int stageNode(
            String node, Map<String, Integer> stages, Map<String, List<String>> groups, SupplyGraph manual) {
        if (node.startsWith(INGREDIENT_TAG)) {
            int result = Integer.MAX_VALUE;
            for (String member : groups.getOrDefault(node, List.of())) {
                result = Math.min(result, stageNode(member, stages, groups, manual));
            }
            return result;
        }
        Integer stage = stages.get(node);
        if (stage != null) {
            return stage;
        }
        if (manual.isTool(node)) {
            int split = node.indexOf('_');
            int rank = manual.rank(node.substring(0, split));
            return rank < 0 ? 1 : rank + 1;
        }
        return 1;
    }

    private static void collectNode(
            String node, Map<String, List<String>> groups, SupplyGraph manual, Set<String> fresh) {
        if (node.startsWith(INGREDIENT_TAG)) {
            groups.getOrDefault(node, List.of()).forEach(member -> collect(member, manual, fresh));
            return;
        }
        collect(node, manual, fresh);
    }

    private static void collect(String id, SupplyGraph manual, Set<String> fresh) {
        if (id.isEmpty() || manual.items().containsKey(id) || manual.isTool(id) || id.startsWith(INGREDIENT_TAG)) {
            return;
        }
        fresh.add(id);
    }

    private static Map<String, SupplyGraph.Building> buildings(List<SupplyGraph.Way> ways, SupplyGraph manual) {
        Map<String, SupplyGraph.Building> buildings = new TreeMap<>();
        for (SupplyGraph.Way way : ways) {
            String id = way.building();
            if (id == null || manual.buildings().containsKey(id) || buildings.containsKey(id)) {
                continue;
            }
            buildings.put(
                    id,
                    new SupplyGraph.Building(
                            id, "", BoardCase.GATHER_MATERIALS, 1, 0, null, Map.of(), List.of(), List.of(), true));
        }
        return buildings;
    }

    private static Map<String, BoardCase> manualCases(SupplyGraph manual) {
        Map<String, BoardCase> cases = new LinkedHashMap<>();
        for (SupplyGraph.Way way : manual.ways()) {
            String name = way.boardCase().name();
            if (!way.consumes().isEmpty() && (name.startsWith("CRAFT_") || name.startsWith("SMELT_"))) {
                cases.putIfAbsent(way.makes(), way.boardCase());
            }
        }
        return cases;
    }

    static String result(JsonNode root) {
        return item(root.path("result").path("id").asText());
    }

    static int amount(JsonNode root) {
        return Math.max(1, root.path("result").path("count").asInt(1));
    }

    static String item(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return GraphNames.graphItem(raw);
    }

    static String tagName(String raw) {
        String name = raw.startsWith(INGREDIENT_TAG) ? raw.substring(1) : raw;
        return name.startsWith(NamespacedId.MINECRAFT) ? name.substring(NamespacedId.MINECRAFT.length()) : name;
    }

    static String simple(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }

    static String sanitize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char symbol = Character.toLowerCase(value.charAt(index));
            out.append(Character.isLetterOrDigit(symbol) ? symbol : '_');
        }
        return out.toString();
    }
}
