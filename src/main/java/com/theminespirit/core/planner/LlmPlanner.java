package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.Objects;
import java.util.Optional;

public final class LlmPlanner implements Planner {
    private final LlmClient client;
    private final ScriptCatalog catalog;
    private final ConditionCatalog conditions;
    private final Planner fallback;

    public LlmPlanner(LlmClient client, ScriptCatalog catalog, Planner fallback) {
        this(client, catalog, ConditionCatalog.empty(), fallback);
    }

    public LlmPlanner(LlmClient client, ScriptCatalog catalog, ConditionCatalog conditions, Planner fallback) {
        this.client = Objects.requireNonNull(client, "client не может быть null: без клиента ИИ некому строить план");
        this.catalog = Objects.requireNonNull(catalog, "catalog не может быть null: план валидируется по каталогу скриптов");
        this.conditions = Objects.requireNonNull(conditions,
                "conditions не может быть null: каталог проверок обязателен");
        this.fallback = Objects.requireNonNull(fallback,
                "fallback не может быть null: невалидный ответ ИИ должен передаваться в запасной планировщик");
    }

    @Override
    public PlanResult plan(Goal goal, WorldObservation observation) {
        String prompt = buildPrompt(goal, observation);
        try {
            TaskNode plan = PlanParser.parse(client.complete(prompt), catalog, conditions);
            return new PlanResult(plan, PlanSource.LLM, Optional.empty());
        } catch (LlmException | PlanValidationException error) {
            PlanResult fallbackResult = fallback.plan(goal, observation);
            return new PlanResult(fallbackResult.plan(), PlanSource.FALLBACK, Optional.of(error.getMessage()));
        }
    }

    public String buildPrompt(Goal goal, WorldObservation observation) {
        Objects.requireNonNull(goal, "goal не может быть null: в промпте нужен текст цели");
        Objects.requireNonNull(observation, "observation не может быть null: в промпте нужен слепок мира");
        StringBuilder prompt = new StringBuilder("Цель: ");
        prompt.append(goal.text()).append('\n');
        prompt.append("Наблюдение: ").append(observation.hash()).append('\n');
        prompt.append("Скрипты: ");
        for (ScriptSpec spec : catalog.all()) {
            prompt.append(spec.id()).append(' ');
        }
        return prompt.toString();
    }
}