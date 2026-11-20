package com.theminespirit.core.inventory;

import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RecipeKnowledge implements ResourceKnowledge {
    private static final Map<String, List<Ingredient>> LADDER_AND_BASIC = Map.ofEntries(
            Map.entry("iron_pickaxe", List.of(new Ingredient("iron_ingot", 3), new Ingredient("stick", 2))),
            Map.entry("oak_planks", List.of()),
            Map.entry("bread", List.of(new Ingredient("wheat", 3))),
            Map.entry("torch", List.of(new Ingredient("stick", 1), new Ingredient("coal", 1))),
            Map.entry("ladder_0", List.of(new Ingredient("ladder_1", 1))),
            Map.entry("ladder_1", List.of(new Ingredient("ladder_2", 1))),
            Map.entry("ladder_2", List.of(new Ingredient("ladder_3", 1))),
            Map.entry("ladder_3", List.of(new Ingredient("ladder_4", 1))),
            Map.entry("ladder_4", List.of(new Ingredient("ladder_5", 1))),
            Map.entry("ladder_5", List.of(new Ingredient("ladder_6", 1))),
            Map.entry("ladder_6", List.of(new Ingredient("ladder_7", 1))),
            Map.entry("ladder_7", List.of(new Ingredient("ladder_8", 1))),
            Map.entry("ladder_8", List.of(new Ingredient("ladder_9", 1))),
            Map.entry("ladder_9", List.of(new Ingredient("ladder_10", 1))));

    private final Map<String, List<Ingredient>> crafts;
    private final Map<String, String> smelts;
    private final Map<String, Integer> outputs;
    private final Set<String> gatherable;

    RecipeKnowledge() {
        this(LADDER_AND_BASIC, Map.of("iron_ingot", "raw_iron"), Map.of(), Set.of("oak_log"));
    }

    RecipeKnowledge(
            Map<String, List<Ingredient>> crafts,
            Map<String, String> smelts,
            Map<String, Integer> outputs,
            Set<String> gatherable) {
        this.crafts = crafts;
        this.smelts = smelts;
        this.outputs = outputs;
        this.gatherable = gatherable;
    }

    @Override
    public boolean isGatherable(String item) {
        return gatherable.contains(item);
    }

    @Override
    public boolean isCraftable(String item) {
        return crafts.containsKey(item);
    }

    @Override
    public int outputCount(String item) {
        return outputs.getOrDefault(item, 1);
    }

    @Override
    public List<Ingredient> ingredientsFor(String item) {
        return crafts.getOrDefault(item, List.of());
    }

    @Override
    public boolean isSmeltable(String item) {
        return smelts.containsKey(item);
    }

    @Override
    public String smeltInputFor(String item) {
        return smelts.get(item);
    }
}
