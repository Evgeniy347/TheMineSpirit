package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmPlannerTests {
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();
    private static final WorldObservation OBSERVATION = Observations.empty("spirit_1");
    private static final Goal GOAL = new Goal("срубить дерево", Map.of());
    private static final ScriptCall FALLBACK_PLAN = new ScriptCall("return_home", Map.of());
    private static final String VALID_PLAN_JSON = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":5}}";

    @Test
    void plan_ClientReturnsValidJson_ReturnsParsedPlan() {
        StubLlmClient client = StubLlmClient.responding(VALID_PLAN_JSON);
        RecordingPlanner fallback = RecordingPlanner.returning(FALLBACK_PLAN);
        LlmPlanner planner = new LlmPlanner(client, CATALOG, fallback);

        TaskNode plan = planner.plan(GOAL, OBSERVATION);

        assertEquals(new ScriptCall("wait", Map.of("ticks", 5)), plan,
                "Валидный JSON-план от клиента обязан исполняться, а не заменяться шаблоном: иначе качество "
                        + "решения ИИ выбрасывается и дух всегда делает одно и то же");
        assertTrue(fallback.isEmpty(),
                "Валидный план не должен передаваться в fallback: вызов запасного планировщика при рабочем "
                        + "ответе ИИ удваивает работу и может украсть сценарий");
        assertEquals(planner.buildPrompt(GOAL, OBSERVATION), client.lastPrompt(),
                "Клиент обязан получить ровно тот промпт, что строит buildPrompt: рассогласование промптов "
                        + "делает ответ ИИ непредсказуемым");
    }

    @Test
    void plan_ClientReturnsInvalidJson_DelegatesToFallback() {
        StubLlmClient client = StubLlmClient.responding("{\"type\":\"script\",\"script\":\"no_such_script\"}");
        RecordingPlanner fallback = RecordingPlanner.returning(FALLBACK_PLAN);
        LlmPlanner planner = new LlmPlanner(client, CATALOG, fallback);

        TaskNode plan = planner.plan(GOAL, OBSERVATION);

        assertEquals(FALLBACK_PLAN, plan,
                "Невалидный план ИИ обязан уходить в fallback: иначе неизвестный скрипт дойдёт до движка "
                        + "и задача сорвётся в тике вместо аккуратного плана по шаблону");
        assertEquals(GOAL, fallback.lastGoal(),
                "Fallback обязан получать исходную цель: несовпадение цели превратит довыполнение "
                        + "в исполнение чужой задачи");
        assertEquals(OBSERVATION, fallback.lastObservation(),
                "Fallback обязан получать исходное наблюдение: другое наблюдение даст план для другого мира");
    }

    @Test
    void plan_ClientThrowsLlmException_DelegatesToFallback() {
        StubLlmClient client = StubLlmClient.throwing(new LlmException("сеть недоступна"));
        RecordingPlanner fallback = RecordingPlanner.returning(FALLBACK_PLAN);
        LlmPlanner planner = new LlmPlanner(client, CATALOG, fallback);

        TaskNode plan = planner.plan(GOAL, OBSERVATION);

        assertEquals(FALLBACK_PLAN, plan,
                "Сбой клиента ИИ обязан уходить в fallback: иначе дух останется без плана при недоступном "
                        + "сервисе и простоит тик за тиком");
        assertEquals(GOAL, fallback.lastGoal(),
                "При сбое клиента fallback обязан получить исходную цель: подмена цели исказит довыполнение");
    }

    @Test
    void buildPrompt_WithGoalAndObservation_ContainsGoalTextHashAndScriptIds() {
        LlmPlanner planner = new LlmPlanner(StubLlmClient.responding(VALID_PLAN_JSON),
                CATALOG, RecordingPlanner.returning(FALLBACK_PLAN));

        String prompt = planner.buildPrompt(GOAL, OBSERVATION);

        assertTrue(prompt.contains(GOAL.text()),
                "Промпт обязан содержать текст цели: без него ИИ решает задачу, которую ему не назвали");
        assertTrue(prompt.contains(OBSERVATION.hash()),
                "Промпт обязан содержать hash наблюдения: без него ИИ не заметит изменения мира "
                        + "и план устареет к моменту исполнения");
        for (ScriptSpec spec : CATALOG.all()) {
            assertTrue(prompt.contains(spec.id()),
                    "Промпт обязан содержать id скрипта '" + spec.id() + "': без каталога ИИ выдумает "
                            + "скрипт вне whitelist, и валидатор отвергнет весь план");
        }
    }
}