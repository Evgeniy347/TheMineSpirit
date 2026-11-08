package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record GraphEvent(String name, List<Task> tasks, Map<String, Integer> push, List<String> advice, boolean life) {
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
        this(name, tasks, push, advice, false);
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
                            entry.getValue().path("life").asBoolean(false)));
        });
        return events;
    }

    static int priority(GraphState state, SupplyGraph graph, String key, double value) {
        return priority(state.facts(), graph.events(), key, value, GraphPriorities.work(graph), graph.maxPriority());
    }

    static int priority(
            GraphFacts facts, Map<String, GraphEvent> events, String key, double value, int workMax, int lifeMax) {
        if (key.startsWith("event:")) {
            int start = "event:".length();
            int end = key.indexOf(':', start);
            String name = end < 0 ? key.substring(start) : key.substring(start, end);
            GraphEvent event = events.get(name);
            if (event != null && event.life()) {
                String taskId = end < 0 ? "" : key.substring(end + 1);
                GraphEvent.Task task = event.tasks().stream()
                        .filter(candidate -> candidate.id().equals(taskId))
                        .findFirst()
                        .orElse(null);
                if (task != null) {
                    return LifeThreats.priority(facts, event, task, lifeMax);
                }
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
