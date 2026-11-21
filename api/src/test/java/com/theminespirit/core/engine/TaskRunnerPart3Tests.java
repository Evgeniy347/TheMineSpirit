package com.theminespirit.core.engine;

import static com.theminespirit.core.engine.TaskRunnerTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskRunnerPart3Tests {
    @Test
    void tick_ParallelAnyOneStepSucceeds_ReturnsSuccess() {
        TaskNode plan = new ParallelNode(ParallelMode.ANY, List.of(script(SCRIPT), script(SCRIPT)));
        ScriptRegistry registry = ScriptRegistry.of(Map.of(SCRIPT, TestScripts.succeeding(1)));
        TaskRunner runner = TaskRunner.start(plan, registry, TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "режим ANY обязан завершиться успехом, когда успешен любой шаг Иначе тест tick_ParallelAnyOneStepSucceeds_ReturnsSuccess перестанет ловить эту поломку. При смене tick_ParallelAnyOneStepSucceeds_ReturnsSuccess проверь это утверждение.");
    }

    @Test
    void tick_ParallelAnyAllStepsFail_ReturnsFailure() {
        TaskNode plan = new ParallelNode(ParallelMode.ANY, List.of(script(SCRIPT), script(SCRIPT)));
        TaskRunner runner = TaskRunner.start(
                plan, TestScripts.singleRegistry(SCRIPT, TestScripts.failing(2)), TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "режим ANY ждёт, пока есть шанс на успех Иначе тест tick_ParallelAnyAllStepsFail_ReturnsFailure перестанет ловить эту поломку. При смене tick_ParallelAnyAllStepsFail_ReturnsFailure проверь это утверждение.");
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "режим ANY обязан провалиться, когда провалились все шаги Иначе тест tick_ParallelAnyAllStepsFail_ReturnsFailure перестанет ловить эту поломку. При смене tick_ParallelAnyAllStepsFail_ReturnsFailure проверь это утверждение.");
    }

    @Test
    void tick_ConditionCheckTrue_RunsThenBranch() {
        CountingFactory thenFactory = new CountingFactory(TestScripts.succeeding(1));
        CountingFactory elseFactory = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = new ConditionNode(
                CHECK, Map.of(), new ScriptCall("then_script", Map.of()), new ScriptCall("else_script", Map.of()));
        TaskRunner runner = TaskRunner.start(
                plan,
                ScriptRegistry.of(
                        Map.of("then_script", thenFactory.asFactory(), "else_script", elseFactory.asFactory())),
                TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "истинная проверка condition обязана исполнять ветку then Иначе тест tick_ConditionCheckTrue_RunsThenBranch перестанет ловить эту поломку. При смене tick_ConditionCheckTrue_RunsThenBranch проверь это утверждение.");
        assertEquals(
                1,
                thenFactory.created,
                "истинная проверка обязана создать экземпляр ветки then Иначе тест tick_ConditionCheckTrue_RunsThenBranch перестанет ловить эту поломку. При смене tick_ConditionCheckTrue_RunsThenBranch проверь это утверждение.");
        assertEquals(
                0,
                elseFactory.created,
                "истинная проверка не должна исполнять ветку else Иначе тест tick_ConditionCheckTrue_RunsThenBranch перестанет ловить эту поломку. При смене tick_ConditionCheckTrue_RunsThenBranch проверь это утверждение.");
    }
}
