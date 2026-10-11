package com.theminespirit.core.planner;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class StubKnowledge implements ResourceKnowledge {
    private final Set<String> gatherable;
    private final Map<String, List<Ingredient>> crafts;
    private final Map<String, String> smelts;
    private final Map<String, Integer> outputs;

    StubKnowledge(Set<String> gatherable, Map<String, List<Ingredient>> crafts, Map<String, String> smelts) {
        this(gatherable, crafts, smelts, Map.of());
    }

    StubKnowledge(Set<String> gatherable, Map<String, List<Ingredient>> crafts, Map<String, String> smelts,
            Map<String, Integer> outputs) {
        this.gatherable = Set.copyOf(gatherable);
        this.crafts = Map.copyOf(crafts);
        this.smelts = Map.copyOf(smelts);
        this.outputs = Map.copyOf(outputs);
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