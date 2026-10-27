package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record GraphEvent(String name, List<Task> tasks, Map<String, Integer> push, List<String> advice) {
    public record Task(
            String id,
            BoardCase boardCase,
            int base,
            Map<String, Integer> consumes,
            @Nullable String building) {
        public Task {
            consumes = Map.copyOf(consumes);
        }
    }

    public GraphEvent {
        tasks = List.copyOf(tasks);
        push = Map.copyOf(push);
        advice = List.copyOf(advice);
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
                        task.hasNonNull("building") ? task.path("building").asText() : null));
            }
            List<String> advice = new ArrayList<>();
            entry.getValue().path("advice").forEach(rule -> advice.add(rule.asText()));
            events.put(
                    entry.getKey(),
                    new GraphEvent(
                            entry.getKey(), tasks, counts(entry.getValue().path("push")), advice));
        });
        return events;
    }

    static Map<String, Integer> counts(JsonNode node) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        node.properties()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue().asInt()));
        return counts;
    }
}
