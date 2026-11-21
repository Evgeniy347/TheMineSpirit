package com.theminespirit.core.engine;

import static com.theminespirit.core.engine.TaskRunnerTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskRunnerTests {
    @Test
    void tick_SequenceOfTwoScripts_RunsToSuccessInTwoTicks() {
        TaskNode plan = sequence(script(SCRIPT), script(SCRIPT));
        TaskRunner runner = TaskRunner.start(
                plan,
                TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)),
                TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "первый тик завершает первый шаг и оставляет узел в работе Иначе тест tick_SequenceOfTwoScripts_RunsToSuccessInTwoTicks перестанет ловить эту поломку. При смене tick_SequenceOfTwoScripts_RunsToSuccessInTwoTicks проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "sequence обязан дойти до конца без провала и завершиться успехом Иначе тест tick_SequenceOfTwoScripts_RunsToSuccessInTwoTicks перестанет ловить эту поломку. При смене tick_SequenceOfTwoScripts_RunsToSuccessInTwoTicks проверь это утверждение.");
    }

    @Test
    void tick_SequenceWithFirstFailure_StopsAtFirstFailureAndSkipsRest() {
        CountingFactory first = new CountingFactory(TestScripts.failing(1));
        CountingFactory second = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = sequence(new ScriptCall("first", Map.of()), new ScriptCall("second", Map.of()));
        TaskRunner runner = TaskRunner.start(
                plan,
                ScriptRegistry.of(Map.of("first", first.asFactory(), "second", second.asFactory())),
                TestScripts.condition(CHECK, true));

        RunStatus status = runner.tick();

        assertEquals(
                RunStatus.FAILED,
                status,
                "провал шага sequence обязан проваливать весь узел: иначе дух продолжит план после неудачи При смене tick_SequenceWithFirstFailure_StopsAtFirstFailureAndSkipsRest проверь это утверждение.");
        assertEquals(
                1,
                first.created,
                "проваленный первый шаг sequence исполняется ровно один раз Иначе тест tick_SequenceWithFirstFailure_StopsAtFirstFailureAndSkipsRest перестанет ловить эту поломку. При смене tick_SequenceWithFirstFailure_StopsAtFirstFailureAndSkipsRest проверь это утверждение.");
        assertEquals(
                0,
                second.created,
                "после провала первого шага остальные шаги sequence не создаются Иначе тест tick_SequenceWithFirstFailure_StopsAtFirstFailureAndSkipsRest перестанет ловить эту поломку. При смене tick_SequenceWithFirstFailure_StopsAtFirstFailureAndSkipsRest проверь это утверждение.");
    }

    @Test
    void tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration() {
        CountingFactory factory = new CountingFactory(TestScripts.succeeding(1));
        TaskRunner runner = TaskRunner.start(
                new RepeatNode(3, new ScriptCall(SCRIPT, Map.of())),
                ScriptRegistry.of(Map.of(SCRIPT, factory.asFactory())),
                TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "после первого повтора из трёх узел ещё работает Иначе тест tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration перестанет ловить эту поломку. При смене tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration проверь это утверждение.");
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "после второго повтора из трёх узел ещё работает Иначе тест tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration перестанет ловить эту поломку. При смене tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "repeat(3) обязан завершиться успехом ровно за три тика однотикового шага Иначе тест tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration перестанет ловить эту поломку. При смене tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration проверь это утверждение.");
        assertEquals(
                3,
                factory.created,
                "repeat обязан создать экземпляр шага на каждую итерацию: повтор с общим экземпляром нарушает изоляцию Иначе тест tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration перестанет ловить эту поломку. При смене tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration проверь это утверждение.");
    }

    @Test
    void tick_RepeatZeroTimes_SucceedsWithoutRunningStep() {
        CountingFactory factory = new CountingFactory(TestScripts.succeeding(1));
        TaskRunner runner = TaskRunner.start(
                new RepeatNode(0, new ScriptCall(SCRIPT, Map.of())),
                ScriptRegistry.of(Map.of(SCRIPT, factory.asFactory())),
                TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "repeat с times <= 0 обязан успешно завершиться сразу: ноль повторов — это готовое действие Иначе тест tick_RepeatZeroTimes_SucceedsWithoutRunningStep перестанет ловить эту поломку. При смене tick_RepeatZeroTimes_SucceedsWithoutRunningStep проверь это утверждение.");
        assertEquals(
                0,
                factory.created,
                "при times = 0 шаг не создаётся и не исполняется Иначе тест tick_RepeatZeroTimes_SucceedsWithoutRunningStep перестанет ловить эту поломку. При смене tick_RepeatZeroTimes_SucceedsWithoutRunningStep проверь это утверждение.");
    }

    @Test
    void tick_Fallback_ReturnsResultOfFirstSuccessfulStep() {
        CountingFactory first = new CountingFactory(TestScripts.failing(1));
        CountingFactory second = new CountingFactory(TestScripts.succeeding(1));
        CountingFactory third = new CountingFactory(TestScripts.failing(1));
        TaskNode plan = new FallbackNode(List.of(
                new ScriptCall("first", Map.of()),
                new ScriptCall("second", Map.of()),
                new ScriptCall("third", Map.of())));
        TaskRunner runner = TaskRunner.start(
                plan,
                ScriptRegistry.of(
                        Map.of("first", first.asFactory(), "second", second.asFactory(), "third", third.asFactory())),
                TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "после провала первого шага fallback пробует следующий Иначе тест tick_Fallback_ReturnsResultOfFirstSuccessfulStep перестанет ловить эту поломку. При смене tick_Fallback_ReturnsResultOfFirstSuccessfulStep проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "fallback обязан остановиться на первом успешном шаге, не исполняя остальные Иначе тест tick_Fallback_ReturnsResultOfFirstSuccessfulStep перестанет ловить эту поломку. При смене tick_Fallback_ReturnsResultOfFirstSuccessfulStep проверь это утверждение.");
        assertEquals(
                1,
                first.created,
                "первый шаг fallback исполняется до провала Иначе тест tick_Fallback_ReturnsResultOfFirstSuccessfulStep перестанет ловить эту поломку. При смене tick_Fallback_ReturnsResultOfFirstSuccessfulStep проверь это утверждение.");
        assertEquals(
                1,
                second.created,
                "второй шаг fallback исполняется и приносит успех Иначе тест tick_Fallback_ReturnsResultOfFirstSuccessfulStep перестанет ловить эту поломку. При смене tick_Fallback_ReturnsResultOfFirstSuccessfulStep проверь это утверждение.");
        assertEquals(
                0,
                third.created,
                "после успеха fallback остальные шаги не создаются Иначе тест tick_Fallback_ReturnsResultOfFirstSuccessfulStep перестанет ловить эту поломку. При смене tick_Fallback_ReturnsResultOfFirstSuccessfulStep проверь это утверждение.");
    }

    @Test
    void tick_FallbackAllStepsFail_ReturnsFailure() {
        TaskNode plan = new FallbackNode(List.of(script(SCRIPT), script(SCRIPT)));
        TaskRunner runner = TaskRunner.start(
                plan, TestScripts.singleRegistry(SCRIPT, TestScripts.failing(1)), TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "после провала первого шага fallback пробует следующий Иначе тест tick_FallbackAllStepsFail_ReturnsFailure перестанет ловить эту поломку. При смене tick_FallbackAllStepsFail_ReturnsFailure проверь это утверждение.");
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "fallback обязан провалиться, когда провалились все ветки: тихий успех скрыл бы невыполнимую задачу Иначе тест tick_FallbackAllStepsFail_ReturnsFailure перестанет ловить эту поломку. При смене tick_FallbackAllStepsFail_ReturnsFailure проверь это утверждение.");
        assertTrue(
                runner.failureReason().isPresent(),
                "провал обязан нести причину: иначе в отчёте задачи нельзя объяснить, почему цель не достигнута При смене tick_FallbackAllStepsFail_ReturnsFailure проверь это утверждение.");
    }

    @Test
    void tick_ParallelAll_WaitsUntilEveryStepSucceeds() {
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(script(SCRIPT), script(SCRIPT)));
        ScriptRegistry registry = ScriptRegistry.of(Map.of(SCRIPT, TestScripts.succeeding(2)));
        TaskRunner runner = TaskRunner.start(plan, registry, TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "режим ALL ждёт, пока успешны все шаги Иначе тест tick_ParallelAll_WaitsUntilEveryStepSucceeds перестанет ловить эту поломку. При смене tick_ParallelAll_WaitsUntilEveryStepSucceeds проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "режим ALL обязан завершиться успехом, когда успешны все шаги Иначе тест tick_ParallelAll_WaitsUntilEveryStepSucceeds перестанет ловить эту поломку. При смене tick_ParallelAll_WaitsUntilEveryStepSucceeds проверь это утверждение.");
    }

    @Test
    void tick_ParallelAllAnyStepFails_ReturnsFailure() {
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(script(SCRIPT), script(SCRIPT)));
        ScriptRegistry registry = ScriptRegistry.of(Map.of(SCRIPT, TestScripts.failing(1)));
        TaskRunner runner = TaskRunner.start(plan, registry, TestScripts.condition(CHECK, true));

        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "режим ALL обязан провалиться, как только любой шаг провалился: иначе провал шага замалчивается При смене tick_ParallelAllAnyStepFails_ReturnsFailure проверь это утверждение.");
    }
}
