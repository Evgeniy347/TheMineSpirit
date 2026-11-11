package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public record GraphPlan(List<Task> tasks, List<Notice> notices, List<Advice> advice) {
    public record Task(
            String id,
            BoardCase boardCase,
            String makes,
            Optional<String> spirit,
            int priority,
            Map<String, Integer> consumes,
            boolean feasible,
            boolean chance,
            boolean implemented,
            int ticks,
            String blocked) {
        public Task {
            consumes = Map.copyOf(consumes);
            Objects.requireNonNull(blocked, "Причина блокировки не может быть null: свободную задачу задают пустой");
            if (priority < 0 || priority > BoardTuning.MAX_PRIORITY) {
                throw new IllegalArgumentException("Приоритет задания " + id + " " + priority
                        + " вне доски [0, " + BoardTuning.MAX_PRIORITY
                        + "]: потолок даёт граф, а не доска и не clamp источника");
            }
        }

        public Task(
                String id,
                BoardCase boardCase,
                String makes,
                Optional<String> spirit,
                int priority,
                Map<String, Integer> consumes,
                boolean feasible,
                boolean chance,
                boolean implemented,
                int ticks) {
            this(id, boardCase, makes, spirit, priority, consumes, feasible, chance, implemented, ticks, "");
        }

        public boolean raised() {
            return priority > 0;
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

    public boolean raised(String id) {
        return task(id).map(Task::raised).orElse(false);
    }

    public int priority(String id) {
        return task(id).map(Task::priority).orElse(-1);
    }

    public List<Task> standing(Set<String> live, Function<String, Optional<BoardStatus>> status) {
        return standing(tasks, live, status);
    }

    public static List<Task> standing(
            List<Task> tasks, Set<String> live, Function<String, Optional<BoardStatus>> status) {
        List<Task> demands = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.implemented() || !task.raised() || !task.feasible()) {
                continue;
            }
            String ticket = GraphTickets.id(task);
            if (!live.contains(ticket)) {
                continue;
            }
            Optional<BoardStatus> current = status.apply(ticket);
            if (current.isPresent()
                    && (current.orElseThrow() == BoardStatus.DONE
                            || current.orElseThrow() == BoardStatus.FAILED
                            || current.orElseThrow() == BoardStatus.CANCELLED)) {
                continue;
            }
            demands.add(task);
        }
        return List.copyOf(demands);
    }
}
