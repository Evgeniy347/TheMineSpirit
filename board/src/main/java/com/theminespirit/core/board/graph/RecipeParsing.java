package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.api.BoardCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class RecipeParsing {
    private RecipeParsing() {}

    static SupplyGraph.@Nullable Way parse(
            String id,
            String type,
            JsonNode root,
            RecipeTags tags,
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

    static SupplyGraph.@Nullable Way shaped(
            String id,
            JsonNode root,
            RecipeTags tags,
            Map<String, List<String>> groups,
            Map<String, BoardCase> manualCases) {
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
                id,
                consumes,
                rows > 2 || cols > 2 ? "crafting_table" : null,
                RecipeImport.result(root),
                RecipeImport.amount(root),
                manualCases);
    }

    static SupplyGraph.@Nullable Way shapeless(
            String id,
            JsonNode root,
            RecipeTags tags,
            Map<String, List<String>> groups,
            Map<String, BoardCase> manualCases) {
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
                RecipeImport.result(root),
                RecipeImport.amount(root),
                manualCases);
    }

    static SupplyGraph.@Nullable Way cooking(
            String id,
            String type,
            JsonNode root,
            RecipeTags tags,
            Map<String, List<String>> groups,
            Map<String, BoardCase> manualCases) {
        String node = resolve(root.path("ingredient"), tags, groups, id);
        if (node.isEmpty()) {
            return null;
        }
        Map<String, Integer> consumes = new LinkedHashMap<>();
        consumes.merge(node, 1, Integer::sum);
        if (RecipeImport.FUELLED.contains(type)) {
            consumes.merge(RecipeImport.COALS, 1, Integer::sum);
        }
        return complete(
                id,
                consumes,
                RecipeImport.STATIONS.get(type),
                RecipeImport.result(root),
                RecipeImport.amount(root),
                manualCases);
    }

    static SupplyGraph.@Nullable Way smithing(
            String id,
            JsonNode root,
            RecipeTags tags,
            Map<String, List<String>> groups,
            Map<String, BoardCase> manualCases) {
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
        return complete(
                id,
                consumes,
                RecipeImport.STATIONS.get("smithing_transform"),
                RecipeImport.result(root),
                RecipeImport.amount(root),
                manualCases);
    }

    static SupplyGraph.@Nullable Way complete(
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
                "auto_" + RecipeImport.sanitize(id),
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

    static String resolve(JsonNode ingredient, RecipeTags tags, Map<String, List<String>> groups, String context) {
        if (ingredient.isArray()) {
            Set<String> members = new LinkedHashSet<>();
            for (JsonNode alternative : ingredient) {
                String node = resolve(alternative, tags, groups, context);
                if (node.isEmpty()) {
                    continue;
                }
                if (node.startsWith(RecipeImport.INGREDIENT_TAG)) {
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
            String name = tag == null
                    ? RecipeImport.ALT_PREFIX + RecipeImport.sanitize(context)
                    : RecipeImport.INGREDIENT_TAG + tag;
            groups.putIfAbsent(name, list);
            return name;
        }
        if (ingredient.isObject()) {
            if (ingredient.has("tag")) {
                String tag = RecipeImport.tagName(ingredient.path("tag").asText());
                if (!tags.has(tag) || tags.members(tag).isEmpty()) {
                    return "";
                }
                String name = RecipeImport.INGREDIENT_TAG + tag;
                groups.putIfAbsent(name, tags.members(tag));
                return name;
            }
            if (ingredient.has("item")) {
                return RecipeImport.item(ingredient.path("item").asText());
            }
        }
        if (ingredient.isTextual()) {
            return RecipeImport.item(ingredient.asText());
        }
        return "";
    }

    static List<SupplyGraph.Way> reverseWeights(List<SupplyGraph.Way> ways) {
        Map<String, List<Integer>> byMakes = new LinkedHashMap<>();
        for (int index = 0; index < ways.size(); index++) {
            byMakes.computeIfAbsent(ways.get(index).makes(), key -> new ArrayList<>())
                    .add(index);
        }
        boolean[] reverse = new boolean[ways.size()];
        for (int index = 0; index < ways.size(); index++) {
            SupplyGraph.Way way = ways.get(index);
            for (String input : way.consumes().keySet()) {
                if (input.startsWith(RecipeImport.INGREDIENT_TAG)) {
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
                    RecipeImport.REVERSE_WEIGHT,
                    way.consumes(),
                    way.tool(),
                    way.tier(),
                    way.building(),
                    way.world(),
                    way.needs()));
        }
        return weighted;
    }
}
