package com.theminespirit.core.policy;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class WaterTasks {
    private static final Set<String> SWIMMING = Set.of("swim", "cross_water", "fish", "dig_out");

    private WaterTasks() {}

    public static boolean allowsSwimming(@Nullable String script) {
        return script != null && SWIMMING.contains(script);
    }

    public static boolean allowsSwimming(@Nullable String script, @Nullable TaskNode plan) {
        if (allowsSwimming(script)) {
            return true;
        }
        if (script != null && !script.isBlank()) {
            return false;
        }
        return plan != null && allowsSwimming(firstScript(plan));
    }

    private static @Nullable String firstScript(@Nullable TaskNode node) {
        if (node == null) {
            return null;
        }
        return switch (node) {
            case ScriptCall call -> call.script();
            case SequenceNode sequence -> firstScript(sequence.steps());
            case FallbackNode fallback -> firstScript(fallback.steps());
            case ParallelNode parallel -> firstScript(parallel.steps());
            case RepeatNode repeat -> firstScript(repeat.step());
            case ConditionNode condition -> {
                String then = firstScript(condition.thenStep());
                yield then != null ? then : firstScript(condition.elseStep());
            }
        };
    }

    private static @Nullable String firstScript(List<TaskNode> steps) {
        for (TaskNode step : steps) {
            String found = firstScript(step);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
