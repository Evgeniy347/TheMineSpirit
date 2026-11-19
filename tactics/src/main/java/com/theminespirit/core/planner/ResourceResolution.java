package com.theminespirit.core.planner;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.CraftedItems;
import com.theminespirit.core.planner.api.FuelPlanner;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.planner.api.UnplannableGoalException;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class ResourceResolution {
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";

    private final ResourceKnowledge knowledge;
    private final Map<String, Integer> remaining;
    private final Set<String> presentStations;
    private final Set<String> path = new HashSet<>();
    private final Set<String> plannedStations = new HashSet<>();
    private final Set<String> plannedTools = new HashSet<>();
    private final @Nullable AcquireSupplies chests;
    private final Set<String> withdrawn = new HashSet<>();

    ResourceResolution(ResourceKnowledge knowledge, ResourceHoldings holdings) {
        this.knowledge = knowledge;
        this.remaining = new HashMap<>(holdings.inventory());
        this.presentStations = holdings.stations();
        this.chests = holdings.chests();
    }

    TaskNode resolve(String item, int count) {
        return resolve(item, count, true);
    }

    TaskNode resolve(String item, int count, boolean topLevel) {
        if (!path.add(item)) {
            throw new UnplannableGoalException(
                    item,
                    "Зацикленный способ добычи предмета '" + item + "': предмет снова встретился на цепочке рецептов");
        }
        try {
            if (knowledge.isCraftable(item)) {
                return craftPlan(item, count, topLevel);
            }
            if (knowledge.isBrushed(item)) {
                return batched("brush", item, count, topLevel);
            }
            TaskNode special = SourcePlans.source(knowledge, item, count);
            if (special != null) return special;
            if (dropsFromAnotherBlock(item)) {
                return gatherPlan(item, count, topLevel);
            }
            if (knowledge.isSmeltable(item)) {
                return smeltPlan(item, count, topLevel);
            }
            if (knowledge.isCraftedProduct(item)) throw CraftedItems.error(item);
            if (knowledge.isGatherable(item)) {
                return gatherPlan(item, count, topLevel);
            }
            throw new UnplannableGoalException(
                    item,
                    "Для предмета '" + item
                            + "' не найден способ добычи: добавьте его в знания как gatherable, smeltable или craftable");
        } finally {
            path.remove(item);
        }
    }

    private boolean dropsFromAnotherBlock(String item) {
        return !ItemName.same(knowledge.gatherBlockFor(item), item);
    }

    private TaskNode gatherPlan(String item, int count, boolean topLevel) {
        Optional<TaskNode> tool = toolStep(knowledge.gatherBlockFor(item));
        TaskNode gather = batched("gather", item, count, topLevel);
        return tool.map(step -> (TaskNode) new SequenceNode(List.of(step, gather)))
                .orElse(gather);
    }

    private Optional<TaskNode> toolStep(String block) {
        Optional<String> tool = knowledge.toolForBlock(block);
        if (tool.isEmpty() || hasInInventory(tool.get()) || !plannedTools.add(ToolTiers.normalize(tool.get()))) {
            return Optional.empty();
        }
        return Optional.of(resolve(tool.get(), 1, false));
    }

    private boolean hasInInventory(String item) {
        List<String> acceptable = ToolTiers.acceptable(item);
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (entry.getValue() > 0 && acceptable.contains(ToolTiers.normalize(entry.getKey()))) {
                return true;
            }
        }
        return false;
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

    private static ScriptCall gather(String block, int count) {
        int radius = block.endsWith("_log") ? CatalogLimits.HARVEST_RADIUS_MAX : CatalogLimits.HARVEST_RADIUS_DEFAULT;
        return new ScriptCall("gather", Map.of("block", block, COUNT, count, RADIUS, radius));
    }

    private ScriptCall single(String script, String item, int count) {
        return switch (script) {
            case "gather" -> gather(knowledge.gatherBlockFor(item), count);
            case "craft" ->
                new ScriptCall(
                        "craft", Map.of("item", item, COUNT, count, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT));
            case "smelt" ->
                new ScriptCall(
                        "smelt", Map.of("item", item, COUNT, count, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT));
            case "brush" -> new ScriptCall("brush", Map.of(COUNT, count, RADIUS, CatalogLimits.HERD_RADIUS_DEFAULT));
            default ->
                throw new IllegalArgumentException(
                        "Скрипт добычи '" + script + "' неизвестен: " + "батчинг знает только gather, craft и smelt");
        };
    }

    private TaskNode craftPlan(String item, int count, boolean topLevel) {
        int output = knowledge.outputCount(item);
        if (output <= 0) {
            throw new UnplannableGoalException(
                    item,
                    "Рецепт предмета '" + item + "' даёт " + output
                            + " предметов за крафт: выход рецепта обязан быть положительным");
        }
        long crafts = (count + (long) output - 1) / output;
        List<TaskNode> steps = new ArrayList<>();
        for (Ingredient ingredient : knowledge.ingredientsFor(item)) {
            ingredient(ingredient.item(), ingredientCount(ingredient, crafts, item))
                    .ifPresent(steps::add);
        }
        stationStep(item).ifPresent(steps::add);
        steps.add(batched("craft", item, count, topLevel));
        return new SequenceNode(steps);
    }

    private Optional<TaskNode> ingredient(String item, int count) {
        int left = count - takeFromInventory(item, count);
        List<TaskNode> steps = new ArrayList<>();
        if (left > 0 && chests != null && withdrawn.add(ToolTiers.normalize(item))) {
            for (ScriptCall call : chests.withdraw(item, left)) {
                left -= call.params().get(COUNT) instanceof Integer taken ? taken : 0;
                steps.add(call);
            }
        }
        if (left > 0) {
            steps.add(resolve(item, left, false));
        }
        if (steps.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(steps.size() == 1 ? steps.get(0) : new SequenceNode(steps));
    }

    private int takeFromInventory(String item, int count) {
        String wanted = ToolTiers.normalize(item);
        int taken = 0;
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (taken >= count || !ToolTiers.normalize(entry.getKey()).equals(wanted) || entry.getValue() <= 0) {
                continue;
            }
            int take = Math.min(count - taken, entry.getValue());
            entry.setValue(entry.getValue() - take);
            taken += take;
        }
        return taken;
    }

    private TaskNode smeltPlan(String item, int count, boolean topLevel) {
        List<TaskNode> steps = new ArrayList<>(4);
        ingredient(knowledge.smeltInputFor(item), count).ifPresent(steps::add);
        FuelPlanner.select(item, count, knowledge, path, remaining)
                .ifPresent(fuel -> steps.add(resolve(fuel.fuel(), fuel.count(), false)));
        stationStep(item).ifPresent(steps::add);
        steps.add(batched("smelt", item, count, topLevel));
        return new SequenceNode(steps);
    }

    private Optional<TaskNode> stationStep(String item) {
        Optional<String> station = knowledge.stationFor(item);
        if (station.isEmpty()) {
            return Optional.empty();
        }
        String id = station.get();
        if (presentStations.contains(id) || !plannedStations.add(id)) {
            return Optional.empty();
        }
        return Optional.of(resolve(id, 1));
    }

    private static int ingredientCount(Ingredient ingredient, long crafts, String item) {
        long total = ingredient.count() * crafts;
        if (total > Integer.MAX_VALUE) {
            throw new UnplannableGoalException(
                    item,
                    "Для '" + item + "' нужно " + total + " предмета '" + ingredient.item()
                            + "' на цепочке рецептов: количество переполняет int, уменьшите цель");
        }
        return (int) total;
    }
}
