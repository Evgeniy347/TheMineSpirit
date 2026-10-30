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
import org.jspecify.annotations.Nullable;

public final class ResourceResolver {
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";

    private ResourceResolver() {}

    public static TaskNode resolve(String item, int count, ResourceKnowledge knowledge) {
        return resolve(item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    public static TaskNode resolve(
            String item, int count, ResourceKnowledge knowledge, Map<String, Integer> inventory) {
        return resolve(item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty(), inventory);
    }

    public static TaskNode resolve(
            String item, int count, ResourceKnowledge knowledge, Map<String, Integer> inventory, Set<String> stations) {
        return resolve(
                item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty(), inventory, stations);
    }

    public static TaskNode resolve(
            String item, int count, ResourceKnowledge knowledge, ScriptCatalog scripts, ConditionCatalog conditions) {
        return resolve(item, count, knowledge, scripts, conditions, Map.of());
    }

    public static TaskNode resolve(
            String item,
            int count,
            ResourceKnowledge knowledge,
            ScriptCatalog scripts,
            ConditionCatalog conditions,
            Map<String, Integer> inventory) {
        return resolve(item, count, knowledge, scripts, conditions, inventory, Set.of());
    }

    public static TaskNode resolve(
            String item,
            int count,
            ResourceKnowledge knowledge,
            ScriptCatalog scripts,
            ConditionCatalog conditions,
            Map<String, Integer> inventory,
            Set<String> presentStations) {
        Objects.requireNonNull(inventory, "inventory не может быть null: без инвентаря резолвер не предпочтёт топливо");
        Objects.requireNonNull(
                presentStations,
                "presentStations не может быть null: без снимка станций резолвер построит печь там, где она уже стоит");
        return resolve(item, count, knowledge, scripts, conditions, new Holdings(inventory, presentStations, null));
    }

    public static TaskNode resolve(
            String item,
            int count,
            ResourceKnowledge knowledge,
            ScriptCatalog scripts,
            ConditionCatalog conditions,
            AcquireSupplies supplies) {
        Objects.requireNonNull(supplies, "supplies не может быть null: без снабжения не прочитать инвентарь");
        return resolve(
                item,
                count,
                knowledge,
                scripts,
                conditions,
                new Holdings(supplies.inventory(), supplies.nearbyStations(), supplies));
    }

    private static TaskNode resolve(
            String item,
            int count,
            ResourceKnowledge knowledge,
            ScriptCatalog scripts,
            ConditionCatalog conditions,
            Holdings holdings) {
        Objects.requireNonNull(item, "item не может быть null: без имени предмета резолвер не найдёт способ добычи");
        Objects.requireNonNull(
                knowledge, "knowledge не может быть null: без знаний резолвер не отличит крафт от сбора");
        Objects.requireNonNull(scripts, "scripts не может быть null");
        Objects.requireNonNull(conditions, "conditions не может быть null");
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество предмета должно быть больше 0, а получено " + count + ": нулевая цель не имеет смысла");
        }
        TaskNode plan = new Resolution(knowledge, holdings).resolve(item, count);
        return PlanValidator.validate(plan, scripts, conditions);
    }

    private record Holdings(
            Map<String, Integer> inventory,
            Set<String> stations,
            @Nullable AcquireSupplies chests) {}

    private static final class Resolution {
        private final ResourceKnowledge knowledge;
        private final Map<String, Integer> remaining;
        private final Set<String> presentStations;
        private final Set<String> path = new HashSet<>();
        private final Set<String> plannedStations = new HashSet<>();
        private final Set<String> plannedTools = new HashSet<>();

        private final @Nullable AcquireSupplies chests;
        private final Set<String> withdrawn = new HashSet<>();

        private Resolution(ResourceKnowledge knowledge, Holdings holdings) {
            this.knowledge = knowledge;
            this.remaining = new HashMap<>(holdings.inventory());
            this.presentStations = holdings.stations();
            this.chests = holdings.chests();
        }

        private TaskNode resolve(String item, int count) {
            return resolve(item, count, true);
        }

        private TaskNode resolve(String item, int count, boolean topLevel) {
            if (!path.add(item)) {
                throw new UnplannableGoalException(
                        item,
                        "Зацикленный способ добычи предмета '" + item
                                + "': предмет снова встретился на цепочке рецептов");
            }
            try {
                if (knowledge.isCraftable(item)) {
                    return craftPlan(item, count, topLevel);
                }
                if (knowledge.isBrushed(item)) {
                    return batched("brush", item, count, topLevel);
                }
                if (dropsFromAnotherBlock(item)) {
                    return gatherPlan(item, count, topLevel);
                }
                if (knowledge.isSmeltable(item)) {
                    return smeltPlan(item, count, topLevel);
                }
                if (knowledge.isGatherable(item)) {
                    if (knowledge.isCraftedProduct(item) && !dropsFromAnotherBlock(item)) {
                        throw CraftedItems.error(item);
                    }
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
            return !knowledge.gatherBlockFor(item).equals(item);
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
            int radius =
                    block.endsWith("_log") ? CatalogLimits.HARVEST_RADIUS_MAX : CatalogLimits.HARVEST_RADIUS_DEFAULT;
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
                case "brush" ->
                    new ScriptCall("brush", Map.of(COUNT, count, RADIUS, CatalogLimits.HERD_RADIUS_DEFAULT));
                default ->
                    throw new IllegalArgumentException("Скрипт добычи '" + script + "' неизвестен: "
                            + "батчинг знает только gather, craft и smelt");
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
            steps.add(resolve(knowledge.smeltInputFor(item), count, false));
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
}
