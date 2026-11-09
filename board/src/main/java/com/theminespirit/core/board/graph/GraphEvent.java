package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record GraphEvent(
        String name, List<Task> tasks, Map<String, Integer> push, List<String> advice, boolean life, boolean vital) {
    public record Task(
            String id,
            BoardCase boardCase,
            int base,
            Map<String, Integer> consumes,
            @Nullable String building,
            @Nullable String world,
            @Nullable String onlyWhenEmpty,
            @Nullable String owner,
            boolean implemented) {
        public Task {
            consumes = Map.copyOf(consumes);
        }
    }

    public GraphEvent {
        tasks = List.copyOf(tasks);
        push = Map.copyOf(push);
        advice = List.copyOf(advice);
    }

    public GraphEvent(String name, List<Task> tasks, Map<String, Integer> push, List<String> advice) {
        this(name, tasks, push, advice, false, false);
    }

    public GraphEvent(String name, List<Task> tasks, Map<String, Integer> push, List<String> advice, boolean life) {
        this(name, tasks, push, advice, life, false);
    }

    static Map<String, GraphEvent> withRepairs(
            Map<String, GraphEvent> events, Map<String, SupplyGraph.Building> buildings) {
        GraphEvent template = events.get("building_damaged");
        if (template == null || template.tasks().isEmpty()) {
            return events;
        }
        Map<String, GraphEvent> repaired = new LinkedHashMap<>(events);
        for (String id : buildings.keySet()) {
            String name = GraphWorldEvents.REPAIR + id;
            if (repaired.containsKey(name)) {
                continue;
            }
            List<Task> tasks = new ArrayList<>();
            for (Task task : template.tasks()) {
                tasks.add(new Task(
                        "repair:" + id,
                        task.boardCase(),
                        task.base(),
                        task.consumes(),
                        id,
                        task.world(),
                        task.onlyWhenEmpty(),
                        task.owner(),
                        task.implemented()));
            }
            repaired.put(
                    name,
                    new GraphEvent(name, tasks, template.push(), template.advice(), template.life(), template.vital()));
        }
        return repaired;
    }

    static Map<String, GraphEvent> parse(JsonNode root) {
        Map<String, GraphEvent> events = new LinkedHashMap<>();
        root.path("events").properties().forEach(entry -> {
            List<Task> tasks = new ArrayList<>();
            for (JsonNode task : entry.getValue().path("tasks")) {
                tasks.add(new Task(
                        task.path("id").asText(),
                        BoardCase.valueOf(task.path("case").asText()),
                        task.path("base").asInt(),
                        counts(task.path("consumes")),
                        text(task, "building"),
                        text(task, "world"),
                        text(task, "onlyWhenEmpty"),
                        text(task, "owner"),
                        task.path("implemented").asBoolean(true)));
            }
            List<String> advice = new ArrayList<>();
            entry.getValue().path("advice").forEach(rule -> advice.add(rule.asText()));
            events.put(
                    entry.getKey(),
                    new GraphEvent(
                            entry.getKey(),
                            tasks,
                            counts(entry.getValue().path("push")),
                            advice,
                            entry.getValue().path("life").asBoolean(false),
                            entry.getValue().path("vital").asBoolean(false)));
        });
        return events;
    }

    static int priority(GraphState state, SupplyGraph graph, String key, double value) {
        return priority(
                state.facts(),
                graph.events(),
                key,
                value,
                GraphPriorities.work(graph),
                graph.maxPriority(),
                graph.costs().life());
    }

    static int priority(
            GraphFacts facts,
            Map<String, GraphEvent> events,
            String key,
            double value,
            int workMax,
            int lifeMax,
            GraphLife life) {
        if (key.startsWith("event:")) {
            int start = "event:".length();
            int end = key.indexOf(':', start);
            String name = end < 0 ? key.substring(start) : key.substring(start, end);
            GraphEvent event = events.get(name);
            String taskId = end < 0 ? "" : key.substring(end + 1);
            GraphEvent.Task task = event == null
                    ? null
                    : event.tasks().stream()
                            .filter(candidate -> candidate.id().equals(taskId))
                            .findFirst()
                            .orElse(null);
            if (event != null && event.life() && task != null) {
                return LifeThreats.priority(facts, event, task, lifeMax, life);
            }
            if (event != null && event.vital() && task != null) {
                return VitalNeeds.priority(
                        task.base(), event.tasks().stream().map(Task::base).toList(), workMax);
            }
        }
        return GraphFlow.priority(value, workMax);
    }

    static Map<String, Integer> counts(JsonNode node) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        node.properties()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue().asInt()));
        return counts;
    }

    private static @Nullable String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asText() : null;
    }
}
