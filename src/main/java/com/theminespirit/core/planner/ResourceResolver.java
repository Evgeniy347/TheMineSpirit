package com.theminespirit.core.planner;

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
import java.util.Optional;
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
        TaskNode plan = new Resolution(knowledge, new HashMap<>(inventory)).resolve(item, count);
        return PlanValidator.validate(plan, scripts, conditions);
    }

    private static final class Resolution {
        private final ResourceKnowledge knowledge;
        private final Map<String, Integer> remaining;
        private final Set<String> path = new HashSet<>();
        private final Set<String> plannedStations = new HashSet<>();

        private Resolution(ResourceKnowledge knowledge, Map<String, Integer> remaining) {
            this.knowledge = knowledge;
            this.remaining = remaining;
        }

        private TaskNode resolve(String item, int count) {
            if (!path.add(item)) {
                throw new UnplannableGoalException(item, "Зацикленный способ добычи предмета '" + item
                        + "': предмет снова встретился на цепочке рецептов");
            }
            try {
                if (knowledge.isCraftable(item)) {
                    return craftPlan(item, count);
                }
                if (knowledge.isSmeltable(item)) {
                    return smeltPlan(item, count);
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

        private TaskNode craftPlan(String item, int count) {
            int output = knowledge.outputCount(item);
            if (output <= 0) {
                throw new UnplannableGoalException(item, "Рецепт предмета '" + item + "' даёт " + output
                        + " предметов за крафт: выход рецепта обязан быть положительным");
            }
            long crafts = (count + (long) output - 1) / output;
            List<TaskNode> steps = new ArrayList<>();
            for (Ingredient ingredient : knowledge.ingredientsFor(item)) {
                steps.add(resolve(ingredient.item(), ingredientCount(ingredient, crafts, item)));
            }
            stationStep(item).ifPresent(steps::add);
            steps.add(new ScriptCall("craft", Map.of("item", item, "count", count)));
            return new SequenceNode(steps);
        }

        private TaskNode smeltPlan(String item, int count) {
            List<TaskNode> steps = new ArrayList<>(4);
            steps.add(resolve(knowledge.smeltInputFor(item), count));
            FuelPlanner.select(item, count, knowledge, path, remaining)
                    .ifPresent(fuel -> steps.add(resolve(fuel.fuel(), fuel.count())));
            stationStep(item).ifPresent(steps::add);
            steps.add(new ScriptCall("smelt", Map.of("item", item, "count", count)));
            return new SequenceNode(steps);
        }

        private Optional<TaskNode> stationStep(String item) {
            Optional<String> station = knowledge.stationFor(item);
            if (station.isEmpty() || !plannedStations.add(station.get())) {
                return Optional.empty();
            }
            return Optional.of(resolve(station.get(), 1));
        }

        private static int ingredientCount(Ingredient ingredient, long crafts, String item) {
            long total = ingredient.count() * crafts;
            if (total > Integer.MAX_VALUE) {
                throw new UnplannableGoalException(item, "Для '" + item + "' нужно " + total + " предмета '"
                        + ingredient.item() + "' на цепочке рецептов: количество переполняет int, уменьшите цель");
            }
            return (int) total;
        }
    }
}
