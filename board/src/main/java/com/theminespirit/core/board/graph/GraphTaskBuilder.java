package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.audit.AuditGraphMapping;
import com.theminespirit.core.board.audit.Group;
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
        if (key.startsWith(EVENT + "audit:")) {
            return auditTask(key, priority);
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
            BoardCase buildingCase = Objects.requireNonNull(
                    building.boardCase(),
                    "Постройка " + id + " не имеет кейса: это предпосылка мира — духи её ищут, а не строят");
            Map<String, Integer> materials = GraphLack.materials(graph, state, id);
            boolean tooled = (building.tool() == null || state.bestTier(building.tool()) >= 0)
                    && building.requires().stream().allMatch(required -> state.built(required) == Built.BUILT);
            return new GraphPlan.Task(
                    key,
                    buildingCase,
                    id,
                    Optional.empty(),
                    priority,
                    materials,
                    tooled && GraphLack.share(graph, state, building.materials()) >= graph.buildStartShare(),
                    false,
                    graph.implemented(id),
                    graph.costs().ticks(key, buildingCase));
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

    private GraphPlan.Task auditTask(String key, int priority) {
        String taskId = key.substring((EVENT + "audit:").length());
        GraphEvent event = Objects.requireNonNull(
                graph.events().get("audit"), "Событие audit не описано в графе: группа не получит кейс");
        GraphEvent.Task chosen =
                Objects.requireNonNull(GraphEvent.find(event, taskId), "Шаблон audit не описывает группу " + taskId);
        Group group = Objects.requireNonNull(
                AuditGraphFacts.group(state, taskId), "Группа аудита " + taskId + " исчезла из фактов графа");
        if (AuditGraphMapping.UNMAPPED.contains(group.kind().name())) {
            throw new IllegalStateException("Группа " + taskId + " оставлена без маппинга: граф не выдаст действие");
        }
        if (chosen.boardCase() != AuditGraphMapping.caseOf(group.kind())) {
            throw new IllegalStateException("Группа " + taskId + ": шаблон audit задаёт " + chosen.boardCase()
                    + ", маппинг требует " + AuditGraphMapping.caseOf(group.kind()));
        }
        Map<String, Integer> materials = AuditGraphFacts.materials(group);
        return new GraphPlan.Task(
                key,
                chosen.boardCase(),
                taskId,
                GraphEventFacts.owner(state, chosen),
                priority,
                materials,
                GraphEventFacts.feasible(graph, state, chosen) && GraphLack.stocked(graph, state, materials),
                false,
                chosen.implemented(),
                graph.costs().ticks(key, chosen.boardCase()));
    }
}
