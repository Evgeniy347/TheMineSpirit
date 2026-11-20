package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class GraphTaskBuilder {
    private static final String BUILDING = GraphPlanner.BUILDING;
    private static final String TOOL = GraphPlanner.TOOL;
    private static final String EVENT = GraphPlanner.EVENT;
    private static final String EXPLORE = GraphPlanner.EXPLORE;
    private static final String STATION = GraphPlanner.STATION;
    private static final int EXPLORE_TICKS = 2400;
    private static final char AT = '@';

    private final SupplyGraph graph;
    private final GraphState state;
    private final GraphReach reach;
    private final GraphChoiceRules choices;
    private final Set<String> chances;

    GraphTaskBuilder(
            SupplyGraph graph, GraphState state, GraphReach reach, GraphChoiceRules choices, Set<String> chances) {
        this.graph = graph;
        this.state = state;
        this.reach = reach;
        this.choices = choices;
        this.chances = chances;
    }

    boolean task(String key) {
        if (key.startsWith(BUILDING) || key.startsWith(EVENT) || key.startsWith(EXPLORE)) {
            return true;
        }
        int at = key.indexOf(AT);
        return !key.startsWith(TOOL)
                && reach.way(at < 0 ? key : key.substring(0, at)).isPresent();
    }

    Optional<String> station(String key) {
        int at = key.indexOf(AT);
        return reach.way(at < 0 ? key : key.substring(0, at))
                .map(SupplyGraph.Way::building)
                .filter(building -> graph.costs().stationCapacity().containsKey(building));
    }

    Optional<GraphPlan.Task> stationTask(String building, double value, Map<String, Double> scores) {
        long busy = scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && task(entry.getKey()))
                .filter(entry ->
                        station(entry.getKey()).filter(building::equals).isPresent())
                .filter(entry -> task(entry.getKey(), 0).feasible())
                .count();
        int capacity = graph.costs().stationCapacity().getOrDefault(building, Integer.MAX_VALUE);
        if (state.built(building) != Built.BUILT || busy <= (long) state.stations(building) * capacity) {
            return Optional.empty();
        }
        SupplyGraph.Building known = Objects.requireNonNull(
                graph.buildings().get(building), "Станция " + building + " не описана среди построек графа");
        Map<String, Integer> materials = GraphLack.materials(graph, state, building);
        return Optional.of(new GraphPlan.Task(
                STATION + building,
                known.boardCase(),
                building,
                Optional.empty(),
                GraphFlow.priority(value * graph.decay(), GraphPriorities.work(graph)),
                materials,
                GraphLack.stocked(graph, state, materials),
                false,
                graph.implemented(building),
                graph.costs().ticks(STATION + building, known.boardCase())));
    }

    GraphPlan.Task task(String key, int priority) {
        if (key.startsWith(EXPLORE)) {
            return new GraphPlan.Task(
                    key,
                    BoardCase.GATHER_MATERIALS,
                    key.substring(EXPLORE.length()),
                    Optional.empty(),
                    priority,
                    Map.of(),
                    true,
                    false,
                    false,
                    EXPLORE_TICKS);
        }
        if (key.startsWith(EVENT)) {
            String[] parts = key.split(":", 3);
            GraphEvent event = Objects.requireNonNull(
                    graph.events().get(parts[1]), "Событие " + parts[1] + " задания не описано в графе");
            GraphEvent.Task chosen = Objects.requireNonNull(
                    GraphEvent.find(event, parts[2]),
                    "Событие " + parts[1] + " не описывает задание " + parts[2] + ": узел графа обязан помнить кейс");
            return new GraphPlan.Task(
                    key,
                    chosen.boardCase(),
                    parts[2],
                    GraphEventFacts.owner(state, chosen),
                    priority,
                    GraphEventFacts.consumes(state, chosen),
                    GraphEventFacts.feasible(graph, state, chosen),
                    false,
                    chosen.implemented(),
                    graph.costs().ticks(key, chosen.boardCase()));
        }
        if (key.startsWith(BUILDING)) {
            String id = key.substring(BUILDING.length());
            SupplyGraph.Building building = Objects.requireNonNull(
                    graph.buildings().get(id), "Постройка " + id + " задания не описана в графе");
            Map<String, Integer> materials = GraphLack.materials(graph, state, id);
            boolean tooled = (building.tool() == null || state.bestTier(building.tool()) >= 0)
                    && building.requires().stream().allMatch(required -> state.built(required) == Built.BUILT);
            return new GraphPlan.Task(
                    key,
                    building.boardCase(),
                    id,
                    Optional.empty(),
                    priority,
                    materials,
                    tooled && GraphLack.share(graph, state, building.materials()) >= graph.buildStartShare(),
                    false,
                    graph.implemented(id),
                    graph.costs().ticks(key, building.boardCase()));
        }
        int at = key.indexOf(AT);
        String wayId = at < 0 ? key : key.substring(0, at);
        SupplyGraph.Way way = reach.way(wayId).orElseThrow();
        Optional<String> spirit = at < 0 ? Optional.empty() : Optional.of(key.substring(at + 1));
        boolean tooled = way.tool() == null
                || state.bestTier(way.tool()) >= graph.rank(way.tier() == null ? "wooden" : way.tier());
        return new GraphPlan.Task(
                key,
                way.boardCase(),
                way.makes(),
                spirit,
                priority,
                way.consumes(),
                tooled
                        && GraphLack.housed(state, way)
                        && reach.available(way)
                        && GraphLack.stocked(graph, state, way.consumes())
                        && GraphLack.stocked(graph, state, way.needs()),
                chances.contains(key),
                graph.implemented(wayId),
                choices.wayTicks(key, way));
    }
}
