package com.theminespirit.core.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.FallbackPlanner;
import com.theminespirit.core.planner.Goal;
import com.theminespirit.core.planner.UnplannableGoalException;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.Map;
import java.util.Objects;

public final class PlanningService {
    private final ScriptCatalog catalog;
    private final ConditionCatalog conditions;
    private final FallbackPlanner planner;

    public PlanningService(ScriptCatalog catalog, ConditionCatalog conditions) {
        this.catalog = Objects.requireNonNull(catalog, "catalog не может быть null: план валидируется по каталогу");
        this.conditions =
                Objects.requireNonNull(conditions, "conditions не может быть null: каталог проверок обязателен");
        this.planner = new FallbackPlanner(catalog, conditions);
    }

    public TaskNode validatePlan(JsonNode plan) {
        Objects.requireNonNull(plan, "plan не может быть null: сухой прогон проверяет присланный узел");
        try {
            return PlanParser.parse(plan.toString(), catalog, conditions);
        } catch (PlanValidationException rejected) {
            throw new ApiException(ErrorCode.INVALID_PLAN, rejected.getMessage(), Map.of(), rejected);
        }
    }

    public TaskNode planGoal(TaskRequest request, WorldObservation observation) {
        Objects.requireNonNull(request, "request не может быть null: у цели есть текст и параметры");
        return planGoal(request.goal(), request.params(), observation);
    }

    public TaskNode planGoal(String goal, Map<String, Object> params, WorldObservation observation) {
        Objects.requireNonNull(observation, "observation не может быть null: планировщик выбирает сценарий по миру");
        if (goal == null || goal.isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Цель пуста: для planGoal нужен непустой goal, а для плана — поле plan в теле POST tasks",
                    Map.of("field", "goal"));
        }
        try {
            return planner.plan(new Goal(goal, params), observation).plan();
        } catch (UnplannableGoalException | PlanValidationException rejected) {
            throw new ApiException(ErrorCode.INVALID_PLAN, rejected.getMessage(), Map.of(), rejected);
        }
    }
}
