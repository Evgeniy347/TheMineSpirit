package com.theminespirit.core.planner;

import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
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
            return resolve(item, count, true);
        }

        private TaskNode resolve(String item, int count, boolean topLevel) {
            if (!path.add(item)) {
                throw new UnplannableGoalException(item, "Зацикленный способ добычи предмета '" + item
                        + "': предмет снова встретился на цепочке рецептов");
            }
            try {
                if (knowledge.isCraftable(item)) {
                    return craftPlan(item, count, topLevel);
                }
                if (knowledge.isSmeltable(item)) {
                    return smeltPlan(item, count, topLevel);
                }
                if (knowledge.isGatherable(item)) {
                    return batched("gather", item, count, topLevel);
                }
                throw new UnplannableGoalException(item, "Для предмета '" + item
                        + "' не найден способ добычи: добавьте его в знания как gatherable, smeltable или craftable");
            } finally {
                path.remove(item);
            }
        }

        private TaskNode batched(String script, String item, int count, boolean topLevel) {
            if (topLevel || count <= CatalogLimits.COUNT_MAX) {
                return single(script, item, count);
            }
            List<TaskNode> batches = new ArrayList<>();
            int left = count;
            while (left > 0) {
                int batch = Math.min(left, CatalogLimits.COUNT_MAX);
                batches.add(single(script, item, batch));
                left -= batch;
            }
            return new SequenceNode(batches);
        }

        private ScriptCall single(String script, String item, int count) {
            return switch (script) {
                case "gather" -> new ScriptCall("gather",
                        Map.of("block", knowledge.gatherBlockFor(item), "count", count,
                                "radius", CatalogLimits.HARVEST_RADIUS_DEFAULT));
                case "craft" -> new ScriptCall("craft", Map.of("item", item, "count", count,
                        "radius", CatalogLimits.STATION_RADIUS_DEFAULT));
                case "smelt" -> new ScriptCall("smelt", Map.of("item", item, "count", count,
                        "radius", CatalogLimits.STATION_RADIUS_DEFAULT));
                default -> throw new IllegalArgumentException("Скрипт добычи '" + script + "' неизвестен: "
                        + "батчинг знает только gather, craft и smelt");
            };
        }

        private TaskNode craftPlan(String item, int count, boolean topLevel) {
            int output = knowledge.outputCount(item);
            if (output <= 0) {
                throw new UnplannableGoalException(item, "Рецепт предмета '" + item + "' даёт " + output
                        + " предметов за крафт: выход рецепта обязан быть положительным");
            }
            long crafts = (count + (long) output - 1) / output;
            List<TaskNode> steps = new ArrayList<>();
            for (Ingredient ingredient : knowledge.ingredientsFor(item)) {
                steps.add(resolve(ingredient.item(), ingredientCount(ingredient, crafts, item), false));
            }
            stationStep(item).ifPresent(steps::add);
            steps.add(batched("craft", item, count, topLevel));
            return new SequenceNode(steps);
        }

        private TaskNode smeltPlan(String item, int count, boolean topLevel) {
            List<TaskNode> steps = new ArrayList<>(4);
            steps.add(resolve(knowledge.smeltInputFor(item), count, false));
            FuelPlanner.select(item, count, knowledge, path, remaining)
                    .ifPresent(fuel -> steps.add(resolve(fuel.fuel(), fuel.count(), false)));
            stationStep(item).ifPresent(steps::add);
            steps.add(batched("smelt", item, count, topLevel));
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
