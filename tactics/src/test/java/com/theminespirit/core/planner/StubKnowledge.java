package com.theminespirit.core.planner;

import com.theminespirit.core.script.CatalogLimits;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StubKnowledge implements ResourceKnowledge {
    private final Set<String> gatherable;
    private final Map<String, List<Ingredient>> crafts;
    private final Map<String, String> smelts;
    private final Map<String, Integer> outputs;
    private final Map<String, String> stations;
    private final Map<String, String> gatherSources;
    private final Map<String, String> tools;
    private final Map<String, ResourceKnowledge.SourceScript> sourceScripts;

    public StubKnowledge(Set<String> gatherable, Map<String, List<Ingredient>> crafts, Map<String, String> smelts) {
        this(gatherable, crafts, smelts, Map.of());
    }

    public StubKnowledge(
            Set<String> gatherable,
            Map<String, List<Ingredient>> crafts,
            Map<String, String> smelts,
            Map<String, Integer> outputs) {
        this(gatherable, crafts, smelts, outputs, Map.of());
    }

    public StubKnowledge(
            Set<String> gatherable,
            Map<String, List<Ingredient>> crafts,
            Map<String, String> smelts,
            Map<String, Integer> outputs,
            Map<String, String> stations) {
        this(gatherable, crafts, smelts, outputs, stations, Map.of(), Map.of(), Map.of());
    }

    private StubKnowledge(
            Set<String> gatherable,
            Map<String, List<Ingredient>> crafts,
            Map<String, String> smelts,
            Map<String, Integer> outputs,
            Map<String, String> stations,
            Map<String, String> gatherSources,
            Map<String, String> tools,
            Map<String, ResourceKnowledge.SourceScript> sourceScripts) {
        this.gatherable = Set.copyOf(gatherable);
        this.crafts = Map.copyOf(crafts);
        this.smelts = Map.copyOf(smelts);
        this.outputs = Map.copyOf(outputs);
        this.stations = Map.copyOf(stations);
        this.gatherSources = Map.copyOf(gatherSources);
        this.tools = Map.copyOf(tools);
        this.sourceScripts = Map.copyOf(sourceScripts);
    }

    public StubKnowledge withGatherSource(String item, String block) {
        Map<String, String> sources = new HashMap<>(gatherSources);
        sources.put(item, block);
        return new StubKnowledge(gatherable, crafts, smelts, outputs, stations, sources, tools, sourceScripts);
    }

    public StubKnowledge withSourceScript(String item, String script, Map<String, Object> params) {
        return withSourceScript(item, script, params, CatalogLimits.COUNT_MAX);
    }

    public StubKnowledge withSourceScript(String item, String script, Map<String, Object> params, int maxCount) {
        Map<String, ResourceKnowledge.SourceScript> scripts = new HashMap<>(sourceScripts);
        scripts.put(item, new ResourceKnowledge.SourceScript(script, params, maxCount));
        return new StubKnowledge(gatherable, crafts, smelts, outputs, stations, gatherSources, tools, scripts);
    }

    StubKnowledge withTool(String block, String tool) {
        Map<String, String> required = new HashMap<>(tools);
        required.put(block, tool);
        return new StubKnowledge(gatherable, crafts, smelts, outputs, stations, gatherSources, required, sourceScripts);
    }

    @Override
    public Optional<ResourceKnowledge.SourceScript> sourceScript(String item) {
        return Optional.ofNullable(sourceScripts.get(item));
    }

    @Override
    public Optional<String> toolForBlock(String block) {
        return Optional.ofNullable(tools.get(block));
    }

    @Override
    public boolean isGatherable(String item) {
        return gatherable.contains(item);
    }

    @Override
    public String gatherBlockFor(String item) {
        return gatherSources.getOrDefault(item, item);
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

    @Override
    public Optional<String> stationFor(String item) {
        return Optional.ofNullable(stations.get(item));
    }
}
