package com.theminespirit.core.plan;

import java.util.List;
import java.util.Objects;

public final class PlanBudget {
    public record Budget(int depth, int nodes) {
        public boolean exceeds() {
            return depth > PlanLimits.MAX_DEPTH || nodes > PlanLimits.MAX_NODES;
        }

        public String describe() {
            return "глубина " + depth + " из " + PlanLimits.MAX_DEPTH + ", узлов " + nodes + " из "
                    + PlanLimits.MAX_NODES;
        }
    }

    private PlanBudget() {}

    public static Budget measure(TaskNode root) {
        Objects.requireNonNull(root, "root не может быть null: бюджет считается по дереву задач");
        return new Budget(
                depth(root),
                1
                        + children(root).stream()
                                .mapToInt(child -> measure(child).nodes())
                                .sum());
    }

    private static int depth(TaskNode node) {
        List<TaskNode> children = children(node);
        if (children.isEmpty()) {
            return 1;
        }
        return 1 + children.stream().mapToInt(child -> depth(child)).max().orElse(0);
    }

    private static List<TaskNode> children(TaskNode node) {
        return switch (node) {
            case ScriptCall ignored -> List.of();
            case SequenceNode sequence -> sequence.steps();
            case FallbackNode fallback -> fallback.steps();
            case ParallelNode parallel -> parallel.steps();
            case RepeatNode repeat -> List.of(repeat.step());
            case ConditionNode condition ->
                condition.elseStep() == null
                        ? List.of(condition.thenStep())
                        : List.of(condition.thenStep(), condition.elseStep());
        };
    }
}
