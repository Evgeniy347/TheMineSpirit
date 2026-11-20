package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

final class RecipeTags {
    private final Map<String, List<String>> raw = new LinkedHashMap<>();
    private final Map<String, List<String>> expanded = new LinkedHashMap<>();
    private final Map<List<String>, String> byMembers = new LinkedHashMap<>();

    RecipeTags(Map<String, String> json) {
        for (Map.Entry<String, String> entry : new TreeMap<>(json).entrySet()) {
            String name = RecipeImport.tagName(entry.getKey());
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
            if (value.startsWith(RecipeImport.INGREDIENT_TAG)) {
                members.addAll(expand(RecipeImport.tagName(value), visiting));
            } else {
                members.add(RecipeImport.item(value));
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
