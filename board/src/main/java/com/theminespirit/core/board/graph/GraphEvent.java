package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record GraphEvent(String name, List<Task> tasks, Map<String, Integer> push, List<String> advice, int band) {
    public record Task(
            String id,
            BoardCase boardCase,
            int base,
            Map<String, Integer> consumes,
            @Nullable String building,
            boolean implemented) {
        public Task {
            consumes = Map.copyOf(consumes);
        }
    }

    public GraphEvent {
        tasks = List.copyOf(tasks);
        push = Map.copyOf(push);
        advice = List.copyOf(advice);
        if (band < 0) {
            throw new IllegalArgumentException(
                    "Полоса события " + band + " отрицательна: события вне полосы задают нулём, а не минусом");
        }
    }

    public GraphEvent(String name, List<Task> tasks, Map<String, Integer> push, List<String> advice) {
        this(name, tasks, push, advice, 0);
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
                        task.hasNonNull("building") ? task.path("building").asText() : null,
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
                            entry.getValue().path("band").asInt(0)));
        });
        return events;
    }

    static int priority(Map<String, GraphEvent> events, String key, double value, int max) {
        if (key.startsWith("event:")) {
            int start = "event:".length();
            int end = key.indexOf(':', start);
            String name = end < 0 ? key.substring(start) : key.substring(start, end);
            GraphEvent event = events.get(name);
            if (event != null && event.band() > 0) {
                return GraphFlow.bandPriority(value, max, event.band());
            }
        }
        return GraphFlow.priority(value, max);
    }

    static Map<String, Integer> counts(JsonNode node) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        node.properties()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue().asInt()));
        return counts;
    }
}
