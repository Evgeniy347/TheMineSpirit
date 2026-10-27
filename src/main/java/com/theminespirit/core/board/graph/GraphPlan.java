package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record GraphPlan(List<Task> tasks, List<Notice> notices, List<Advice> advice, int pushes) {
    public record Task(
            String id,
            BoardCase boardCase,
            String makes,
            Optional<String> spirit,
            int priority,
            Map<String, Integer> consumes,
            boolean feasible,
            boolean chance,
            boolean implemented) {
        public Task {
            consumes = Map.copyOf(consumes);
        }
    }

    public record Notice(String building, String reason) {}

    public record Advice(String event, String rule) {}

    public GraphPlan {
        tasks = List.copyOf(tasks);
        notices = List.copyOf(notices);
        advice = List.copyOf(advice);
    }

    public Optional<Task> task(String id) {
        return tasks.stream().filter(task -> task.id().equals(id)).findFirst();
    }

    public int priority(String id) {
        return task(id).map(Task::priority).orElse(-1);
    }
}
