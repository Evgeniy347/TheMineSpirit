package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class RecipeImport {
    public static final String INGREDIENT_TAG = "#";
    public static final double REVERSE_WEIGHT = 0.1;
    private static final String COALS = "#coals";
    private static final String ALT_PREFIX = "#alt_";
    private static final String NAMESPACE = "minecraft:";
    private static final Map<String, String> STATIONS = Map.of(
            "smelting", "furnace",
            "blasting", "blast_furnace",
            "smoking", "smoker",
            "campfire_cooking", "campfire",
            "stonecutting", "stonecutter",
            "smithing_transform", "smithing_table");
    private static final Set<String> FUELLED = Set.of("smelting", "blasting", "smoking");

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
        Tags tags = new Tags(tagJson);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, BoardCase> manualCases = manualCases(manual);
        Map<String, Integer> parsed = new TreeMap<>();
        Map<String, Integer> skipped = new TreeMap<>();
        List<SupplyGraph.Way> ways = new ArrayList<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(recipes).entrySet()) {
            JsonNode root = JsonResource.parse(entry.getValue(), "рецепт " + entry.getKey());
            String type = simple(root.path("type").asText());
            SupplyGraph.Way way = parse(entry.getKey(), type, root, tags, groups, manualCases);
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
        ways = reverseWeights(ways);
        ways.sort(Comparator.comparing(SupplyGraph.Way::id));
        Map<String, List<String>> usedGroups = usedGroups(ways, groups);
        Map<String, SupplyGraph.Item> items = items(ways, usedGroups, manual);
        Map<String, SupplyGraph.Building> buildings = buildings(ways, manual);
        return new Result(usedGroups, ways, items, buildings, parsed, skipped);
    }

    public static List<SupplyGraph.Way> select(List<SupplyGraph.Way> ways, SupplyGraph manual, int max) {
        Map<String, Integer> manualWays = new TreeMap<>();
        for (SupplyGraph.Way way : manual.ways()) {
            manualWays.merge(way.makes(), 1, Integer::sum);
        }
        Map<String, List<SupplyGraph.Way>> byMakes = new TreeMap<>();
        for (SupplyGraph.Way way : ways) {
            byMakes.computeIfAbsent(way.makes(), key -> new ArrayList<>()).add(way);
        }
        List<SupplyGraph.Way> selected = new ArrayList<>();
        byMakes.forEach((makes, candidates) -> {
            int left = max - manualWays.getOrDefault(makes, 0);
            if (left <= 0) {
                return;
            }
            candidates.stream()
                    .sorted(Comparator.comparingInt(
                                    (SupplyGraph.Way way) -> way.consumes().size())
                            .thenComparing(SupplyGraph.Way::id))
                    .limit(left)
                    .forEach(selected::add);
        });
        selected.sort(Comparator.comparing(SupplyGraph.Way::id));
        return selected;
    }

    private static SupplyGraph.@Nullable Way parse(
            String id,
            String type,
            JsonNode root,
            Tags tags,
            Map<String, List<String>> groups,
            Map<String, BoardCase> manualCases) {
        return switch (type) {
            case "crafting_shaped" -> shaped(id, root, tags, groups, manualCases);
            case "crafting_shapeless" -> shapeless(id, root, tags, groups, manualCases);
            case "smelting", "blasting", "smoking", "campfire_cooking", "stonecutting" ->
                cooking(id, type, root, tags, groups, manualCases);
            case "smithing_transform" -> smithing(id, root, tags, groups, manualCases);
            default -> null;
        };
    }

    private static SupplyGraph.@Nullable Way shaped(
            String id, JsonNode root, Tags tags, Map<String, List<String>> groups, Map<String, BoardCase> manualCases) {
        JsonNode pattern = root.path("pattern");
        JsonNode key = root.path("key");
        if (!pattern.isArray() || pattern.isEmpty() || !key.isObject()) {
            return null;
        }
        int rows = pattern.size();
        int cols = 0;
        for (JsonNode row : pattern) {
            cols = Math.max(cols, row.asText().length());
        }
        Map<String, Integer> consumes = new LinkedHashMap<>();
        for (JsonNode row : pattern) {
            String line = row.asText();
            for (int index = 0; index < line.length(); index++) {
                char mark = line.charAt(index);
                if (mark == ' ') {
                    continue;
                }
                String node = resolve(key.path(String.valueOf(mark)), tags, groups, id + "_" + mark);
                if (node.isEmpty()) {
                    return null;
                }
                consumes.merge(node, 1, Integer::sum);
            }
        }
        return complete(
                id, consumes, rows > 2 || cols > 2 ? "crafting_table" : null, result(root), amount(root), manualCases);
    }

    private static SupplyGraph.@Nullable Way shapeless(
            String id, JsonNode root, Tags tags, Map<String, List<String>> groups, Map<String, BoardCase> manualCases) {
        JsonNode ingredients = root.path("ingredients");
        if (!ingredients.isArray() || ingredients.isEmpty()) {
            return null;
        }
        Map<String, Integer> consumes = new LinkedHashMap<>();
        int index = 0;
        for (JsonNode ingredient : ingredients) {
            String node = resolve(ingredient, tags, groups, id + "_" + index);
            if (node.isEmpty()) {
                return null;
            }
            consumes.merge(node, 1, Integer::sum);
            index++;
        }
        return complete(
                id,
                consumes,
                ingredients.size() > 4 ? "crafting_table" : null,
                result(root),
                amount(root),
                manualCases);
    }

    private static SupplyGraph.@Nullable Way cooking(
            String id,
            String type,
            JsonNode root,
            Tags tags,
            Map<String, List<String>> groups,
            Map<String, BoardCase> manualCases) {
        String node = resolve(root.path("ingredient"), tags, groups, id);
        if (node.isEmpty()) {
            return null;
        }
        Map<String, Integer> consumes = new LinkedHashMap<>();
        consumes.merge(node, 1, Integer::sum);
        if (FUELLED.contains(type)) {
            consumes.merge(COALS, 1, Integer::sum);
        }
        return complete(id, consumes, STATIONS.get(type), result(root), amount(root), manualCases);
    }

    private static SupplyGraph.@Nullable Way smithing(
            String id, JsonNode root, Tags tags, Map<String, List<String>> groups, Map<String, BoardCase> manualCases) {
        Map<String, Integer> consumes = new LinkedHashMap<>();
        for (String field : List.of("template", "base", "addition")) {
            JsonNode ingredient = root.get(field);
            if (ingredient == null) {
                continue;
            }
            String node = resolve(ingredient, tags, groups, id + "_" + field);
            if (node.isEmpty()) {
                return null;
            }
            consumes.merge(node, 1, Integer::sum);
        }
        return complete(id, consumes, STATIONS.get("smithing_transform"), result(root), amount(root), manualCases);
    }

    private static SupplyGraph.@Nullable Way complete(
            String id,
            Map<String, Integer> consumes,
            @Nullable String building,
            String makes,
            int amount,
            Map<String, BoardCase> manualCases) {
        if (makes.isEmpty() || consumes.isEmpty()) {
            return null;
        }
        return new SupplyGraph.Way(
                "auto_" + sanitize(id),
                manualCases.getOrDefault(makes, BoardCase.GATHER_MATERIALS),
                makes,
                amount,
                1.0,
                consumes,
                null,
                null,
                building,
                null,
                Map.of());
    }

    private static String resolve(JsonNode ingredient, Tags tags, Map<String, List<String>> groups, String context) {
        if (ingredient.isArray()) {
            Set<String> members = new LinkedHashSet<>();
            for (JsonNode alternative : ingredient) {
                String node = resolve(alternative, tags, groups, context);
                if (node.isEmpty()) {
                    continue;
                }
                if (node.startsWith(INGREDIENT_TAG)) {
                    members.addAll(tags.members(node.substring(1)));
                } else {
                    members.add(node);
                }
            }
            if (members.isEmpty()) {
                return "";
            }
            if (members.size() == 1) {
                return members.iterator().next();
            }
            List<String> list = List.copyOf(members);
            String tag = tags.groupOf(list);
            String name = tag == null ? ALT_PREFIX + sanitize(context) : INGREDIENT_TAG + tag;
            groups.putIfAbsent(name, list);
            return name;
        }
        if (ingredient.isObject()) {
            if (ingredient.has("tag")) {
                String tag = tagName(ingredient.path("tag").asText());
                if (!tags.has(tag) || tags.members(tag).isEmpty()) {
                    return "";
                }
                String name = INGREDIENT_TAG + tag;
                groups.putIfAbsent(name, tags.members(tag));
                return name;
            }
            if (ingredient.has("item")) {
                return item(ingredient.path("item").asText());
            }
        }
        if (ingredient.isTextual()) {
            return item(ingredient.asText());
        }
        return "";
    }

    private static List<SupplyGraph.Way> reverseWeights(List<SupplyGraph.Way> ways) {
        Map<String, List<Integer>> byMakes = new LinkedHashMap<>();
        for (int index = 0; index < ways.size(); index++) {
            byMakes.computeIfAbsent(ways.get(index).makes(), key -> new ArrayList<>())
                    .add(index);
        }
        boolean[] reverse = new boolean[ways.size()];
        for (int index = 0; index < ways.size(); index++) {
            SupplyGraph.Way way = ways.get(index);
            for (String input : way.consumes().keySet()) {
                if (input.startsWith(INGREDIENT_TAG)) {
                    continue;
                }
                if (input.equals(way.makes())) {
                    reverse[index] = true;
                    continue;
                }
                for (int other : byMakes.getOrDefault(input, List.of())) {
                    if (other != index && ways.get(other).consumes().containsKey(way.makes())) {
                        reverse[index] = true;
                        reverse[other] = true;
                    }
                }
            }
        }
        List<SupplyGraph.Way> weighted = new ArrayList<>(ways.size());
        for (int index = 0; index < ways.size(); index++) {
            SupplyGraph.Way way = ways.get(index);
            if (!reverse[index]) {
                weighted.add(way);
                continue;
            }
            weighted.add(new SupplyGraph.Way(
                    way.id(),
                    way.boardCase(),
                    way.makes(),
                    way.yield(),
                    REVERSE_WEIGHT,
                    way.consumes(),
                    way.tool(),
                    way.tier(),
                    way.building(),
                    way.world(),
                    way.needs()));
        }
        return weighted;
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

    private static int stage(
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
                            id, BoardCase.GATHER_MATERIALS, 1, 0, null, Map.of(), List.of(), List.of(), true));
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

    private static String result(JsonNode root) {
        return item(root.path("result").path("id").asText());
    }

    private static int amount(JsonNode root) {
        return Math.max(1, root.path("result").path("count").asInt(1));
    }

    private static String item(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return GraphNames.graphItem(raw);
    }

    private static String tagName(String raw) {
        String name = raw.startsWith(INGREDIENT_TAG) ? raw.substring(1) : raw;
        return name.startsWith(NAMESPACE) ? name.substring(NAMESPACE.length()) : name;
    }

    private static String simple(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }

    private static String sanitize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char symbol = Character.toLowerCase(value.charAt(index));
            out.append(Character.isLetterOrDigit(symbol) ? symbol : '_');
        }
        return out.toString();
    }

    private static final class Tags {
        private final Map<String, List<String>> raw = new LinkedHashMap<>();
        private final Map<String, List<String>> expanded = new LinkedHashMap<>();
        private final Map<List<String>, String> byMembers = new LinkedHashMap<>();

        Tags(Map<String, String> json) {
            for (Map.Entry<String, String> entry : new TreeMap<>(json).entrySet()) {
                String name = tagName(entry.getKey());
                JsonNode root = JsonResource.parse(entry.getValue(), "тег " + entry.getKey());
                List<String> values = new ArrayList<>();
                for (JsonNode value : root.path("values")) {
                    String id = value.isObject() ? value.path("id").asText() : value.asText();
                    if (!id.isBlank()) {
                        values.add(id);
                    }
                }
                raw.put(name, values);
            }
            for (String name : raw.keySet()) {
                expanded.put(name, List.copyOf(expand(name, new LinkedHashSet<>())));
            }
            expanded.forEach((name, members) -> {
                if (members.size() > 1 && raw.containsKey(name)) {
                    List<String> key = members.stream().sorted().toList();
                    byMembers.merge(key, name, (left, right) -> shorter(left, right));
                }
            });
        }

        boolean has(String name) {
            return expanded.containsKey(name);
        }

        List<String> members(String name) {
            return expanded.getOrDefault(name, List.of());
        }

        @Nullable
        String groupOf(List<String> members) {
            return byMembers.get(members.stream().sorted().toList());
        }

        private Set<String> expand(String name, Set<String> visiting) {
            Set<String> members = new LinkedHashSet<>();
            if (!visiting.add(name)) {
                return members;
            }
            for (String value : raw.getOrDefault(name, List.of())) {
                if (value.startsWith(INGREDIENT_TAG)) {
                    members.addAll(expand(tagName(value), visiting));
                } else {
                    members.add(item(value));
                }
            }
            visiting.remove(name);
            return members;
        }

        private static String shorter(String left, String right) {
            if (left.length() != right.length()) {
                return left.length() < right.length() ? left : right;
            }
            return left.compareTo(right) <= 0 ? left : right;
        }
    }
}
