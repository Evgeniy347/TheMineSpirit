package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class GraphExplain {
    private static final int SHOWN = 12;

    private GraphExplain() {}

    public static GraphDecisions of(GraphPlan plan, long tick) {
        Objects.requireNonNull(plan, "План не может быть null: объяснять нечего");
        List<GraphPlan.Task> tasks = new ArrayList<>(plan.tasks());
        tasks.sort(Comparator.comparingInt(GraphPlan.Task::priority).reversed().thenComparing(GraphPlan.Task::id));
        List<GraphDecisions.Row> rows = new ArrayList<>();
        for (int index = 0; index < tasks.size() && rows.size() < SHOWN; index++) {
            GraphPlan.Task chosen = tasks.get(index);
            GraphPlan.Task next = candidate(tasks, index, chosen);
            rows.add(new GraphDecisions.Row(
                    chosen.spirit().orElse("любой"),
                    chosen.id(),
                    chosen.priority(),
                    chosen.feasible(),
                    next == null ? "" : next.id(),
                    next == null ? 0 : next.priority(),
                    why(chosen, next)));
        }
        return new GraphDecisions(tick, rows);
    }

    private static GraphPlan.@Nullable Task candidate(List<GraphPlan.Task> tasks, int from, GraphPlan.Task chosen) {
        for (int index = from + 1; index < tasks.size(); index++) {
            GraphPlan.Task task = tasks.get(index);
            if (!task.makes().equals(chosen.makes())) {
                return task;
            }
        }
        return null;
    }

    private static String why(GraphPlan.Task chosen, GraphPlan.@Nullable Task next) {
        if (next == null) {
            return "других работ граф не нашёл";
        }
        if (!next.feasible()) {
            return "неосуществим сейчас: нет материалов или инструмента";
        }
        int gap = chosen.priority() - next.priority();
        return gap > 0 ? "вес ниже на " + gap : "тот же вес, ниже по порядку графа";
    }
}
