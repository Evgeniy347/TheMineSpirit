package com.theminespirit.core.planner;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.PlanBudget;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.ScriptCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class AcquireTaskPlan {
    private final ResourceKnowledge knowledge;
    private final AcquireSupplies supplies;
    private final ScriptCatalog catalog;
    private final ConditionCatalog conditions;

    AcquireTaskPlan(
            ResourceKnowledge knowledge, AcquireSupplies supplies, ScriptCatalog catalog, ConditionCatalog conditions) {
        this.knowledge = Objects.requireNonNull(
                knowledge, "knowledge не может быть null: без знаний резолвер не отличит крафт от сбора");
        this.supplies =
                Objects.requireNonNull(supplies, "supplies не может быть null: подплан читает инвентарь и сундуки");
        this.catalog = Objects.requireNonNull(
                catalog, "catalog не может быть null: без каталога подплан не пройдёт валидацию");
        this.conditions = Objects.requireNonNull(
                conditions, "conditions не может быть null: без каталога проверок подплан не пройдёт валидацию");
    }

    record Built(TaskNode plan, int chestPlanned) {}

    Built build(
            String item,
            int remaining,
            int chestPlanned,
            @Nullable BlockCoord gatherAt,
            int gatherAtLeast,
            boolean firstAttempt) {
        List<ScriptCall> withdraws = StorageItems.container(item) ? List.of() : supplies.withdraw(item, remaining);
        int fromChests = withdraws.stream().mapToInt(step -> count(step)).sum();
        int need = remaining - (firstAttempt ? fromChests : chestPlanned);
        List<TaskNode> nodes = new ArrayList<>(withdraws);
        if (need > 0) {
            if (gatherAt != null) {
                nodes.add(new ScriptCall("goto", Map.of("x", gatherAt.x(), "y", gatherAt.y(), "z", gatherAt.z())));
            }
            int trip = gatherAt == null ? need : Math.max(need, gatherAtLeast);
            nodes.add(ResourceResolver.resolve(item, trip, knowledge, catalog, conditions, supplies));
        }
        TaskNode plan = PlanValidator.validate(new SequenceNode(nodes), catalog, conditions);
        PlanBudget.Budget budget = PlanBudget.measure(plan);
        if (budget.exceeds()) {
            throw new PlanValidationException("Подплан acquire '" + item + "' ×" + remaining + " выходит за бюджет: "
                    + budget.describe() + ": разбейте цель на меньшие задачи");
        }
        return new Built(plan, fromChests);
    }

    private static int count(ScriptCall step) {
        Object value = step.params().get("count");
        return value instanceof Integer integer ? integer : 0;
    }
}
