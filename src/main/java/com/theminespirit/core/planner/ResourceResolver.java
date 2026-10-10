package com.theminespirit.core.planner;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ResourceResolver {
    private ResourceResolver() {
    }

    public static TaskNode resolve(String item, int count, ResourceKnowledge knowledge) {
        Objects.requireNonNull(item, "item не может быть null: без имени предмета резолвер не найдёт способ добычи");
        Objects.requireNonNull(knowledge,
                "knowledge не может быть null: без знаний резолвер не отличит крафт от сбора");
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество предмета должно быть больше 0, а получено " + count + ": нулевая цель не имеет смысла");
        }
        return resolve(item, count, knowledge, new HashSet<>());
    }

    private static TaskNode resolve(String item, int count, ResourceKnowledge knowledge, Set<String> path) {
        if (!path.add(item)) {
            throw new UnplannableGoalException("Зацикленный способ добычи предмета '" + item
                    + "': предмет снова встретился на цепочке рецептов");
        }
        try {
            if (knowledge.isCraftable(item)) {
                return craftPlan(item, count, knowledge, path);
            }
            if (knowledge.isSmeltable(item)) {
                return smeltPlan(item, count, knowledge, path);
            }
            if (knowledge.isGatherable(item)) {
                return new ScriptCall("gather", Map.of("block", item, "count", count));
            }
            throw new UnplannableGoalException("Для предмета '" + item
                    + "' не найден способ добычи: добавьте его в знания как gatherable, smeltable или craftable");
        } finally {
            path.remove(item);
        }
    }

    private static TaskNode craftPlan(String item, int count, ResourceKnowledge knowledge, Set<String> path) {
        List<TaskNode> steps = new ArrayList<>();
        for (Ingredient ingredient : knowledge.ingredientsFor(item)) {
            steps.add(resolve(ingredient.item(), ingredient.count() * count, knowledge, path));
        }
        steps.add(new ScriptCall("craft", Map.of("item", item, "count", count)));
        return new SequenceNode(steps);
    }

    private static TaskNode smeltPlan(String item, int count, ResourceKnowledge knowledge, Set<String> path) {
        List<TaskNode> steps = new ArrayList<>(2);
        steps.add(resolve(knowledge.smeltInputFor(item), count, knowledge, path));
        steps.add(new ScriptCall("smelt", Map.of("item", item, "count", count)));
        return new SequenceNode(steps);
    }
}