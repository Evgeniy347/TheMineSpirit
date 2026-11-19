package com.theminespirit.core.planner;

import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.ScriptCatalog;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ResourceResolver {

    private ResourceResolver() {}

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
        return resolve(
                item, count, knowledge, scripts, conditions, new ResourceHoldings(inventory, presentStations, null));
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
                new ResourceHoldings(supplies.inventory(), supplies.nearbyStations(), supplies));
    }

    private static TaskNode resolve(
            String item,
            int count,
            ResourceKnowledge knowledge,
            ScriptCatalog scripts,
            ConditionCatalog conditions,
            ResourceHoldings holdings) {
        Objects.requireNonNull(item, "item не может быть null: без имени предмета резолвер не найдёт способ добычи");
        Objects.requireNonNull(
                knowledge, "knowledge не может быть null: без знаний резолвер не отличит крафт от сбора");
        Objects.requireNonNull(scripts, "scripts не может быть null");
        Objects.requireNonNull(conditions, "conditions не может быть null");
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество предмета должно быть больше 0, а получено " + count + ": нулевая цель не имеет смысла");
        }
        TaskNode plan = new ResourceResolution(knowledge, holdings).resolve(item, count);
        return PlanValidator.validate(plan, scripts, conditions);
    }
}
