package com.theminespirit.core.planner;

import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ResourceResolver {
    private ResourceResolver() {
    }

    public static TaskNode resolve(String item, int count, ResourceKnowledge knowledge) {
        return resolve(item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    public static TaskNode resolve(String item, int count, ResourceKnowledge knowledge,
            Map<String, Integer> inventory) {
        return resolve(item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty(), inventory);
    }

    public static TaskNode resolve(String item, int count, ResourceKnowledge knowledge, ScriptCatalog scripts,
            ConditionCatalog conditions) {
        return resolve(item, count, knowledge, scripts, conditions, Map.of());
    }

    public static TaskNode resolve(String item, int count, ResourceKnowledge knowledge, ScriptCatalog scripts,
            ConditionCatalog conditions, Map<String, Integer> inventory) {
        Objects.requireNonNull(item, "item не может быть null: без имени предмета резолвер не найдёт способ добычи");
        Objects.requireNonNull(knowledge,
                "knowledge не может быть null: без знаний резолвер не отличит крафт от сбора");
        Objects.requireNonNull(scripts, "scripts не может быть null");
        Objects.requireNonNull(conditions, "conditions не может быть null");
        Objects.requireNonNull(inventory,
                "inventory не может быть null: без инвентаря резолвер не предпочтёт имеющееся топливо");
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество предмета должно быть больше 0, а получено " + count + ": нулевая цель не имеет смысла");
        }
        TaskNode plan = resolve(item, count, knowledge, new HashSet<>(), new HashMap<>(inventory));
        return PlanValidator.validate(plan, scripts, conditions);
    }

    private static TaskNode resolve(String item, int count, ResourceKnowledge knowledge, Set<String> path,
            Map<String, Integer> remaining) {
        if (!path.add(item)) {
            throw new UnplannableGoalException(item, "Зацикленный способ добычи предмета '" + item
                    + "': предмет снова встретился на цепочке рецептов");
        }
        try {
            if (knowledge.isCraftable(item)) {
                return craftPlan(item, count, knowledge, path, remaining);
            }
            if (knowledge.isSmeltable(item)) {
                return smeltPlan(item, count, knowledge, path, remaining);
            }
            if (knowledge.isGatherable(item)) {
                return new ScriptCall("gather", Map.of("block", item, "count", count));
            }
            throw new UnplannableGoalException(item, "Для предмета '" + item
                    + "' не найден способ добычи: добавьте его в знания как gatherable, smeltable или craftable");
        } finally {
            path.remove(item);
        }
    }

    private static TaskNode craftPlan(String item, int count, ResourceKnowledge knowledge, Set<String> path,
            Map<String, Integer> remaining) {
        int output = knowledge.outputCount(item);
        if (output <= 0) {
            throw new UnplannableGoalException(item, "Рецепт предмета '" + item + "' даёт " + output
                    + " предметов за крафт: выход рецепта обязан быть положительным");
        }
        long crafts = (count + (long) output - 1) / output;
        List<TaskNode> steps = new ArrayList<>();
        for (Ingredient ingredient : knowledge.ingredientsFor(item)) {
            steps.add(resolve(ingredient.item(), ingredientCount(ingredient, crafts, item), knowledge, path, remaining));
        }
        steps.add(new ScriptCall("craft", Map.of("item", item, "count", count)));
        return new SequenceNode(steps);
    }

    private static int ingredientCount(Ingredient ingredient, long crafts, String item) {
        long total = ingredient.count() * crafts;
        if (total > Integer.MAX_VALUE) {
            throw new UnplannableGoalException(item, "Для '" + item + "' нужно " + total + " предмета '"
                    + ingredient.item() + "' на цепочке рецептов: количество переполняет int, уменьшите цель");
        }
        return (int) total;
    }

    private static TaskNode smeltPlan(String item, int count, ResourceKnowledge knowledge, Set<String> path,
            Map<String, Integer> remaining) {
        List<TaskNode> steps = new ArrayList<>(3);
        steps.add(resolve(knowledge.smeltInputFor(item), count, knowledge, path, remaining));
        FuelPlanner.select(item, count, knowledge, path, remaining)
                .ifPresent(fuel -> steps.add(resolve(fuel.fuel(), fuel.count(), knowledge, path, remaining)));
        steps.add(new ScriptCall("smelt", Map.of("item", item, "count", count)));
        return new SequenceNode(steps);
    }
}
