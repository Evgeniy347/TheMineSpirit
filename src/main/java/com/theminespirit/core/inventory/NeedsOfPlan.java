package com.theminespirit.core.inventory;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class NeedsOfPlan {
    public static final int MAX_DEPTH = 8;

    private static final Set<String> ITEM_SCRIPTS = Set.of("craft", "smelt", "equip", "deposit", "acquire");
    private static final String GATHER_SCRIPT = "gather";
    private static final String ITEM_PARAM = "item";
    private static final String BLOCK_PARAM = "block";

    private NeedsOfPlan() {
    }

    public static List<String> collect(TaskNode plan, ResourceKnowledge knowledge) {
        Objects.requireNonNull(plan, "План не может быть null: нужное считают по шагам плана");
        Objects.requireNonNull(knowledge, "Знания о рецептах не могут быть null: "
                + "без них не раскрыть ингредиенты предстоящего крафта");
        List<String> needs = new ArrayList<>();
        collectFrom(plan, knowledge, new HashSet<>(), needs);
        return needs;
    }

    private static void collectFrom(TaskNode node, ResourceKnowledge knowledge, Set<String> path,
            List<String> needs) {
        switch (node) {
            case ScriptCall call -> collectFromScript(call, knowledge, path, needs);
            case SequenceNode sequence -> collectFromSteps(sequence.steps(), knowledge, path, needs);
            case FallbackNode fallback -> collectFromSteps(fallback.steps(), knowledge, path, needs);
            case ParallelNode parallel -> collectFromSteps(parallel.steps(), knowledge, path, needs);
            case RepeatNode repeat -> collectFrom(repeat.step(), knowledge, path, needs);
            case ConditionNode condition -> {
                collectFrom(condition.thenStep(), knowledge, path, needs);
                if (condition.elseStep() != null) {
                    collectFrom(condition.elseStep(), knowledge, path, needs);
                }
            }
        }
    }

    private static void collectFromSteps(List<TaskNode> steps, ResourceKnowledge knowledge, Set<String> path,
            List<String> needs) {
        for (TaskNode step : steps) {
            collectFrom(step, knowledge, path, needs);
        }
    }

    private static void collectFromScript(ScriptCall call, ResourceKnowledge knowledge, Set<String> path,
            List<String> needs) {
        if (ITEM_SCRIPTS.contains(call.script())) {
            String item = text(call, ITEM_PARAM);
            if (item != null) {
                collectWithIngredients(item, knowledge, 0, new HashSet<>(), needs);
            }
            return;
        }
        if (GATHER_SCRIPT.equals(call.script())) {
            String block = text(call, BLOCK_PARAM);
            if (block != null) {
                add(needs, block);
            }
        }
    }

    private static void collectWithIngredients(String item, ResourceKnowledge knowledge, int depth,
            Set<String> path, List<String> needs) {
        if (depth > MAX_DEPTH || !path.add(item)) {
            return;
        }
        add(needs, item);
        for (Ingredient ingredient : knowledge.ingredientsFor(item)) {
            collectWithIngredients(ingredient.item(), knowledge, depth + 1, path, needs);
        }
        path.remove(item);
    }

    private static String text(ScriptCall call, String name) {
        Object value = call.params().get(name);
        return value instanceof String text ? text : null;
    }

    private static void add(List<String> needs, String item) {
        if (!needs.contains(item)) {
            needs.add(item);
        }
    }
}
